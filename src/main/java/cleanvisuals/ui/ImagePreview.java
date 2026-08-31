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

package cleanvisuals.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Shows the highlighted image beside the file list, so picking a background is a matter of
 * looking at it rather than recognising a filename.
 * <p>
 * Decoding is subsampled: the reader is told to skip pixels so that a 4000px wallpaper is read
 * at roughly preview size rather than decoded in full and thrown away. Arrowing down a folder of
 * large images therefore costs about the same as arrowing down a folder of small ones, which is
 * what makes previewing on every selection change affordable at all.
 * <p>
 * Only the highlighted file is ever read. Thumbnailing every row of the list would mean decoding
 * the whole folder up front, which is the version of this that gets expensive.
 */
@Slf4j
public class ImagePreview extends JPanel implements PropertyChangeListener
{
	private static final int PREVIEW_SIZE = 150;

	private final JLabel image = new JLabel();
	private final JLabel caption = new JLabel();

	public ImagePreview(JFileChooser chooser)
	{
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(PREVIEW_SIZE + 16, PREVIEW_SIZE + 40));
		setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 4));

		image.setHorizontalAlignment(SwingConstants.CENTER);
		image.setVerticalAlignment(SwingConstants.CENTER);
		image.setFont(FontManager.getRunescapeSmallFont());
		image.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		image.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		image.setPreferredSize(new Dimension(PREVIEW_SIZE, PREVIEW_SIZE));

		caption.setHorizontalAlignment(SwingConstants.CENTER);
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		caption.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		add(image, BorderLayout.CENTER);
		add(caption, BorderLayout.SOUTH);

		chooser.addPropertyChangeListener(this);
		show(null);
	}

	@Override
	public void propertyChange(PropertyChangeEvent event)
	{
		String property = event.getPropertyName();

		if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(property))
		{
			show(null);
		}
		else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(property))
		{
			show((File) event.getNewValue());
		}
	}

	private void show(File file)
	{
		if (file == null || !file.isFile())
		{
			image.setIcon(null);
			image.setText("No image selected");
			caption.setText(" ");
			return;
		}

		Loaded loaded;
		try
		{
			loaded = read(file);
		}
		catch (IOException | RuntimeException e)
		{
			// A folder full of images will contain something unreadable sooner or later, and a
			// preview pane is not the place to make a fuss about it.
			log.debug("Could not preview {}", file, e);
			loaded = null;
		}

		if (loaded == null || loaded.image == null)
		{
			image.setIcon(null);
			image.setText("Cannot preview");
			caption.setText(file.getName());
			return;
		}

		image.setText(null);
		image.setIcon(new ImageIcon(scaleToFit(loaded.image)));
		caption.setText(loaded.width + " x " + loaded.height);
	}

	/**
	 * Reads the image at roughly preview size, skipping pixels rather than decoding them all.
	 */
	private static Loaded read(File file) throws IOException
	{
		try (ImageInputStream stream = ImageIO.createImageInputStream(file))
		{
			if (stream == null)
			{
				return null;
			}

			Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
			if (!readers.hasNext())
			{
				return null;
			}

			ImageReader reader = readers.next();
			try
			{
				reader.setInput(stream, true, true);

				int width = reader.getWidth(0);
				int height = reader.getHeight(0);

				ImageReadParam param = reader.getDefaultReadParam();
				int step = Math.max(1, Math.min(width, height) / PREVIEW_SIZE);
				param.setSourceSubsampling(step, step, 0, 0);

				// Frame zero for an animated gif: a still is all a preview needs, and reading the
				// rest would undo the point of subsampling in the first place.
				return new Loaded(reader.read(0, param), width, height);
			}
			finally
			{
				reader.dispose();
			}
		}
	}

	private static BufferedImage scaleToFit(BufferedImage source)
	{
		double scale = Math.min(
			(double) PREVIEW_SIZE / source.getWidth(),
			(double) PREVIEW_SIZE / source.getHeight());

		// Never scale up: a 32px icon shown at 150px is blur pretending to be detail.
		scale = Math.min(1d, scale);

		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return result;
	}

	/**
	 * The decoded preview plus the source's true dimensions, which subsampling has already lost
	 * by the time the image comes back.
	 */
	private static final class Loaded
	{
		private final BufferedImage image;
		private final int width;
		private final int height;

		private Loaded(BufferedImage image, int width, int height)
		{
			this.image = image;
			this.width = width;
			this.height = height;
		}
	}
}
