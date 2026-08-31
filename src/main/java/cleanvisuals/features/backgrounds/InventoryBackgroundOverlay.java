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

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.gameui.SidePanelWidgets;

/**
 * Background behind the inventory.
 * <p>
 * Bounds come from the inventory item container, which is the same interface in all three
 * layouts -- the game reparents it rather than defining a separate one per layout. That makes
 * the inventory simpler than the chatbox, which needed per-layout handling for its bounds.
 * <p>
 * The obstruction is {@link SpriteID#SIDE_BACKGROUND}, the side panel backing. Note this
 * sprite covers the whole side panel, not just the inventory, so blanking it in resizable
 * layouts also clears the backing behind the tab area.
 */
@Singleton
@Slf4j
public class InventoryBackgroundOverlay extends RegionBackgroundOverlay
{
	private final CleanVisualsConfig config;

	/**
	 * Layout the probe last reported for, so it logs once per layout rather than per frame.
	 */
	private String lastProbe;

	@Inject
	InventoryBackgroundOverlay(Client client, ClientThread clientThread, CleanVisualsConfig config)
	{
		super(client, clientThread);
		this.config = config;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.invBackground();
	}

	/**
	 * The whole side panel, not just the inventory.
	 * <p>
	 * Binding to the inventory item container meant the image vanished the moment any other
	 * tab was opened -- quests, magic, prayer -- because that container only exists while the
	 * inventory tab is showing. The side panel background persists across tabs, and it is also
	 * what {@link SpriteID#SIDE_BACKGROUND} actually covers, so region and obstruction now
	 * describe the same area.
	 * <p>
	 * Falls back to the item container if no panel widget resolves, so the region still works
	 * in a layout whose panel widget is not among the candidates.
	 */
	@Override
	protected Widget boundsWidget()
	{
		probeOnce();

		for (Widget candidate : sidePanelWidgets())
		{
			if (candidate != null && !candidate.isHidden())
			{
				return candidate;
			}
		}
		return client.getWidget(InterfaceID.Inventory.ITEMS);
	}

	/**
	 * Logs what actually resolves for the current layout.
	 * <p>
	 * The side panel widget was guessed from constant names twice and was wrong both times,
	 * which is silent: an unresolved candidate just falls back to the item container, and the
	 * symptom is the background being confined to the inventory tab and only working with the
	 * game's transparency option enabled. This reports the truth instead -- the top level
	 * interface in use, whether each candidate resolved, and the item container's ancestry,
	 * one of which is the real side panel.
	 */
	private void probeOnce()
	{
		// Debug level, not info. While this was being developed it logged unconditionally, because
		// gating it behind a checkbox is how the first attempt at collecting it silently produced
		// nothing -- but that reasoning applies to a developer chasing a layout bug, not to every
		// user's log filling with side panel dumps. It stays available with debug logging on.
		if (!log.isDebugEnabled())
		{
			return;
		}

		int topLevel = client.getTopLevelInterfaceId();
		String layout = client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
			? "MODERN" : "CLASSIC";
		String probeKey = layout + ":" + topLevel;

		if (probeKey.equals(lastProbe))
		{
			return;
		}
		lastProbe = probeKey;

		log.debug("[side panel probe] layout={} topLevelInterfaceId={} sideTransparency={}",
			layout, topLevel, client.getVarbitValue(VarbitID.SIDE_TRANSPARENCY));

		logCandidate("Toplevel.SIDE", InterfaceID.Toplevel.SIDE);
		logCandidate("ToplevelOsrsStretch.SIDE_BACKGROUND", InterfaceID.ToplevelOsrsStretch.SIDE_BACKGROUND);
		logCandidate("ToplevelPreEoc.SIDE_BACKGROUND", InterfaceID.ToplevelPreEoc.SIDE_BACKGROUND);
		logCandidate("ToplevelPreEoc.SIDE_STATIC_BACKGROUND", InterfaceID.ToplevelPreEoc.SIDE_STATIC_BACKGROUND);
		logCandidate("ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND", InterfaceID.ToplevelPreEoc.SIDE_MOVABLE_BACKGROUND);

		Widget items = client.getWidget(InterfaceID.Inventory.ITEMS);
		if (items == null)
		{
			log.debug("[side panel probe] inventory item container did not resolve");
			return;
		}

		int depth = 0;
		for (Widget w = items; w != null && depth < 8; w = w.getParent(), depth++)
		{
			log.debug("[side panel probe] ancestor[{}] id={} (group={} child={}) bounds={} hidden={}",
				depth, w.getId(), w.getId() >>> 16, w.getId() & 0xFFFF, w.getBounds(), w.isHidden());
		}
	}

