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

package cleanvisuals.features.gameui;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.module.PluginLifecycleComponent;

/**
 * Makes the game frame see-through.
 * <p>
 * Separate from {@link GameUiRecolour} because the two work through different mechanisms and
 * neither can do the other's job. Colour goes through the sprite, which survives a round trip
 * intact. Opacity cannot: sprite transparency is binary -- the draw routines test for a pixel
 * value of exactly 0 rather than reading alpha -- so anything partial has to be applied to the
 * widget.
 * <p>
 * Applied every frame rather than once, because widgets are rebuilt whenever an interface
 * reloads or the layout changes, and a rebuilt widget comes back at its original opacity. Only
 * writes when the value actually differs.
 */
@Singleton
@Slf4j
public class GameUiOpacity implements PluginLifecycleComponent
{
	private final Client client;
	private final CleanVisualsConfig config;

	/**
	 * Original opacity per component id, so shutDown can put the frame back.
	 */
	private final Map<Integer, Integer> originals = new HashMap<>();

	@Inject
	GameUiOpacity(Client client, CleanVisualsConfig config)
	{
		this.client = client;
		this.config = config;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.gameUiOpacityEnabled();
	}

	@Override
	public void shutDown()
	{
		restore();
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (!client.isResized())
		{
			// Resizable only, by choice. The fixed frame does not repaint per frame, so a
			// transparent widget there would reveal stale pixels rather than the scene.
			restore();
			return;
        }

		// Widget opacity runs 0 = opaque to 255 = fully transparent, the inverse of the slider.
		int target = Math.round(clampPercent(config.gameUiOpacity()) * 255f / 100f);

		for (UiGroup group : UiGroup.values())
		{
			if (!isGroupEnabled(group))
			{
				continue;
			}

			for (int componentId : group.widgetIds())
			{
				Widget widget = client.getWidget(componentId);
				if (widget == null || widget.isHidden())
				{
					continue;
				}

				originals.putIfAbsent(componentId, widget.getOpacity());

				// The unread-message flash is not special-cased here. It keeps its colour, which
				// is what makes it legible against a black and white frame, but it fades with
				// everything else: a tab that snapped to solid while the frame around it stayed
				// see-through drew the eye by breaking the look rather than by being clear.
				if (widget.getOpacity() != target)
				{
					widget.setOpacity(target);
				}
			}
		}
	}

	private void restore()
	{
		if (originals.isEmpty())
		{
			return;
		}

		for (Map.Entry<Integer, Integer> entry : originals.entrySet())
		{
			Widget widget = client.getWidget(entry.getKey());
			if (widget != null)
			{
				widget.setOpacity(entry.getValue());
			}
		}
		originals.clear();
	}

	private boolean isGroupEnabled(UiGroup group)
	{
		switch (group)
		{
			case MINIMAP:
				return config.gameUiMinimap();
			case SIDE_PANEL_FRAME:
				return config.gameUiSidePanelFrame();
			case SIDE_PANEL_BACKING:
				return config.gameUiSidePanelBacking();
			case CHAT_BAR:
				return config.gameUiChatBar();
			case ORBS:
				return config.gameUiOrbs();
			case CHAT_TABS:
				return config.gameUiChatTabs();
			default:
				// BUTTONS has no measured widget ids -- it is a sprite-only group.
				return false;
		}
	}

	private static int clampPercent(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
