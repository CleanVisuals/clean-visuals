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

package cleanvisuals.features.presets;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.backgrounds.ImageFit;

/**
 * Saves, loads, applies and deletes presets.
 * <p>
 * Presets are JSON files on disk rather than config entries: they are documents you name,
 * copy and share, not settings. One file per preset keeps them individually deletable and
 * diffable.
 * <p>
 * Live settings stay in RuneLite's config. Applying a preset writes those config values,
 * which the overlay already re-reads every frame, so a preset switch is visible immediately
 * with no extra plumbing.
 */
@Singleton
@Slf4j
public class PresetManager
{
	private static final Path PRESETS_DIR = Path.of(RuneLite.RUNELITE_DIR.getPath(), "clean-visuals", "presets");
	private static final String SUFFIX = ".json";

	/**
	 * The starting look, bundled in the jar rather than written to disk: a file on disk could be
	 * edited or deleted, and this has to mean the same thing on every installation.
	 */
	private static final String DEFAULTS_RESOURCE = "/default-preset.json";

	/**
	 * Config keys for one region. Regions store identical settings under different prefixes,
	 * so the capture and apply logic is written once against this.
	 */
	private static final class Keys
	{
		private final String imagePath;
		private final String fit;
		private final String zoom;
		private final String focalX;
		private final String focalY;
		private final String hue;
		private final String saturation;
		private final String grayscale;
		private final String imageOpacity;
		private final String widgetTransparency;

		private Keys(String prefix, String pathKey)
		{
			this.imagePath = pathKey;
			this.fit = prefix + "Fit";
			this.zoom = prefix + "Zoom";
			this.focalX = prefix + "FocalX";
			this.focalY = prefix + "FocalY";
			this.hue = prefix + "Hue";
			this.saturation = prefix + "Saturation";
			this.grayscale = prefix + "Grayscale";
			this.imageOpacity = prefix + "ImageOpacity";
			this.widgetTransparency = prefix + "WidgetTransparency";
		}
	}

	/**
	 * Config key prefixes per region. Presets store a typed model rather than raw keys, so this
	 * mapping is the only place that needs to know what a region's settings are called.
	 */
	private static final Map<String, Keys> REGION_KEYS = Map.of(
		Preset.REGION_CHATBOX, new Keys("chatbox", "chatboxImagePath"),
		Preset.REGION_INVENTORY, new Keys("inv", "invImagePath"),
		Preset.REGION_LOGIN, new Keys("login", "loginImagePath"));

	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	PresetManager(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/**
	 * Display names, read from inside each file rather than taken from the filename.
	 * <p>
	 * Filenames are sanitised and lower-cased so they are safe on disk, so using them for
	 * display would show "my night setup" for a preset the user named "My Night Setup". The
	 * stored name round-trips back to the same file via {@link #pathFor(String)}.
	 */
	public List<String> listNames()
	{
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(PRESETS_DIR))
		{
			return names;
		}

		List<Path> files = new ArrayList<>();
		try (Stream<Path> stream = Files.list(PRESETS_DIR))
		{
			stream.filter(p -> p.getFileName().toString().endsWith(SUFFIX)).forEach(files::add);
		}
		catch (IOException e)
		{
			log.warn("Could not list presets", e);
			return names;
		}

		for (Path file : files)
		{
			String fallback = file.getFileName().toString();
			fallback = fallback.substring(0, fallback.length() - SUFFIX.length());

			String name = fallback;
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				Preset preset = gson.fromJson(reader, Preset.class);
				if (preset != null && preset.getName() != null && !preset.getName().trim().isEmpty())
				{
					name = preset.getName().trim();
				}
			}
			catch (IOException | JsonSyntaxException e)
			{
				log.warn("Skipping unreadable preset {}", file.getFileName(), e);
			}

			names.add(name);
		}

		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public boolean exists(String name)
	{
		return Files.isRegularFile(pathFor(name));
	}

