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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.presets.Preset;
import cleanvisuals.features.presets.PresetManager;
import cleanvisuals.ui.CollapsibleSection;
import cleanvisuals.ui.PanelComponents;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: one collapsible section per region, plus preset management.
 * <p>
 * Every control for a background lives here rather than in the config screen. RuneLite's config
 * screen has no file-picker item and no way to put a button on it, so the image choice had to be
 * here regardless -- and splitting the framing sliders away from the image they frame meant
 * editing a background was a trip between two screens.
 * <p>
 * Only one section is open at a time. Three regions' worth of preview and sliders would
 * otherwise be a panel you scroll rather than a panel you read.
 */
@Singleton
@Slf4j
public class BackgroundsPanel extends PluginPanel
{
	private static final String NO_PRESETS = "(no presets saved)";

	private final ConfigManager configManager;
	private final PresetManager presetManager;

	private final List<RegionSection> sections = new ArrayList<>();
	private final JComboBox<String> presetList = new JComboBox<>();

	@Inject
	BackgroundsPanel(ConfigManager configManager, PresetManager presetManager)
	{
		this.configManager = configManager;
		this.presetManager = presetManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		addRegion(content, "Chatbox", "chatbox", Preset.REGION_CHATBOX, true);
		addRegion(content, "Side panel", "inv", Preset.REGION_INVENTORY, true);
		addRegion(content, "Login screen", "login", Preset.REGION_LOGIN, false);

		// Two plain labels rather than one html label: html does not wrap to the panel width here,
		// so the single line was being cut off mid-sentence at the edge.
		content.add(PanelComponents.hint("PNG, JPG, GIF or BMP."));
		content.add(PanelComponents.hint("Animated GIFs play."));
		content.add(buildPresetSection());

		add(content, BorderLayout.NORTH);

		// Something is open on arrival, so the panel explains itself rather than showing three
		// closed headers and no sign of what is inside them.
		sections.get(0).section.setExpanded(true);

		refresh();
	}

	private void addRegion(JPanel content, String title, String prefix, String regionId,
		boolean supportsOpacity)
	{
		String enableKey = prefix + "Background";

		JCheckBox enable = PanelComponents.checkBox("", "Draw this background in game");
		enable.addActionListener(e ->
			configManager.setConfiguration(CleanVisualsConfig.GROUP_NAME, enableKey,
				Boolean.toString(enable.isSelected())));

		RegionImagePicker editor = new RegionImagePicker(configManager, presetManager, prefix,
			regionId, supportsOpacity);

		CollapsibleSection section = new CollapsibleSection(title, enable);
		section.getContent().add(editor);

		RegionSection region = new RegionSection(section, editor, enable, enableKey);
		sections.add(region);

		// Opening one closes the rest, so the panel's height stays roughly constant.
		section.setOnExpanded(() -> sections.stream()
			.filter(other -> other != region)
			.forEach(other -> other.section.setExpanded(false)));

		content.add(section);
	}

	/**
	 * Re-reads config so the panel reflects changes made elsewhere, such as loading a preset.
	 */
	public void refresh()
	{
		for (RegionSection region : sections)
		{
			// setSelected fires no ActionEvent, so this cannot loop back into a config write.
			region.enable.setSelected(Boolean.parseBoolean(
				orEmpty(configManager.getConfiguration(CleanVisualsConfig.GROUP_NAME, region.enableKey))));
			region.editor.refresh();
		}

		reloadPresets(selectedPreset());
	}

	/**
	 * Whether a config key is one this panel displays.
	 */
	public boolean tracks(String configKey)
	{
		return sections.stream().anyMatch(region ->
			region.enableKey.equals(configKey) || region.editor.keys().contains(configKey));
	}

