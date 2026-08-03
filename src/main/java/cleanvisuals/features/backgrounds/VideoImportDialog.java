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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Chooses where in a video to start, and at what rate, before committing to an import.
 * <p>
 * Existed because picking a start time blind is guesswork: the useful part of a clip is rarely
 * its first thirty seconds, and there was no way to see what you were selecting.
 * <p>
 * The preview is a single frame decoded on demand, not a playable video. Seeking to one frame
 * costs a fraction of a second, where making the region itself scrub live would mean extracting
 * the whole clip to disk first -- hundreds of megabytes to answer a question this answers for
 * free.
 */
@Slf4j
class VideoImportDialog
{
	private static final int PREVIEW_WIDTH = 360;
	private static final int PREVIEW_HEIGHT = 200;

	/**
	 * Delay before a slider position triggers a decode. Dragging fires continuously, and starting
	 * an ffmpeg process per pixel would queue dozens of them for frames nobody will see.
	 */
	private static final int DEBOUNCE_MS = 250;

	/**
	 * What the user chose.
	 */
	@Getter
	static final class Result
	{
		private final int fps;
		private final double startSeconds;

		private Result(int fps, double startSeconds)
		{
			this.fps = fps;
			this.startSeconds = startSeconds;
		}
	}

	private VideoImportDialog()
	{
	}

	/**
	 * @return the chosen settings, or null if cancelled
	 */
	static Result show(Component parent, File source, int targetWidth)
	{
		double duration = VideoImporter.probeDuration(source);

		JLabel preview = new JLabel("Loading preview...", SwingConstants.CENTER);
		preview.setPreferredSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		preview.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		preview.setOpaque(true);
		preview.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JComboBox<Integer> fpsBox = new JComboBox<>();
		for (int rate : VideoImporter.RATES)
		{
			fpsBox.addItem(rate);
		}
		fpsBox.setSelectedItem(VideoImporter.DEFAULT_FPS);

		// Whole seconds: this picks roughly where to start, and sub-second precision would imply
		// an accuracy that keyframe seeking does not actually deliver.
		int maxStart = (int) Math.max(0, Math.floor(duration) - 1);
		JSlider start = new JSlider(0, Math.max(1, maxStart), 0);
		start.setEnabled(duration > 0);

		JLabel window = new JLabel(" ");
		JLabel sourceInfo = new JLabel(duration > 0
			? String.format("%s  --  %s long", source.getName(), time(duration))
			: source.getName() + "  --  length unknown");
		sourceInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		Runnable updateWindow = () ->
		{
			int fps = (Integer) fpsBox.getSelectedItem();
			int seconds = VideoImporter.budgetedSeconds(source, targetWidth, fps);
			int from = start.getValue();

			// The clip may be shorter than the budget allows, in which case the end is the end of
			// the video rather than the budget limit -- saying otherwise would promise footage
			// that does not exist.
			double to = duration > 0 ? Math.min(duration, from + (double) seconds) : from + seconds;
			double actual = to - from;

			window.setText(String.format("<html>Plays <b>%s</b> to <b>%s</b> (%s)<br>"
					+ "at %d fps the budget holds %s</html>",
				time(from), time(to), time(actual), fps, time(seconds)));
		};

		// Held in a one-element array so the timer's listener can see the latest requested
		// position without the variable needing to be effectively final.
		final int[] pending = {-1};
		Timer debounce = new Timer(DEBOUNCE_MS, null);
		debounce.setRepeats(false);
		debounce.addActionListener(e ->
		{
			int at = start.getValue();
			if (at == pending[0])
			{
				return;
			}
			pending[0] = at;

			new Thread(() ->
			{
				BufferedImage frame = VideoImporter.previewFrame(source, at, PREVIEW_WIDTH);
				SwingUtilities.invokeLater(() ->
				{
					// Ignore a decode that finished after the user moved on.
					if (start.getValue() != at)
					{
						return;
					}

					if (frame == null)
					{
						preview.setIcon(null);
						preview.setText("No preview at " + time(at));
						return;
					}
					preview.setText(null);
					preview.setIcon(new ImageIcon(fit(frame)));
				});
			}, "rpe-video-preview").start();
		});

		start.addChangeListener(e ->
		{
			updateWindow.run();
			debounce.restart();
		});
		fpsBox.addActionListener(e -> updateWindow.run());

		updateWindow.run();
		debounce.restart();

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.add(sourceInfo);
		controls.add(new JLabel("Start position"));
		controls.add(start);

		JPanel fpsRow = new JPanel(new BorderLayout(6, 0));
		fpsRow.add(new JLabel("Frame rate"), BorderLayout.WEST);
		fpsRow.add(fpsBox, BorderLayout.CENTER);
		controls.add(fpsRow);
		controls.add(window);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.add(preview);
		content.add(controls);

		int result = JOptionPane.showConfirmDialog(parent, content, "Import video",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		debounce.stop();

		if (result != JOptionPane.OK_OPTION)
		{
			return null;
		}

		return new Result((Integer) fpsBox.getSelectedItem(), start.getValue());
	}

	/**
	 * Letterboxes into the preview box, so what you see is the frame's real shape rather than one
	 * stretched to fill.
	 */
	private static BufferedImage fit(BufferedImage source)
	{
		double scale = Math.min(
			(double) PREVIEW_WIDTH / source.getWidth(),
			(double) PREVIEW_HEIGHT / source.getHeight());

		int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(source, 0, 0, width, height, null);
		g.dispose();
		return result;
	}

	private static String time(double seconds)
	{
		int total = (int) Math.round(seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}
}
