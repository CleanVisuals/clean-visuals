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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import cleanvisuals.features.presets.Preset;
import cleanvisuals.features.presets.PresetManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: an image picker per region, plus preset management.
 * <p>
 * RuneLite's config screen can only render checkboxes, spinners, text fields, colour pickers
 * and enum dropdowns -- there is no file-picker item type and no way to put a button there --
 * so anything resembling an upload button has to live here.
 */
@Singleton
@Slf4j
public class BackgroundsPanel extends PluginPanel
{
	private static final String NO_PRESETS = "(no presets saved)";

	private final PresetManager presetManager;

	private final List<RegionImagePicker> pickers;
	private final JComboBox<String> presetList = new JComboBox<>();

	@Inject
	BackgroundsPanel(ConfigManager configManager, PresetManager presetManager)
	{
		this.presetManager = presetManager;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		pickers = List.of(
			new RegionImagePicker(configManager, presetManager, "Chatbox",
				"chatboxImagePath", Preset.REGION_CHATBOX),
			new RegionImagePicker(configManager, presetManager, "Side panel",
				"invImagePath", Preset.REGION_INVENTORY),
			new RegionImagePicker(configManager, presetManager, "Login screen",
				"loginImagePath", Preset.REGION_LOGIN));
		pickers.forEach(content::add);

		JLabel hint = new JLabel("<html>PNG, JPG, GIF or BMP. Animated GIFs play.<br>"
			+ "Framing controls are in the plugin's config.</html>");
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hint.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		content.add(hint);

		content.add(buildPresetSection());

		add(content, BorderLayout.NORTH);

		refresh();
	}

	/**
	 * Re-reads config so the panel reflects changes made elsewhere, such as editing a path in
	 * the config screen or loading a preset.
	 */
	public void refresh()
	{
		pickers.forEach(RegionImagePicker::refresh);
		reloadPresets(selectedPreset());
	}

	/**
	 * Whether a config key is one this panel displays.
	 */
	public boolean tracks(String configKey)
	{
		return pickers.stream().anyMatch(p -> p.getConfigKey().equals(configKey));
	}

	private JPanel buildPresetSection()
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(10, 0, 0, 0)));

		JLabel title = new JLabel("Presets");
		title.setForeground(Color.WHITE);
		title.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		section.add(title);

		presetList.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		section.add(presetList);

		JPanel row1 = new JPanel(new GridLayout(1, 2, 4, 0));
		row1.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton load = new JButton("Load");
		load.setToolTipText("Replace current settings with this preset");
		load.addActionListener(e -> applySelected());
		row1.add(load);

		JButton update = new JButton("Update");
		update.setToolTipText("Overwrite this preset with the current settings");
		update.addActionListener(e -> updateSelected());
		row1.add(update);
		section.add(row1);

		JPanel row2 = new JPanel(new GridLayout(1, 2, 4, 0));
		row2.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton saveAs = new JButton("Save as");
		saveAs.setToolTipText("Save the current settings as a new preset");
		saveAs.addActionListener(e -> saveAsNew());
		row2.add(saveAs);

		JButton delete = new JButton("Delete");
		delete.addActionListener(e -> deleteSelected());
		row2.add(delete);
		section.add(row2);

		JLabel note = new JLabel("<html>Presets cover every region.</html>");
		note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		note.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		section.add(note);

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
		if (name == null)
		{
			return;
		}

		int choice = JOptionPane.showConfirmDialog(this,
			"Overwrite \"" + name + "\" with the current settings?",
			"Update preset", JOptionPane.YES_NO_OPTION);
		if (choice != JOptionPane.YES_OPTION)
		{
			return;
		}

		savePreset(name);
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

		int choice = JOptionPane.showConfirmDialog(this,
			"Delete preset \"" + name + "\"?\n(The images themselves are kept.)",
			"Delete preset", JOptionPane.YES_NO_OPTION);
		if (choice != JOptionPane.YES_OPTION)
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
}