	private JPanel buildPresetSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(10, 0, 0, 0)));

		section.add(PanelComponents.title("Presets"));

		PanelComponents.style(presetList);
		presetList.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		section.add(presetList);

		JButton load = PanelComponents.flatButton("Load", this::applySelected);
		load.setToolTipText("Replace current settings with this preset");

		// Both of these throw away work that cannot be got back -- Update replaces a saved setup
		// with whatever is live, silently, and Delete removes it. Neither should happen on one
		// click of a button sitting next to Load.
		JButton update = PanelComponents.confirmButton("Update", "Overwrite?", this::updateSelected);
		update.setToolTipText("Overwrite this preset with the current settings");
		section.add(PanelComponents.buttonRow(load, update));

		JButton saveAs = PanelComponents.flatButton("Save as", this::saveAsNew);
		saveAs.setToolTipText("Save the current settings as a new preset");

		JButton delete = PanelComponents.confirmButton("Delete", "Sure?", this::deleteSelected);
		delete.setToolTipText("Delete this preset. The images themselves are kept");
		section.add(PanelComponents.buttonRow(saveAs, delete));

		return section;
	}

	private void applySelected()
	{
		String name = selectedPreset();
		if (name == null)
		{
			return;
		}

		try
		{
			presetManager.apply(name);
			refresh();
		}
		catch (IOException e)
		{
			log.warn("Failed to apply preset {}", name, e);
			error("Could not load that preset:\n" + e.getMessage());
		}
	}

	private void updateSelected()
	{
		String name = selectedPreset();
		if (name != null)
		{
			savePreset(name);
		}
	}

	private void saveAsNew()
	{
		String name = JOptionPane.showInputDialog(this, "Name this preset:", "Save preset",
			JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.trim().isEmpty())
		{
			return;
		}
		name = name.trim();

		// Names are sanitised into filenames, so two visibly different names can collide.
		// Ask rather than silently replacing someone's saved setup.
		if (presetManager.exists(name))
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"A preset with that name already exists. Overwrite it?",
				"Save preset", JOptionPane.YES_NO_OPTION);
			if (choice != JOptionPane.YES_OPTION)
			{
				return;
			}
		}

		savePreset(name);
	}

	private void savePreset(String name)
	{
		try
		{
			presetManager.save(name);
			reloadPresets(name);
		}
		catch (IOException e)
		{
			log.warn("Failed to save preset {}", name, e);
			error("Could not save that preset:\n" + e.getMessage());
		}
	}

	private void deleteSelected()
	{
		String name = selectedPreset();
		if (name == null)
		{
			return;
		}

		try
		{
			presetManager.delete(name);
			reloadPresets(null);
		}
		catch (IOException e)
		{
			log.warn("Failed to delete preset {}", name, e);
			error("Could not delete that preset:\n" + e.getMessage());
		}
	}

	private String selectedPreset()
	{
		Object selected = presetList.getSelectedItem();
		if (selected == null || NO_PRESETS.equals(selected))
		{
			return null;
		}
		return selected.toString();
	}

	private void reloadPresets(String select)
	{
		presetList.removeAllItems();

		List<String> names = presetManager.listNames();
		if (names.isEmpty())
		{
			presetList.addItem(NO_PRESETS);
			return;
		}

		names.forEach(presetList::addItem);
		if (select != null && names.contains(select))
		{
			presetList.setSelectedItem(select);
		}
	}

	private void error(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Presets", JOptionPane.ERROR_MESSAGE);
	}

	private static String orEmpty(String value)
	{
		return value == null ? "" : value;
	}

	/**
	 * One region's header, editor and enable tick, kept together so the panel can refresh or
	 * collapse them as a unit.
	 */
	private static final class RegionSection
	{
		private final CollapsibleSection section;
		private final RegionImagePicker editor;
		private final JCheckBox enable;
		private final String enableKey;

		private RegionSection(CollapsibleSection section, RegionImagePicker editor,
			JCheckBox enable, String enableKey)
		{
			this.section = section;
			this.editor = editor;
			this.enable = enable;
			this.enableKey = enableKey;
		}
	}
}
