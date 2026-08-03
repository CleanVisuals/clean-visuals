/*
 * Copyright (c) 2026, Clean Visuals
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cleanvisuals.features.backgrounds;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a video file into a folder of numbered frames, by shelling out to ffmpeg.
 * <p>
 * The plugin cannot decode video itself: Java ships no decoder, and the only dependency small
 * enough to justify bundling (JCodec) handles H.264 baseline profile alone, which most mp4 files
 * are not. Converting once on import instead means every format ffmpeg supports works, the result
 * is plain PNGs that {@link AnimatedImage#loadSequence} already reads, and nothing new is added to
 * the client's dependency verification.
 * <p>
 * The cost is a prerequisite: ffmpeg has to be installed. That is checked up front so the failure
 * is a clear message rather than a broken import.
 */
@Slf4j
final class VideoImporter
{
	/**
	 * Selectable extraction rates, smoothest first.
	 * <p>
	 * Any integer works because ffmpeg's {@code fps} filter resamples the source to the requested
	 * rate -- these are not divisors of some fixed extraction rate, so there is no constraint to
	 * respect beyond keeping them whole numbers for {@code fps.txt}.
	 * <p>
	 * Lower rates buy duration on both axes at once: fewer frames per second of source means both
	 * fewer bytes held in memory and fewer PNGs on disk.
	 */
	static final int[] RATES = {15, 12, 10, 8, 5};

	/**
	 * Default rate when nothing has been chosen. Written into {@code fps.txt} beside the frames on
	 * every import, so the loader always plays back at whatever rate the extraction actually used
	 * -- the two cannot drift into playing fast or slow by their ratio.
	 */
	static final int DEFAULT_FPS = 15;

	/**
	 * Frame memory budget, matching {@code AnimatedImage.SEQUENCE_BUDGET_BYTES}. The loader
	 * enforces this itself; extracting to the same number just avoids writing thousands of PNGs
	 * that would then be ignored.
	 */
	private static final long BUDGET_BYTES = 320L * 1024 * 1024;

	/**
	 * Hard ceiling regardless of region size, matching the loader's own.
	 */
	private static final int MAX_FRAMES = 3000;

	/**
	 * How many frames fit in the budget once {@code source} is scaled to {@code width}.
	 * <p>
	 * The source's real aspect ratio is measured rather than assumed. Assuming 16:9 was wrong by a
	 * factor of three on portrait video: a 46-second phone clip extracted 2209 frames and wrote
	 * 365MB of PNGs, of which the loader could use 698. Height is what consumes the budget, so it
	 * has to be known before deciding how much to extract.
	 * <p>
	 * Falls back to 16:9 if the probe fails, which is no worse than the old behaviour -- the
	 * loader measures real frames and stops at the true budget either way.
	 */
	static int budgetedFrames(File source, int width)
	{
		int sourceHeight = 0;
		int sourceWidth = 0;

		int[] size = source == null ? null : probeSize(source);
		if (size != null)
		{
			sourceWidth = size[0];
			sourceHeight = size[1];
		}

		int height = sourceWidth > 0 && sourceHeight > 0
			? Math.round((float) width * sourceHeight / sourceWidth)
			: Math.round(width * 9f / 16f);

		long bytesPerFrame = (long) width * Math.max(1, height) * 4;
		return (int) Math.max(1, Math.min(MAX_FRAMES, BUDGET_BYTES / bytesPerFrame));
	}

	/**
	 * Seconds of video the budget allows at this width and rate, for showing before committing to
	 * an import. Assumes 16:9 when no source is known, which is what the region-width estimate in
	 * the panel is for -- the real figure follows once a file is chosen.
	 */
	static int budgetedSeconds(File source, int width, int fps)
	{
		return Math.max(1, budgetedFrames(source, width) / Math.max(1, fps));
	}