	/**
	 * Every image path any saved preset points at, plus the ones currently live in config.
	 * <p>
	 * This is the keep-set for asset cleanup, and the live values matter as much as the saved
	 * ones: a background you are using right now but have not saved into a preset is still in
	 * use, and deleting it because no preset happens to name it would be the worst possible
	 * outcome of a cleanup.
	 * <p>
	 * Paths are returned exactly as stored. Comparison against real files is the caller's job,
	 * since only it knows which directory it is sweeping.
	 */
	public Set<String> referencedImagePaths()
	{
		Set<String> paths = new HashSet<>();

		for (Keys keys : REGION_KEYS.values())
		{
			String live = get(keys.imagePath);
			if (live != null && !live.trim().isEmpty())
			{
				paths.add(live.trim());
			}
		}

		for (String name : listNames())
		{
			Preset preset;
			try
			{
				preset = load(name);
			}
			catch (IOException e)
			{
				// An unreadable preset is treated as referencing everything it might have: skip
				// the sweep rather than risk deleting what it pointed at.
				log.warn("Could not read preset {} while collecting references", name, e);
				continue;
			}

			if (preset == null || preset.getRegions() == null)
			{
				continue;
			}

			for (RegionStyle style : preset.getRegions().values())
			{
				if (style != null && style.getImagePath() != null && !style.getImagePath().trim().isEmpty())
				{
					paths.add(style.getImagePath().trim());
				}
			}
		}

		return paths;
	}

	/**
	 * Snapshots the current settings under the given name and writes it to disk.
	 */
	public void save(String name) throws IOException
	{
		Preset preset = new Preset(name);
		REGION_KEYS.forEach((region, keys) -> preset.getRegions().put(region, capture(keys)));
		preset.setGameUi(captureGameUi());
		BORDER_PREFIXES.forEach((region, prefix) -> preset.getBorders().put(region, captureBorder(prefix)));

		Files.createDirectories(PRESETS_DIR);
		try (Writer writer = Files.newBufferedWriter(pathFor(name), StandardCharsets.UTF_8))
		{
			gson.toJson(preset, writer);
		}
	}

	/**
	 * Loads a preset and writes its values into the live settings.
	 */
	public void apply(String name) throws IOException
	{
		Preset preset = load(name);
		if (preset == null)
		{
			throw new IOException("Preset not found: " + name);
		}

		applyPreset(preset);
	}

	/**
	 * The look a brand new installation starts with, shipped in the jar.
	 * <p>
	 * Applied only to an installation that has never been configured -- see {@code FirstRunSetup}.
	 * It carries no image paths: a path from the machine this was authored on would mean nothing
	 * anywhere else, and the regions are switched off on a fresh install anyway. What it does
	 * carry is the framing each region should use once an image is chosen, so the first picture
	 * someone picks already sits the way it is meant to.
	 */
	public void applyBundledDefaults() throws IOException
	{
		// getResourceAsStream rather than getResource: on the hub this jar is never unpacked, so
		// a file path into it does not resolve.
		try (InputStream in = PresetManager.class.getResourceAsStream(DEFAULTS_RESOURCE))
		{
			if (in == null)
			{
				throw new IOException("Bundled defaults missing: " + DEFAULTS_RESOURCE);
			}

			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
			{
				Preset preset = gson.fromJson(reader, Preset.class);
				if (preset == null)
				{
					throw new IOException("Bundled defaults are empty");
				}
				applyPreset(preset);
			}
		}
		catch (JsonSyntaxException e)
		{
			throw new IOException("Bundled defaults are not valid JSON", e);
		}
	}

	private void applyPreset(Preset preset)
	{
		// preset.region() defaults any region the preset predates, so older presets load
		// cleanly and simply leave newer regions at their defaults.
		REGION_KEYS.forEach((region, keys) -> applyStyle(preset.region(region), keys));
		applyGameUi(preset.gameUi());
		BORDER_PREFIXES.forEach((region, prefix) -> applyBorder(preset, region, prefix));
	}

	/**
	 * Config key prefix per region for the custom borders. The enable key is the prefix itself,
	 * and the rest hang off it.
	 */
	private static final Map<String, String> BORDER_PREFIXES = Map.of(
		Preset.REGION_INVENTORY, "sidePanelBorder",
		Preset.REGION_CHATBOX, "chatboxBorder");

