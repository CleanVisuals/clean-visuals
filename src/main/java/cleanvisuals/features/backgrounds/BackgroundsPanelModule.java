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
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Adds the backgrounds panel to the sidebar while the feature is switched on.
 */
@Singleton
@Slf4j
public class BackgroundsPanelModule implements PluginLifecycleComponent
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Provider<BackgroundsPanel> panelProvider;

	private BackgroundsPanel panel;
	private NavigationButton navigationButton;

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.chatboxBackground() || config.invBackground() || config.loginBackground();
	}

	@Override
	public void startUp()
	{
		panel = panelProvider.get();

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/panel.png");

		navigationButton = NavigationButton.builder()
			.tooltip("Backgrounds")
			.icon(icon)
			.priority(9)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	public void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		panel = null;
	}

	/**
	 * Keeps the panel in step when the path is changed from the config screen instead of the
	 * panel's own button.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (panel == null
			|| !CleanVisualsConfig.GROUP_NAME.equals(event.getGroup())
			|| !panel.tracks(event.getKey()))
		{
			return;
		}

		final BackgroundsPanel target = panel;
		SwingUtilities.invokeLater(target::refresh);
	}
}
