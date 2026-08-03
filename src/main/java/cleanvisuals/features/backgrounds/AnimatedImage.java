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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Node;

/**
 * One or more frames loaded from an image file, with per-frame delays.
 * <p>
 * A still image is simply an animation of one frame, so callers do not need a separate path
 * for the two cases.
 */
@Slf4j
public class AnimatedImage
{
	/**
	 * Beyond this, frames are dropped. A decoded frame costs width * height * 4 bytes, so an
	 * unbounded frame count on a large GIF is an easy way to exhaust the heap.
	 */
	private static final int MAX_FRAMES = 300;

	/**
	 * Frames are downscaled to fit this on load. Source resolution well beyond the region it
	 * will be drawn into costs memory for detail nobody can see -- a chatbox is a few hundred
	 * pixels wide.
	 */
	private static final int MAX_DIMENSION = 1280;

	/**
	 * GIFs commonly specify 0, meaning "as fast as possible". Browsers settled on treating
	 * that as 100ms and animations are authored expecting it.
	 */
	private static final int DEFAULT_DELAY_MS = 100;

	/**
	 * Floor on frame time, so a pathological file cannot ask to be redrawn every millisecond.
	 */
	private static final int MIN_DELAY_MS = 20;

	/**
	 * Playback rate for a frame-sequence folder with no {@code fps.txt}. Matches the {@code fps=15}
	 * in the documented ffmpeg command, so the default case needs no configuration at all.
	 * Imported video writes its own {@code fps.txt}, so this only applies to hand-made folders.
	 */
	private static final int SEQUENCE_FPS = 15;

	/**
	 * Memory budget for a frame sequence, in bytes.
	 * <p>
	 * A flat frame count is the wrong unit here. Frames cost {@code width * height * 4}, so 300 of
	 * them is 45MB for a 260px side panel and 430MB for an 800px login screen -- the same number
	 * meaning "comfortable" in one region and "out of heap" in another. Budgeting bytes instead
	 * lets a small region hold minutes of video while a large one is still held to something the
	 * heap can take.
	 */
	private static final long SEQUENCE_BUDGET_BYTES = 320L * 1024 * 1024;

	/**
	 * Sanity ceiling on a sequence, independent of the byte budget. Guards against a folder of
	 * thousands of tiny frames producing an animation nobody wants to sit through.
	 */
	private static final int MAX_SEQUENCE_FRAMES = 3000;

	private final List<BufferedImage> frames;
	private final List<Integer> delaysMs;

	@Getter
	private final int totalDurationMs;

	private AnimatedImage(List<BufferedImage> frames, List<Integer> delaysMs)
	{
		this.frames = frames;
		this.delaysMs = delaysMs;
		this.totalDurationMs = delaysMs.stream().mapToInt(Integer::intValue).sum();
	}

	public static AnimatedImage still(BufferedImage image)
	{
		return new AnimatedImage(List.of(image), List.of(DEFAULT_DELAY_MS));
	}

	public int frameCount()
	{
		return frames.size();
	}

	public boolean isAnimated()
	{
		return frames.size() > 1 && totalDurationMs > 0;
	}

	public BufferedImage frame(int index)
	{
		if (frames.isEmpty())
		{
			return null;
		}
		return frames.get(Math.floorMod(index, frames.size()));
	}

	/**
	 * The frame that should be showing after {@code elapsedMs} of playback.
	 */
	public int indexAt(long elapsedMs)
	{
		if (!isAnimated())
		{
			return 0;
		}

		long position = Math.floorMod(elapsedMs, totalDurationMs);
		for (int i = 0; i < delaysMs.size(); i++)
		{
			position -= delaysMs.get(i);
			if (position < 0)
			{
				return i;
			}
		}
		return delaysMs.size() - 1;
	}

