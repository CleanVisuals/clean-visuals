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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import lombok.extern.slf4j.Slf4j;
import cleanvisuals.features.presets.PresetManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Shows what the assets folder is holding and lets orphaned entries be deleted.
 * <p>
 * Assets accumulate silently: every image chosen through the picker is copied in and keyed by its
 * original filename, and nothing has ever removed them. Pick ten backgrounds over a month and all
 * ten are still on disk, along with the frame folder of every video ever imported.
 * <p>
 * Deletion is manual and explicit by design. An automatic sweep that gets its keep-set wrong
 * destroys the user's data with no warning and no undo, so this shows sizes, marks what is in use,
 * pre-selects nothing that is referenced, and deletes only what was ticked.
 */
@Slf4j
class AssetCleanupDialog
{
	private AssetCleanupDialog()
	{
	}

	/**
	 * One entry in the assets folder -- a single image, or a video's frame directory.
	 */
	private static final class Entry
	{
		private final Path path;
		private final long bytes;
		private final boolean inUse;
		private final String detail;

		private Entry(Path path, long bytes, boolean inUse, String detail)
		{
			this.path = path;
			this.bytes = bytes;
			this.inUse = inUse;
			this.detail = detail;
		}
	}

	static void show(Component parent, Path assetsDir, PresetManager presetManager)
	{
		if (!Files.isDirectory(assetsDir))
		{
			JOptionPane.showMessageDialog(parent, "No assets folder yet -- nothing to clean.",
				"Assets", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		Set<String> referenced = presetManager.referencedImagePaths();
		List<Entry> entries = scan(assetsDir, referenced);

		if (entries.isEmpty())
		{
			JOptionPane.showMessageDialog(parent, "The assets folder is empty.",
				"Assets", JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		long total = entries.stream().mapToLong(e -> e.bytes).sum();
		long reclaimable = entries.stream().filter(e -> !e.inUse).mapToLong(e -> e.bytes).sum();

		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

		List<JCheckBox> boxes = new ArrayList<>();
		for (Entry entry : entries)
		{
			JCheckBox box = new JCheckBox(String.format("%s  --  %s%s",
				entry.path.getFileName(), readable(entry.bytes), entry.detail));

			// In-use entries are shown but cannot be selected. Hiding them would leave the sizes
			// not adding up to the folder total, which reads as a bug.
			box.setEnabled(!entry.inUse);
			box.setSelected(false);
			if (entry.inUse)
			{
				box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}

			boxes.add(box);
			list.add(box);
		}

		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(520, Math.min(420, 40 + entries.size() * 24)));

		JPanel header = new JPanel(new GridLayout(0, 1));
		header.add(new JLabel(String.format("%d item(s), %s total", entries.size(), readable(total))));
		header.add(new JLabel(String.format("%s can be freed. Greyed items are used by a preset "
			+ "or by your current settings.", readable(reclaimable))));
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JPanel selectRow = new JPanel(new GridLayout(1, 2, 4, 0));
		JButton all = new JButton("Select all unused");
		all.addActionListener(e -> {
			for (int i = 0; i < boxes.size(); i++)
			{
				if (!entries.get(i).inUse)
				{
					boxes.get(i).setSelected(true);
				}
			}
		});
		JButton none = new JButton("Select none");
		none.addActionListener(e -> boxes.forEach(b -> b.setSelected(false)));
		selectRow.add(all);
		selectRow.add(none);

		JPanel content = new JPanel(new BorderLayout());
		content.add(header, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		content.add(Box.createVerticalStrut(8), BorderLayout.SOUTH);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(content, BorderLayout.CENTER);
		wrapper.add(selectRow, BorderLayout.SOUTH);

		int result = JOptionPane.showConfirmDialog(parent, wrapper, "Stored images and videos",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (result != JOptionPane.OK_OPTION)
		{
			return;
		}

		List<Entry> selected = new ArrayList<>();
		for (int i = 0; i < boxes.size(); i++)
		{
			if (boxes.get(i).isSelected() && !entries.get(i).inUse)
			{
				selected.add(entries.get(i));
			}
		}

		if (selected.isEmpty())
		{
			return;
		}

		long freeing = selected.stream().mapToLong(e -> e.bytes).sum();
		int confirm = JOptionPane.showConfirmDialog(parent,
			String.format("Permanently delete %d item(s), freeing %s?", selected.size(), readable(freeing)),
			"Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

		if (confirm != JOptionPane.YES_OPTION)
		{
			return;
		}

		int deleted = 0;
		for (Entry entry : selected)
		{
			try
			{
				deleteRecursively(entry.path);
				deleted++;
			}
			catch (IOException e)
			{
				log.warn("Could not delete {}", entry.path, e);
			}
		}

		JOptionPane.showMessageDialog(parent,
			String.format("Deleted %d of %d item(s).", deleted, selected.size()),
			"Cleanup", JOptionPane.INFORMATION_MESSAGE);
	}

	private static List<Entry> scan(Path assetsDir, Set<String> referenced)
	{
		List<Entry> entries = new ArrayList<>();

		try (Stream<Path> children = Files.list(assetsDir))
		{
			for (Path child : children.toArray(Path[]::new))
			{
				boolean directory = Files.isDirectory(child);
				long bytes = directory ? sizeOf(child) : sizeOf(child);

				// Referenced paths are stored as absolute strings, so compare that way rather
				// than by filename -- two assets can share a name across different folders.
				boolean inUse = referenced.contains(child.toString())
					|| referenced.contains(child.toAbsolutePath().toString());

				String detail = "";
				if (directory)
				{
					long frames = countFrames(child);
					detail = "  (video, " + frames + " frames)";
				}
				if (inUse)
				{
					detail += "  [in use]";
				}

				entries.add(new Entry(child, bytes, inUse, detail));
			}
		}
		catch (IOException e)
		{
			log.warn("Could not list assets", e);
		}

		// Largest first: the point of the dialog is reclaiming space, so what is worth deleting
		// should be at the top rather than wherever the filesystem happened to put it.
		entries.sort(Comparator.comparingLong((Entry e) -> e.bytes).reversed());
		return entries;
	}

	private static long countFrames(Path directory)
	{
		try (Stream<Path> files = Files.list(directory))
		{
			return files.filter(p -> p.getFileName().toString().endsWith(".png")).count();
		}
		catch (IOException e)
		{
			return 0;
		}
	}

	private static long sizeOf(Path path)
	{
		try (Stream<Path> walk = Files.walk(path))
		{
			return walk.filter(Files::isRegularFile).mapToLong(p ->
			{
				try
				{
					return Files.size(p);
				}
				catch (IOException e)
				{
					return 0L;
				}
			}).sum();
		}
		catch (IOException e)
		{
			return 0L;
		}
	}

	private static void deleteRecursively(Path path) throws IOException
	{
		try (Stream<Path> paths = Files.walk(path))
		{
			// Deepest first, so a directory is empty by the time it is removed.
			for (Path p : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new))
			{
				Files.deleteIfExists(p);
			}
		}
	}

	private static String readable(long bytes)
	{
		if (bytes >= 1024L * 1024 * 1024)
		{
			return String.format("%.1f GB", bytes / (1024d * 1024 * 1024));
		}
		if (bytes >= 1024 * 1024)
		{
			return String.format("%.1f MB", bytes / (1024d * 1024));
		}
		if (bytes >= 1024)
		{
			return String.format("%.0f KB", bytes / 1024d);
		}
		return bytes + " B";
	}
}