	private void logCandidate(String name, int componentId)
	{
		Widget widget = client.getWidget(componentId);
		log.debug("[side panel probe] {} id={} -> {}", name, componentId,
			widget == null ? "null" : ("bounds=" + widget.getBounds() + " hidden=" + widget.isHidden()));
	}

	/**
	 * Candidate side panel widgets for the current layout, most likely first.
	 * <p>
	 * Deliberately not selected by the stone-arrangement varbit. Measurement showed the
	 * constant names do not mean what they suggest: resizable with
	 * {@link VarbitID#RESIZABLE_STONE_ARRANGEMENT} at 0 -- the "classic" arrangement -- still
	 * runs top level interface 161, {@code ToplevelOsrsStretch}, and {@code ToplevelPreEoc}
	 * never resolves at all. Branching on that varbit sent every lookup down the PreEoc path,
	 * where they all returned null and silently fell back to the inventory item container.
	 * <p>
	 * Offering every candidate and taking whichever resolves avoids depending on that mapping
	 * being right.
	 */
	private Widget[] sidePanelWidgets()
	{
		// Which interface the panel lives on, and which widgets are backing rather than tab
		// strips, is documented on SidePanelWidgets -- the custom border needs the same answer,
		// and that is not knowledge worth holding in two places.
		return SidePanelWidgets.candidates(client);
	}

	@Override
	protected int obstructionSpriteId()
	{
		return SpriteID.SIDE_BACKGROUND;
	}

	/**
	 * The side panel does not blank its sprite in resizable layouts.
	 * <p>
	 * {@link SpriteID#SIDE_BACKGROUND} also backs the tab strips above and below the panel, so
	 * blanking it stripped the stone from behind the tab icons and left them floating over the
	 * image. Lowering the backing widget's opacity is enough on its own here, and leaves the
	 * tab strips intact.
	 * <p>
	 * Fixed mode still uses the sprite, because that is the only route there -- and it draws
	 * the image into the sprite rather than blanking it, so the tab strips keep their art.
	 */
	@Override
	protected boolean blanksSpriteWhenResizable()
	{
		return false;
	}

	/**
	 * The inventory's backing is not its bounds widget.
	 * <p>
	 * Bounds come from the item container, but what actually paints behind the items is the
	 * layout's side panel background, which is a different widget in a different interface.
	 * With the game's "transparent side panel" option off (varbit
	 * {@link VarbitID#SIDE_TRANSPARENCY}) that widget is opaque and covers anything drawn at
	 * UNDER_WIDGETS -- which is why the inventory background only appeared with transparency
	 * enabled.
	 * <p>
	 * The widget differs per layout, and resizable classic splits it across static and movable
	 * variants, so every candidate is offered and the missing ones are skipped.
	 */
	@Override
	protected Widget[] backingWidgets()
	{
		return sidePanelWidgets();
	}

	@Override
	protected RegionSettings settings()
	{
		return new RegionSettings(
			config.invImagePath(),
			config.invFit(),
			config.invZoom(),
			config.invFocalX(),
			config.invFocalY(),
			config.invHue(),
			config.invSaturation(),
			config.invGrayscale(),
			config.invImageOpacity(),
			config.invWidgetTransparency());
	}
}
