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

package cleanvisuals.module;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.backgrounds.BackgroundsPanelModule;
import cleanvisuals.features.backgrounds.ChatboxBackgroundOverlay;
import cleanvisuals.features.backgrounds.InventoryBackgroundOverlay;
import cleanvisuals.features.backgrounds.LoginScreenBackground;
import cleanvisuals.features.gameui.GameUiOpacity;
import cleanvisuals.features.gameui.GameUiRecolour;
import cleanvisuals.features.presets.FirstRunSetup;
import cleanvisuals.features.gameui.ChatboxBorder;
import cleanvisuals.features.gameui.SidePanelBorder;
import cleanvisuals.features.gameui.SidePanelBorderHide;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

public class CleanVisualsModule extends AbstractModule
{
	@Override
	protected void configure()
	{
		bind(ComponentManager.class);
	}

	/**
	 * Every component the plugin runs. {@link ComponentManager} starts and stops each one as its
	 * own config options change, so nothing here needs to know about the others.
	 */
	@Provides
	Set<PluginLifecycleComponent> lifecycleComponents(
		ChatboxBackgroundOverlay chatboxBackgroundOverlay,
		InventoryBackgroundOverlay inventoryBackgroundOverlay,
		LoginScreenBackground loginScreenBackground,
		BackgroundsPanelModule backgroundsPanelModule,
		GameUiRecolour gameUiRecolour,
		GameUiOpacity gameUiOpacity,
		SidePanelBorderHide sidePanelBorderHide,
		SidePanelBorder sidePanelBorder,
		ChatboxBorder chatboxBorder,
		FirstRunSetup firstRunSetup
	)
	{
		return ImmutableSet.<PluginLifecycleComponent>builder()
			.add(chatboxBackgroundOverlay)
			.add(inventoryBackgroundOverlay)
			.add(loginScreenBackground)
			.add(backgroundsPanelModule)
			.add(gameUiRecolour)
			.add(gameUiOpacity)
			.add(sidePanelBorderHide)
			.add(sidePanelBorder)
			.add(chatboxBorder)
			.add(firstRunSetup)
			.build();
	}

	@Provides
	@Singleton
	CleanVisualsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CleanVisualsConfig.class);
	}
}