	private BorderPreset captureBorder(String prefix)
	{
		BorderPreset border = new BorderPreset();
		border.setEnabled(getBoolean(prefix));
		border.setStyle(orEmpty(get(prefix + "Style")).trim());
		border.setColour(orEmpty(get(prefix + "Colour")).trim());
		border.setThickness(getInt(prefix + "Thickness", 3));
		border.setOpacity(getInt(prefix + "Opacity", 100));
		return border;
	}

	/**
	 * Writes a border back, field by field, skipping anything the preset does not carry.
	 * <p>
	 * A preset written before borders existed has no entry at all, and one written by a future
	 * version might carry only some fields. Either way the missing ones are left as they are
	 * rather than being stamped with defaults.
	 */
	private void applyBorder(Preset preset, String regionId, String prefix)
	{
		Map<String, BorderPreset> borders = preset.getBorders();
		if (borders == null)
		{
			return;
		}

		BorderPreset border = borders.get(regionId);
		if (border == null)
		{
			return;
		}

		if (border.getEnabled() != null)
		{
			set(prefix, Boolean.toString(border.getEnabled()));
		}
		if (border.getStyle() != null && !border.getStyle().isEmpty())
		{
			set(prefix + "Style", border.getStyle());
		}
		if (border.getColour() != null && !border.getColour().isEmpty())
		{
			set(prefix + "Colour", border.getColour());
		}
		if (border.getThickness() != null)
		{
			set(prefix + "Thickness", Integer.toString(border.getThickness()));
		}
		if (border.getOpacity() != null)
		{
			set(prefix + "Opacity", Integer.toString(border.getOpacity()));
		}
	}

	/**
	 * Restores one region's framing, colour and opacity settings to their defaults.
	 * <p>
	 * The image path is deliberately kept. Resetting how an image is framed and resetting which
	 * image it is are different intentions, and the destructive one should not be a side effect of
	 * the other -- "Clear" already exists for that.
	 */
	public void resetRegion(String regionId)
	{
		Keys keys = REGION_KEYS.get(regionId);
		if (keys == null)
		{
			log.warn("Unknown region {}", regionId);
			return;
		}

		// RegionStyle's field initialisers are the defaults, so a fresh one is the reset target
		// and there is no second list of default values to drift out of step.
		RegionStyle defaults = new RegionStyle();
		defaults.setImagePath(orEmpty(get(keys.imagePath)));
		applyStyle(defaults, keys);
	}

	public void delete(String name) throws IOException
	{
		// Assets are deliberately left alone. They may be shared with other presets, and
		// losing an image because one preset referencing it was removed would be surprising.
		Files.deleteIfExists(pathFor(name));
	}

