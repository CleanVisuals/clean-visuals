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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
import cleanvisuals.ui.CollapsibleSection;
import cleanvisuals.ui.ImagePreview;
import cleanvisuals.ui.PanelComponents;
import cleanvisuals.ui.SliderRow;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Everything for one region: preview, image choice, framing and colour.
 * <p>
 * Config keys all follow the region's prefix, so a region is described here by that prefix
 * alone rather than by ten key names repeated three times over.
 * <p>
 * These controls used to live in the config screen, a screen away from the image they applied
 * to. Here they sit under the preview, and since the overlay re-reads config every frame, a
 * drag shows in game as it happens.
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

	private static final int PREVIEW_WIDTH = PluginPanel.PANEL_WIDTH - 32;
	private static final int PREVIEW_HEIGHT = 64;
	private static final int LABEL_WIDTH = 62;

	private final ConfigManager configManager;
	private final PresetManager presetManager;
	private final String regionId;

	private final String pathKey;
	private final String fitKey;
	private final String zoomKey;
	private final String focalXKey;
	private final String focalYKey;
	private final String imageOpacityKey;
	private final String transparencyKey;
	private final String hueKey;
	private final String saturationKey;
	private final String grayscaleKey;

	private final JLabel preview = new JLabel();
	private final JLabel filename = new JLabel();
	private final JComboBox<ImageFit> fit = new JComboBox<>(ImageFit.values());
	private final JCheckBox grayscale = PanelComponents.checkBox("Black & white",
		"Converts the image to greyscale. Overrides hue and saturation");

	private final SliderRow zoom;
	private final SliderRow focalX;
	private final SliderRow focalY;
	private final SliderRow imageOpacity;
	private final SliderRow transparency;
	private final SliderRow hue;
	private final SliderRow saturation;

	/**
	 * Whether the region blends with what is behind it. The login screen is handed to the client
	 * as a whole-screen sprite with nothing behind it, so opacity there would control nothing --
	 * and the overlay does not read those keys.
	 */
	private final boolean supportsOpacity;

	/**
	 * True while the controls are being filled in from config, so those values are not written
	 * straight back out again.
	 */
	private boolean updating;

	/**
	 * The path the preview is currently showing, so a redraw for an unchanged image can be
	 * skipped entirely rather than re-decoding the file.
	 */
	private String previewedPath;

	private static File lastDirectory;

	RegionImagePicker(ConfigManager configManager, PresetManager presetManager, String prefix,
		String regionId, boolean supportsOpacity)
	{
		this.configManager = configManager;
		this.presetManager = presetManager;
		this.regionId = regionId;
		this.supportsOpacity = supportsOpacity;

		this.pathKey = prefix + "ImagePath";
		this.fitKey = prefix + "Fit";
		this.zoomKey = prefix + "Zoom";
		this.focalXKey = prefix + "FocalX";
		this.focalYKey = prefix + "FocalY";
		this.imageOpacityKey = prefix + "ImageOpacity";
		this.transparencyKey = prefix + "WidgetTransparency";
		this.hueKey = prefix + "Hue";
		this.saturationKey = prefix + "Saturation";
		this.grayscaleKey = prefix + "Grayscale";

		this.zoom = new SliderRow("Zoom", 10, 400, "Zoom relative to the fitted size, as a percentage. 100 = as Fit produced it", v -> write(zoomKey, v));
		this.focalX = new SliderRow("Pos X", 0, 100, "Which point of the image sits at the centre. 0 = left edge, 100 = right edge", v -> write(focalXKey, v));
		this.focalY = new SliderRow("Pos Y", 0, 100, "Which point of the image sits at the centre. 0 = top edge, 100 = bottom edge", v -> write(focalYKey, v));
		this.imageOpacity = new SliderRow("Opacity", 0, 100, "How strongly the image is drawn, as a percentage. 100 = fully visible", v -> write(imageOpacityKey, v));
		this.transparency = new SliderRow("See-thru", 0, 100, "How see-through this region's own background becomes. 0 = unchanged, 100 = fully see-through", v -> write(transparencyKey, v));
		this.hue = new SliderRow("Hue", -180, 180, "Rotates colours around the colour wheel, in degrees. 0 = unchanged", v -> write(hueKey, v));
		this.saturation = new SliderRow("Satur.", 0, 200, "Saturation as a percentage. 0 = grey, 100 = unchanged, 200 = double intensity", v -> write(saturationKey, v));

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		buildPreview();
		buildImageButtons();
		buildFraming();
		buildColour();
		buildReset();

		refresh();
	}

	private void buildPreview()
	{
		preview.setHorizontalAlignment(SwingConstants.CENTER);
		preview.setVerticalAlignment(SwingConstants.CENTER);
		preview.setFont(FontManager.getRunescapeSmallFont());
		preview.setPreferredSize(new Dimension(PREVIEW_WIDTH, PREVIEW_HEIGHT));
		preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, PREVIEW_HEIGHT));
		preview.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		preview.setOpaque(true);
		preview.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(preview);

		filename.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		filename.setFont(FontManager.getRunescapeSmallFont());
		filename.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		filename.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		add(filename);
	}

	private void buildImageButtons()
	{
		add(PanelComponents.buttonRow(
			PanelComponents.flatButton("Choose", this::chooseImage),
			PanelComponents.flatButton("Clear", () -> setImagePath(""))));
	}

	private void buildFraming()
	{
		PanelComponents.style(fit);
		fit.addActionListener(e ->
		{
			Object selected = fit.getSelectedItem();
			if (!updating && selected instanceof ImageFit)
			{
				write(fitKey, ((ImageFit) selected).name());
			}
		});

		JLabel label = new JLabel("Fit");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setPreferredSize(new Dimension(LABEL_WIDTH, 22));

		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		row.add(label, BorderLayout.WEST);
		row.add(fit, BorderLayout.CENTER);
		add(row);

		add(zoom);
		add(focalX);
		add(focalY);

		if (supportsOpacity)
		{
			add(imageOpacity);
			add(transparency);
		}
	}

	/**
	 * Hue, saturation and greyscale behind their own header: they are the least reached for of
	 * the controls, and leaving them open would push the reset button off the bottom.
	 */
	private void buildColour()
	{
		CollapsibleSection colour = new CollapsibleSection("Colour", null);
		colour.getContent().add(hue);
		colour.getContent().add(saturation);

		grayscale.addActionListener(e ->
		{
			if (!updating)
			{
				write(grayscaleKey, Boolean.toString(grayscale.isSelected()));
			}
		});
		colour.getContent().add(grayscale);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		wrapper.add(colour, BorderLayout.CENTER);
		add(wrapper);
	}

	private void buildReset()
	{
		add(PanelComponents.buttonRow(PanelComponents.flatButton("Reset framing", () ->
		{
			presetManager.resetRegion(regionId);
			refresh();
		})));
	}

	/**
	 * Every config key this editor displays, so the panel knows which changes it must redraw for.
	 */
	Set<String> keys()
	{
		return Set.of(pathKey, fitKey, zoomKey, focalXKey, focalYKey, imageOpacityKey,
			transparencyKey, hueKey, saturationKey, grayscaleKey);
	}

	void refresh()
	{
		updating = true;
		try
		{
			fit.setSelectedItem(readFit());
			zoom.setValue(readInt(zoomKey, 100));
			focalX.setValue(readInt(focalXKey, 50));
			focalY.setValue(readInt(focalYKey, 50));
			imageOpacity.setValue(readInt(imageOpacityKey, 100));
			transparency.setValue(readInt(transparencyKey, 100));
			hue.setValue(readInt(hueKey, 0));
			saturation.setValue(readInt(saturationKey, 100));
			grayscale.setSelected(Boolean.parseBoolean(orEmpty(read(grayscaleKey)).trim()));
			updatePreview(read(pathKey));
		}
		finally
		{
			updating = false;
		}
	}

	private void chooseImage()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a background image");
		chooser.setCurrentDirectory(lastDirectory);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.addChoosableFileFilter(new FileNameExtensionFilter(
			"Images (png, jpg, jpeg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));

		// Registers itself as a listener on the chooser, so the preview follows the selection.
		chooser.setAccessory(new ImagePreview(chooser));

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
		configManager.setConfiguration(CleanVisualsConfig.GROUP_NAME, pathKey, path);

		// Re-importing over an existing name leaves the path identical but the pixels different,
		// so the cache below has to be dropped rather than trusted.
		previewedPath = null;
		updatePreview(path);
	}

	private void updatePreview(String path)
	{
		String normalised = path == null ? "" : path.trim();

		// Dragging any slider writes config, and every write brings the whole panel back through
		// here. Decoding the same file off disk sixty times a second to redraw a preview that
		// cannot have changed is the most expensive thing this panel could possibly do, so an
		// unchanged path is answered by leaving the preview exactly as it is.
		if (normalised.equals(previewedPath))
		{
			return;
		}
		previewedPath = normalised;

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
			preview.setText("Image missing");
			filename.setText("Choose it again to restore");
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

	private String read(String key)
	{
		return configManager.getConfiguration(CleanVisualsConfig.GROUP_NAME, key);
	}

	private int readInt(String key, int fallback)
	{
		String raw = read(key);
		if (raw == null || raw.trim().isEmpty())
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

	private ImageFit readFit()
	{
		String raw = read(fitKey);
		if (raw == null || raw.trim().isEmpty())
		{
			return ImageFit.FILL;
		}
		try
		{
			return ImageFit.valueOf(raw.trim());
		}
		catch (IllegalArgumentException e)
		{
			return ImageFit.FILL;
		}
	}

	private void write(String key, int value)
	{
		write(key, Integer.toString(value));
	}

	private void write(String key, String value)
	{
		configManager.setConfiguration(CleanVisualsConfig.GROUP_NAME, key, value);
	}

	private static String orEmpty(String value)
	{
		return value == null ? "" : value;
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