	/**
	 * The video stream's pixel dimensions via ffprobe, or null if it cannot be determined.
	 * ffprobe ships in the same builds as ffmpeg, so it is available wherever ffmpeg is.
	 */
	private static int[] probeSize(File source)
	{
		String output = runProbe(source, "stream=width,height");
		if (output == null)
		{
			return null;
		}

		try
		{
			// "width,height", sometimes with a trailing comma on odd streams.
			String[] parts = output.split(",");
			if (parts.length < 2)
			{
				return null;
			}
			return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Runs ffprobe for the given {@code -show_entries} expression and returns its raw output.
	 */
	private static String runProbe(File source, String entries)
	{
		String ffmpeg = executable();
		if (ffmpeg == null || source == null)
		{
			return null;
		}

		// Same directory, same naming: ffprobe sits beside ffmpeg in every build.
		String ffprobe = ffmpeg.equals("ffmpeg")
			? "ffprobe"
			: ffmpeg.replace("ffmpeg.exe", "ffprobe.exe");

		List<String> command = new ArrayList<>(List.of(ffprobe, "-v", "error"));
		if (entries.startsWith("stream="))
		{
			command.addAll(List.of("-select_streams", "v:0"));
		}
		command.addAll(List.of("-show_entries", entries, "-of", "csv=p=0", source.getAbsolutePath()));

		try
		{
			Process process = new ProcessBuilder(command).start();

			String output;
			try (var stream = process.getInputStream())
			{
				output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
			}

			if (!process.waitFor(30, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				return null;
			}

			return process.exitValue() == 0 ? output : null;
		}
		catch (IOException e)
		{
			log.debug("Could not probe {}", source, e);
			return null;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/**
	 * Windows installs ffmpeg in various places and often without touching PATH, so a couple of
	 * common ones are worth trying before giving up.
	 */
	private static final List<String> FALLBACK_PATHS = List.of(
		"C:\\ffmpeg\\bin\\ffmpeg.exe",
		"C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
		System.getProperty("user.home") + "\\scoop\\shims\\ffmpeg.exe");

	private VideoImporter()
	{
	}

	static boolean isAvailable()
	{
		return executable() != null;
	}

	/**
	 * Extracts {@code source} into {@code targetDir} as {@code 0001.png} onwards.
	 * <p>
	 * Blocking and slow -- call it off the event thread.
	 *
	 * @param targetWidth pixel width to scale to, height following the source aspect ratio
	 */
	static void extract(File source, Path targetDir, int targetWidth, int fps, double startSeconds)
		throws IOException
	{
		String ffmpeg = executable();
		if (ffmpeg == null)
		{
			throw new IOException("ffmpeg was not found. Install it and try again.");
		}

		// A rerun must not blend with whatever a previous import left behind: the loader takes
		// every image in the folder in name order, so stale frames would be spliced into the
		// animation rather than replaced.
		deleteContents(targetDir);
		Files.createDirectories(targetDir);

		List<String> command = new ArrayList<>();
		command.add(ffmpeg);
		command.add("-y");

		// -ss before -i seeks by jumping in the container rather than decoding everything up to
		// that point, which is the difference between a fast import and one that reads the whole
		// file. It can land on the nearest keyframe, so the result may start slightly before the
		// requested time -- acceptable for a looping background, and the only alternative costs
		// a full decode of everything skipped.
		if (startSeconds > 0)
		{
			command.add("-ss");
			command.add(String.format(java.util.Locale.ROOT, "%.3f", startSeconds));
		}

		command.add("-i");
		command.add(source.getAbsolutePath());
		command.add("-vf");
		command.add("fps=" + fps + ",scale=" + targetWidth + ":-2:flags=lanczos");
		command.add("-frames:v");
		command.add(String.valueOf(budgetedFrames(source, targetWidth)));
		// Zero-padded, because the loader sorts by name and %d would put frame 10 before 2.
		command.add(targetDir.resolve("%04d.png").toString());

		ProcessBuilder builder = new ProcessBuilder(command);

		builder.redirectErrorStream(true);

		Process process = builder.start();
		String output;
		try (var stream = process.getInputStream())
		{
			output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		try
		{
			if (!process.waitFor(5, TimeUnit.MINUTES))
			{
				process.destroyForcibly();
				throw new IOException("ffmpeg timed out after 5 minutes");
			}
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Import was interrupted", e);
		}

		if (process.exitValue() != 0)
		{
			log.warn("ffmpeg failed:\n{}", output);
			throw new IOException("ffmpeg could not read that file. Its output is in the client log.");
		}

		int frames = countFrames(targetDir);
		if (frames == 0)
		{
			throw new IOException("ffmpeg produced no frames from that file.");
		}

		// Record the rate alongside the frames rather than relying on the loader's default, so an
		// import stays correct even if that default changes later.
		Files.writeString(targetDir.resolve("fps.txt"), String.valueOf(fps));

		log.debug("Extracted {} frames at {}px, {}fps ({}s) from {}s",
			frames, targetWidth, fps, frames / fps, String.format(java.util.Locale.ROOT, "%.1f", startSeconds));
	}

	/**
	 * The source's duration in seconds, or 0 if it cannot be determined.
	 */
	static double probeDuration(File source)
	{
		String output = runProbe(source, "format=duration");
		if (output == null)
		{
			return 0;
		}

		try
		{
			return Double.parseDouble(output.split(",")[0].trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/**
	 * Decodes a single frame at {@code atSeconds} and returns it, for previewing a start point
	 * without extracting anything.
	 * <p>
	 * This is what makes choosing a start position possible without holding the whole clip on
	 * disk: seeking to one frame costs a fraction of a second, where extracting the entire video
	 * so it could be scrubbed in-game would cost hundreds of megabytes.
	 */
	static BufferedImage previewFrame(File source, int atSeconds, int width)
	{
		String ffmpeg = executable();
		if (ffmpeg == null)
		{
			return null;
		}

		Path temp = null;
		try
		{
			temp = Files.createTempFile("rpe-preview", ".png");

			Process process = new ProcessBuilder(ffmpeg,
				"-y",
				"-ss", String.valueOf(Math.max(0, atSeconds)),
				"-i", source.getAbsolutePath(),
				"-frames:v", "1",
				"-vf", "scale=" + width + ":-2",
				temp.toString())
				.redirectErrorStream(true)
				.start();

			// Drained so a chatty ffmpeg cannot fill the pipe buffer and deadlock waiting for a
			// reader that never comes.
			try (var stream = process.getInputStream())
			{
				stream.readAllBytes();
			}

			if (!process.waitFor(30, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				return null;
			}

			return process.exitValue() == 0 ? ImageIO.read(temp.toFile()) : null;
		}
		catch (IOException e)
		{
			log.debug("Could not preview {} at {}s", source, atSeconds, e);
			return null;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return null;
		}
		finally
		{
			if (temp != null)
			{
				try
				{
					Files.deleteIfExists(temp);
				}
				catch (IOException ignored)
				{
					// A leftover temp file is harmless; the OS clears them.
				}
			}
		}
	}

	static int countFrames(Path directory)
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return (int) files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
		}
		catch (IOException e)
		{
			return 0;
		}
	}

	/**
	 * PATH first, then the usual Windows install locations.
	 */
	private static String executable()
	{
		if (runs("ffmpeg"))
		{
			return "ffmpeg";
		}

		for (String candidate : FALLBACK_PATHS)
		{
			if (new File(candidate).isFile() && runs(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	private static boolean runs(String command)
	{
		try
		{
			Process process = new ProcessBuilder(command, "-version")
				.redirectErrorStream(true)
				.start();

			if (!process.waitFor(10, TimeUnit.SECONDS))
			{
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		}
		catch (IOException e)
		{
			return false;
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static void deleteContents(Path directory) throws IOException
	{
		if (!Files.isDirectory(directory))
		{
			return;
		}

		try (Stream<Path> paths = Files.walk(directory))
		{
			// Deepest first, so directories are empty by the time they are removed.
			for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
			{
				if (!path.equals(directory))
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}
}
