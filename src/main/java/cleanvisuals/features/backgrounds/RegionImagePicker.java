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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFileChooser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.presets.PresetManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Image picker for a single region: preview, choose and clear.
 * <p>
 * One of these per region, so adding the bank or login screen is a single extra instance
 * rather than another copy of this code.
 */
@Slf4j
public class RegionImagePicker extends JPanel
{
	/**
	 * Where every background image lives. The only file i/o this plugin does with a
	 * user-supplied path is into and out of here.
	 */
	static final Path ASSETS_DIR = Path.of(
		net.runelite.client.RuneLite.RUNELITE_DIR.getPath(), "clean-visuals", "assets");

	private static final int PREVIEW_WIDTH = PluginPanel.PANEL_WIDTH - 20;
	private static final int PREVIEW_HEIGHT = 80;

	private final ConfigManager configManager;
	private final PresetManager presetManager;
	private final String configKey;
	private final String regionId;

	private final JLabel preview = new JLabel();
	private final JLabel filename = new JLabel();

	private static File lastDirectory;

	RegionImagePicker(ConfigManager configManager, PresetManager presetManager, String title,
		String configKey, String regionId)
	{
		this.configManager = configManager;
		this.presetManager = presetManager;
		this.configKey = configKey;
		this.regionId = regionId;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

		JLabel heading = new JLabel(title);
		heading.setForeground(Color.WHITE);
		heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		add(heading);

		preview.setHorizontalAlignment(SwingConstants.CENTER);
		preview.setVerticalAlignment(SwingConstants.CENTER);
		preview.setPreferredSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_HEIGHT));
		preview.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		preview.setOpaque(true);
		preview.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		add(preview);

		filename.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		filename.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		add(filename);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton choose = new JButton("Choose image");
		choose.addActionListener(e -> chooseImage());
		buttons.add(choose);

		JButton clear = new JButton("Clear");
		clear.addActionListener(e -> setImagePath(""));
		buttons.add(clear);
		add(buttons);

		JPanel resetRow = new JPanel(new GridLayout(1, 1, 4, 0));
		resetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		resetRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		JButton reset = new JButton("Reset");
		reset.setToolTipText("Restore framing, colour and opacity to defaults. Keeps the image");
		reset.addActionListener(e -> presetManager.resetRegion(regionId));
		resetRow.add(reset);
		add(resetRow);

		refresh();
	}

	void refresh()
	{
		updatePreview(configManager.getConfiguration(CleanVisualsConfig.GROUP_NAME, configKey));
	}

	String getConfigKey()
	{
		return configKey;
	}

	private void chooseImage()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a background image");
		chooser.setCurrentDirectory(lastDirectory);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.addChoosableFileFilter(new FileNameExtensionFilter(
			"Images (png, jpg, jpeg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));

		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}

		File selected = chooser.getSelectedFile();
		lastDirectory = selected.getParentFile();

		try
		{
			setImagePath(importAsset(selected).toString());
		}
		catch (IOException e)
		{
			log.warn("Failed to import background image {}", selected, e);
			JOptionPane.showMessageDialog(this,
				"Could not import that image:\n" + e.getMessage(),
				"Import failed", JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Copies the chosen file into the plugin's assets directory, so a saved setup does not
	 * depend on wherever the user happened to pick the file from.
	 */
	private Path importAsset(File source) throws IOException
	{
		Files.createDirectories(ASSETS_DIR);
		Path target = ASSETS_DIR.resolve(source.getName());
		Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	private void setImagePath(String path)
	{
		// The overlay re-reads this every frame, so the game updates as soon as it is set.
		configManager.setConfiguration(CleanVisualsConfig.GROUP_NAME, configKey, path);
		updatePreview(path);
	}

	private void updatePreview(String path)
	{
		if (path == null || path.trim().isEmpty())
		{
			preview.setIcon(null);
			preview.setText("No image");
			filename.setText("Using test pattern");
			return;
		}

		File file = new File(path.trim());
		filename.setText(file.getName());

		// A path can outlive what it points at -- the file deleted, or a preset saved on another
		// machine. ImageIO throws rather than returning null for those, so they are answered here
		// instead of as a caught exception on a perfectly ordinary condition.
		//
		// A preset is a document you can receive from someone else, and it can claim any path as
		// an image's location -- so a path pointing outside the assets directory is refused here
		// too, the same as one that no longer exists, rather than opened.
		if (!isInsideAssetsDir(file) || !file.isFile())
		{
			preview.setIcon(null);
			preview.setText("File not found");
			return;
		}

		BufferedImage image = null;
		try
		{
			image = ImageIO.read(file);
		}
		catch (IOException e)
		{
			log.debug("Could not read preview for {}", path, e);
		}

		if (image == null)
		{
			preview.setIcon(null);
			preview.setText("Cannot preview");
			return;
		}

		preview.setText(null);
		preview.setIcon(new ImageIcon(scaleToFit(image)));
	}

	/**
	 * Whether {@code file} is a direct child of {@link #ASSETS_DIR}, the only kind of path
	 * {@link #importAsset} ever produces.
	 * <p>
	 * Used to guard every place a config-supplied path is opened, since config is not only
	 * written by {@link #importAsset} -- applying a preset writes it too, and a preset is a
	 * document you can receive from someone else.
	 */
	static boolean isInsideAssetsDir(File file)
	{
		return ASSETS_DIR.equals(file.toPath().toAbsolutePath().normalize().getParent());
	}

	/**
	 * Letterboxes into the preview box so the image's true aspect ratio is visible -- the
	 * point of the preview is to show what you are working with, not to flatter it.
	 */
	private static BufferedImage scaleToFit(BufferedImage source)
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
}