	private Preset load(String name) throws IOException
	{
		Path path = pathFor(name);
		if (!Files.isRegularFile(path))
		{
			return null;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
		{
			Preset preset = gson.fromJson(reader, Preset.class);
			if (preset == null)
			{
				throw new IOException("Preset file is empty: " + name);
			}
			return preset;
		}
		catch (JsonSyntaxException e)
		{
			throw new IOException("Preset file is not valid JSON: " + name, e);
		}
	}

	/**
	 * Config keys for the Game UI section, paired with the {@code UiGroup} they belong to.
	 * <p>
	 * Group names are stored as strings rather than as the enum, so this package does not depend
	 * on {@code features.gameui} and a preset written today still loads if a group is renamed --
	 * an unrecognised key is simply skipped.
	 */
	private static final Map<String, String> GAME_UI_GROUP_KEYS = Map.of(
		"MINIMAP", "gameUiMinimap",
		"SIDE_PANEL_FRAME", "gameUiSidePanelFrame",
		"SIDE_PANEL_BACKING", "gameUiSidePanelBacking",
		"TAB_ICONS", "gameUiTabIcons",
		"CHAT_BAR", "gameUiChatBar",
		"ORBS", "gameUiOrbs",
		"CHAT_TABS", "gameUiChatTabs",
		"BUTTONS", "gameUiButtons",
		"SCROLLBARS", "gameUiScrollbars");

	private static final Map<String, String> GAME_UI_TINT_KEYS = Map.of(
		"MINIMAP", "gameUiTintMinimap",
		"SIDE_PANEL_FRAME", "gameUiTintSidePanelFrame",
		"SIDE_PANEL_BACKING", "gameUiTintSidePanelBacking",
		"TAB_ICONS", "gameUiTintTabIcons",
		"CHAT_BAR", "gameUiTintChatBar",
		"ORBS", "gameUiTintOrbs",
		"CHAT_TABS", "gameUiTintChatTabs",
		"BUTTONS", "gameUiTintButtons",
		"SCROLLBARS", "gameUiTintScrollbars");

	private static final Map<String, String> GAME_UI_HIDE_KEYS = Map.of(
		"MINIMAP", "hideMinimap",
		"SIDE_PANEL_FRAME", "hideSidePanelFrame",
		"SIDE_PANEL_BACKING", "hideSidePanelBacking",
		"CHAT_BAR", "hideChatBar",
		"CHAT_TABS", "hideChatTabs",
		"ORBS", "hideOrbs",
		"SCROLLBARS", "hideScrollbars",
		"BUTTONS", "hideButtons");

	private GameUiStyle captureGameUi()
	{
		GameUiStyle style = new GameUiStyle();
		style.setRecolourEnabled(getBoolean("gameUiRecolour"));
		style.setHueShiftDegrees(getInt("gameUiHue", 0));
		style.setSaturationPercent(getInt("gameUiSaturation", 100));
		style.setGrayscale(getBoolean("gameUiGrayscale"));
		style.setTintStrength(getInt("gameUiTintStrength", 100));
		style.setOpacityEnabled(getBoolean("gameUiOpacityEnabled"));
		style.setOpacityPercent(getInt("gameUiOpacity", 0));
		style.setHideSidePanelBorder(getBoolean("hideSidePanelBorder"));

		style.setReportColourEnabled(getBoolean("chatReportColourEnabled"));
		style.setReportColour(orEmpty(get("chatReportColour")).trim());
		style.setNotifyColourEnabled(getBoolean("chatNotifyColourEnabled"));
		style.setNotifyColour(orEmpty(get("chatNotifyColour")).trim());

		GAME_UI_GROUP_KEYS.forEach((group, key) -> style.getGroups().put(group, getBoolean(key)));
		GAME_UI_HIDE_KEYS.forEach((group, key) -> style.getHidden().put(group, getBoolean(key)));
		GAME_UI_TINT_KEYS.forEach((group, key) ->
		{
			String value = get(key);
			if (value != null && !value.trim().isEmpty())
			{
				style.getTints().put(group, value.trim());
			}
		});

		return style;
	}

	private void applyGameUi(GameUiStyle style)
	{
		set("gameUiRecolour", Boolean.toString(style.isRecolourEnabled()));
		set("gameUiHue", Integer.toString(style.getHueShiftDegrees()));
		set("gameUiSaturation", Integer.toString(style.getSaturationPercent()));
		set("gameUiGrayscale", Boolean.toString(style.isGrayscale()));
		set("gameUiTintStrength", Integer.toString(style.getTintStrength()));
		set("gameUiOpacityEnabled", Boolean.toString(style.isOpacityEnabled()));
		set("gameUiOpacity", Integer.toString(style.getOpacityPercent()));
		set("hideSidePanelBorder", Boolean.toString(style.isHideSidePanelBorder()));

		// Null on every preset written before the chat tab exceptions existed, so skipped rather
		// than switched off -- the same rule the tints follow.
		if (style.getReportColourEnabled() != null)
		{
			set("chatReportColourEnabled", Boolean.toString(style.getReportColourEnabled()));
		}
		if (style.getReportColour() != null && !style.getReportColour().isEmpty())
		{
			set("chatReportColour", style.getReportColour());
		}
		if (style.getNotifyColourEnabled() != null)
		{
			set("chatNotifyColourEnabled", Boolean.toString(style.getNotifyColourEnabled()));
		}
		if (style.getNotifyColour() != null && !style.getNotifyColour().isEmpty())
		{
			set("chatNotifyColour", style.getNotifyColour());
		}

		GAME_UI_GROUP_KEYS.forEach((group, key) ->
		{
			Boolean enabled = style.getGroups() == null ? null : style.getGroups().get(group);
			if (enabled != null)
			{
				set(key, Boolean.toString(enabled));
			}
		});

		GAME_UI_HIDE_KEYS.forEach((group, key) ->
		{
			Boolean hidden = style.getHidden() == null ? null : style.getHidden().get(group);
			if (hidden != null)
			{
				set(key, Boolean.toString(hidden));
			}
		});

		GAME_UI_TINT_KEYS.forEach((group, key) ->
		{
			String value = style.getTints() == null ? null : style.getTints().get(group);
			// A preset with no entry for a group leaves that group's tint alone rather than
			// clearing it, so an older preset does not silently wipe a newer control.
			if (value != null && !value.trim().isEmpty())
			{
				set(key, value.trim());
			}
		});
	}

	private RegionStyle capture(Keys keys)
	{
		RegionStyle style = new RegionStyle();
		style.setImagePath(orEmpty(get(keys.imagePath)));
		style.setFit(parseFit(get(keys.fit)));
		style.setZoomPercent(getInt(keys.zoom, 100));
		style.setFocalXPercent(getInt(keys.focalX, 50));
		style.setFocalYPercent(getInt(keys.focalY, 50));
		style.setHueShiftDegrees(getInt(keys.hue, 0));
		style.setSaturationPercent(getInt(keys.saturation, 100));
		style.setGrayscale(getBoolean(keys.grayscale));
		style.setImageOpacity(getInt(keys.imageOpacity, 100));
		style.setWidgetTransparency(getInt(keys.widgetTransparency, 100));
		return style;
	}

	private void applyStyle(RegionStyle style, Keys keys)
	{
		set(keys.imagePath, orEmpty(style.getImagePath()));
		set(keys.fit, (style.getFit() == null ? ImageFit.FILL : style.getFit()).name());
		set(keys.zoom, Integer.toString(style.getZoomPercent()));
		set(keys.focalX, Integer.toString(style.getFocalXPercent()));
		set(keys.focalY, Integer.toString(style.getFocalYPercent()));
		set(keys.hue, Integer.toString(style.getHueShiftDegrees()));
		set(keys.saturation, Integer.toString(style.getSaturationPercent()));
		set(keys.grayscale, Boolean.toString(style.isGrayscale()));
		set(keys.imageOpacity, Integer.toString(style.getImageOpacity()));
		set(keys.widgetTransparency, Integer.toString(style.getWidgetTransparency()));
	}

	private String get(String key)
	{
		return configManager.getConfiguration(CleanVisualsConfig.GROUP_NAME, key);
	}

	private void set(String key, String value)
	{
		configManager.setConfiguration(CleanVisualsConfig.GROUP_NAME, key, value);
	}

	private int getInt(String key, int fallback)
	{
		String raw = get(key);
		if (raw == null || raw.isEmpty())
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

	private boolean getBoolean(String key)
	{
		return Boolean.parseBoolean(orEmpty(get(key)).trim());
	}

	private static ImageFit parseFit(String raw)
	{
		if (raw == null || raw.isEmpty())
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

	private static String orEmpty(String value)
	{
		return value == null ? "" : value;
	}

	/**
	 * Presets are named by the user, so the name has to survive becoming a filename.
	 */
	private static Path pathFor(String name)
	{
		return PRESETS_DIR.resolve(sanitise(name) + SUFFIX);
	}

	static String sanitise(String name)
	{
		String cleaned = name == null ? "" : name.trim().replaceAll("[^a-zA-Z0-9 _-]", "_");
		if (cleaned.isEmpty())
		{
			cleaned = "preset";
		}
		return cleaned.toLowerCase(Locale.ROOT);
	}
}