	/**
	 * Loads every frame of an image file.
	 * <p>
	 * GIF frames are frequently <b>partial</b> -- a sub-rectangle holding only what changed
	 * since the previous frame, positioned by an offset, with a disposal method saying what to
	 * do with the canvas afterwards. Reading each frame in isolation and displaying it, which
	 * is the obvious implementation, produces torn and flickering output. Each frame is
	 * therefore composited onto a running canvas here and captured whole.
	 */
	public static AnimatedImage load(File file) throws IOException
	{
		try (ImageInputStream stream = ImageIO.createImageInputStream(file))
		{
			if (stream == null)
			{
				throw new IOException("Could not open " + file.getName());
			}

			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext())
			{
				throw new IOException("No decoder for " + file.getName());
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(stream, false, false);
				return read(reader, file);
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	/**
	 * Loads a folder of numbered image files as one animation -- the video path.
	 * <p>
	 * Decoding video in-client was considered and rejected. Java has no built-in decoder; the only
	 * dependency small enough to justify (JCodec) handles H.264 baseline profile alone, which most
	 * mp4 files are not, and streaming decode would need a background thread, a bounded frame
	 * buffer and keyframe seeking to loop. That is a subsystem. This is a loop over
	 * {@code ImageIO.read}, and the conversion happens once, outside the client:
	 * <pre>
	 * ffmpeg -i clip.mp4 -vf "fps=15,scale=520:-1" frames/%04d.png
	 * </pre>
	 * Frames play at {@link #SEQUENCE_FPS} unless the folder contains an {@code fps.txt} holding a
	 * single number. Keep the {@code fps=} above and that file in agreement, or playback runs fast
	 * or slow by exactly their ratio.
	 * <p>
	 * Scale the export to roughly the region's width. Frames are held decoded, so a 520-wide
	 * sequence costs about 290KB each and a 1280-wide one about ten times that, against a 512MB
	 * heap.
	 */
	public static AnimatedImage loadSequence(File directory) throws IOException
	{
		File[] entries = directory.listFiles(f -> f.isFile() && isImage(f.getName()));
		if (entries == null || entries.length == 0)
		{
			throw new IOException("No image files in " + directory.getName());
		}

		// Lexicographic, which is why the ffmpeg pattern above zero-pads: %04d sorts correctly as
		// text where %d puts frame 10 before frame 2.
		Arrays.sort(entries, Comparator.comparing(File::getName));

		int count = Math.min(entries.length, MAX_SEQUENCE_FRAMES);
		int delayMs = Math.max(MIN_DELAY_MS, Math.round(1000f / readFps(directory)));

		List<BufferedImage> frames = new ArrayList<>(count);
		List<Integer> delays = new ArrayList<>(count);
		long bytes = 0;

		for (int i = 0; i < count; i++)
		{
			BufferedImage frame = ImageIO.read(entries[i]);
			if (frame == null)
			{
				// Skip rather than fail: one unreadable frame in a few hundred should not cost the
				// whole animation.
				log.warn("Could not decode {}", entries[i].getName());
				continue;
			}

			BufferedImage scaled = scaleForMemory(toArgb(frame));
			bytes += (long) scaled.getWidth() * scaled.getHeight() * 4;

			// Checked after adding at least one frame, so an oversized single frame still loads
			// rather than producing an empty animation.
			if (bytes > SEQUENCE_BUDGET_BYTES && !frames.isEmpty())
			{
				log.warn("{} exceeds the {}MB frame budget; stopping at {} of {} frames",
					directory.getName(), SEQUENCE_BUDGET_BYTES / (1024 * 1024),
					frames.size(), entries.length);
				break;
			}

			frames.add(scaled);
			delays.add(delayMs);
		}

		if (entries.length > frames.size())
		{
			log.warn("{} has {} frames; using {}", directory.getName(), entries.length, frames.size());
		}

		if (frames.isEmpty())
		{
			throw new IOException("No decodable frames in " + directory.getName());
		}

		return new AnimatedImage(frames, delays);
	}

	private static boolean isImage(String name)
	{
		String lower = name.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
			|| lower.endsWith(".bmp") || lower.endsWith(".gif");
	}

	private static int readFps(File directory)
	{
		File file = new File(directory, "fps.txt");
		if (!file.isFile())
		{
			return SEQUENCE_FPS;
		}

		try
		{
			int fps = Integer.parseInt(Files.readString(file.toPath()).trim());
			return fps > 0 ? fps : SEQUENCE_FPS;
		}
		catch (IOException | NumberFormatException e)
		{
			log.warn("Unreadable fps.txt in {}, using {}", directory.getName(), SEQUENCE_FPS);
			return SEQUENCE_FPS;
		}
	}

	/**
	 * JPEG frames decode without an alpha channel, which the draw path expects. GIF loading gets
	 * this for free by compositing onto an ARGB canvas; a sequence has no canvas to composite on.
	 */
	private static BufferedImage toArgb(BufferedImage source)
	{
		return source.getType() == BufferedImage.TYPE_INT_ARGB ? source : copy(source);
	}

	private static AnimatedImage read(ImageReader reader, File file) throws IOException
	{
		int available = reader.getNumImages(true);
		if (available <= 0)
		{
			throw new IOException("No frames in " + file.getName());
		}

		int count = Math.min(available, MAX_FRAMES);
		if (available > count)
		{
			log.warn("{} has {} frames; using the first {}", file.getName(), available, count);
		}

		List<BufferedImage> frames = new ArrayList<>(count);
		List<Integer> delays = new ArrayList<>(count);

		// Logical screen size: partial frames are positioned within this, not within the
		// previous frame.
		int canvasWidth = reader.getWidth(0);
		int canvasHeight = reader.getHeight(0);
		for (int i = 1; i < count; i++)
		{
			canvasWidth = Math.max(canvasWidth, reader.getWidth(i));
			canvasHeight = Math.max(canvasHeight, reader.getHeight(i));
		}

		BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D canvasGraphics = canvas.createGraphics();

		for (int i = 0; i < count; i++)
		{
			BufferedImage raw = reader.read(i);
			GifFrameInfo info = GifFrameInfo.from(reader, i);

			BufferedImage previous = null;
			if (info.disposal == Disposal.RESTORE_PREVIOUS)
			{
				previous = copy(canvas);
			}

			canvasGraphics.drawImage(raw, info.x, info.y, null);
			frames.add(scaleForMemory(copy(canvas)));
			delays.add(info.delayMs);

			switch (info.disposal)
			{
				case RESTORE_BACKGROUND:
					canvasGraphics.setComposite(java.awt.AlphaComposite.Clear);
					canvasGraphics.fillRect(info.x, info.y, raw.getWidth(), raw.getHeight());
					canvasGraphics.setComposite(java.awt.AlphaComposite.SrcOver);
					break;
				case RESTORE_PREVIOUS:
					canvasGraphics.dispose();
					canvas = previous != null ? previous : canvas;
					canvasGraphics = canvas.createGraphics();
					break;
				case NONE:
				default:
					break;
			}
		}

		canvasGraphics.dispose();
		return new AnimatedImage(frames, delays);
	}

	private static BufferedImage copy(BufferedImage source)
	{
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return result;
	}

	private static BufferedImage scaleForMemory(BufferedImage source)
	{
		int longest = Math.max(source.getWidth(), source.getHeight());
		if (longest <= MAX_DIMENSION)
		{
			return source;
		}

		double scale = (double) MAX_DIMENSION / longest;
		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return result;
	}

	private enum Disposal
	{
		NONE,
		RESTORE_BACKGROUND,
		RESTORE_PREVIOUS
	}

	/**
	 * Per-frame placement and timing, pulled out of the GIF metadata tree. Non-GIF formats
	 * have no such metadata and fall back to sensible defaults.
	 */
	private static final class GifFrameInfo
	{
		private int x;
		private int y;
		private int delayMs = DEFAULT_DELAY_MS;
		private Disposal disposal = Disposal.NONE;

		static GifFrameInfo from(ImageReader reader, int index)
		{
			GifFrameInfo info = new GifFrameInfo();

			try
			{
				IIOMetadata metadata = reader.getImageMetadata(index);
				if (metadata == null)
				{
					return info;
				}

				String format = metadata.getNativeMetadataFormatName();
				if (format == null || !format.startsWith("javax_imageio_gif"))
				{
					return info;
				}

				IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);

				Node descriptor = child(root, "ImageDescriptor");
				if (descriptor != null)
				{
					info.x = intAttribute(descriptor, "imageLeftPosition", 0);
					info.y = intAttribute(descriptor, "imageTopPosition", 0);
				}

				Node control = child(root, "GraphicControlExtension");
				if (control != null)
				{
					// GIF stores delay in hundredths of a second.
					int hundredths = intAttribute(control, "delayTime", 0);
					info.delayMs = hundredths <= 0
						? DEFAULT_DELAY_MS
						: Math.max(MIN_DELAY_MS, hundredths * 10);

					String disposal = attribute(control, "disposalMethod");
					if ("restoreToBackgroundColor".equals(disposal))
					{
						info.disposal = Disposal.RESTORE_BACKGROUND;
					}
					else if ("restoreToPrevious".equals(disposal))
					{
						info.disposal = Disposal.RESTORE_PREVIOUS;
					}
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Could not read GIF frame metadata for frame {}", index, e);
			}

			return info;
		}

		private static Node child(IIOMetadataNode root, String name)
		{
			var nodes = root.getElementsByTagName(name);
			return nodes.getLength() == 0 ? null : nodes.item(0);
		}

		private static String attribute(Node node, String name)
		{
			var attributes = node.getAttributes();
			if (attributes == null)
			{
				return null;
			}
			Node item = attributes.getNamedItem(name);
			return item == null ? null : item.getNodeValue();
		}

		private static int intAttribute(Node node, String name, int fallback)
		{
			String raw = attribute(node, name);
			if (raw == null)
			{
				return fallback;
			}
			try
			{
				return Integer.parseInt(raw.trim());
			}
			catch (NumberFormatException e)
			{
				return fallback;
			}
		}
	}
}
