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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.util.ImageUtil;

/**
 * Removes the border around the modern side panel by replacing its sprites with transparent ones.
 * <p>
 * The border has no widget. A scan of the full widget tree in resizable modern, with the panel on
 * screen, walked 1145 widgets looking for all 132 border sprite ids and matched only two 18x18
 * journal tab icons -- so there is nothing to hide, reposition or set opacity on. The frame
 * routine paints these sprites directly, which leaves substituting the sprite as the only lever.
 * <p>
 * <b>This is global.</b> Sprite overrides are keyed by id and consulted wherever the sprite is
 * drawn, and these sprites are general interface chrome rather than side panel furniture -- the
 * same ones border the settings window and the bank. Blanking them here blanks them everywhere.
 * That is inherent to the mechanism, not a shortcut: there is no id that means "this border, on
 * this panel". Scoping it would mean drawing over the region with an overlay instead, which is a
 * different feature.
 * <p>
 * Opacity is not available by this route at all. Sprite transparency is binary -- the draw
 * routines test for a pixel value of exactly {@code 0} rather than reading alpha -- so a
 * half-transparent replacement renders fully opaque or fully gone, with nothing in between.
 *
 * <h2>Stone only, deliberately</h2>
 *
 * The game's "Modern Layout - Side panel visual appearance" option offers a stone and a steel
 * style. Only stone is handled here.
 * <p>
 * Steel was implemented and then reverted. Blanking its six sprites
 * ({@code Steelborder} 310-313 and {@code Steelborder2} 314-315, mirrored by the frame routine for
 * the bottom and left edges) removed most of the border but always left the vertical divider down
 * the panel's left edge. That divider survived every attempt to identify it: it is not a widget --
 * a dump in this layout reported 56 sprite-backed widgets and zero rectangles or lines, with
 * nothing thin and tall near the panel's left edge at x=734 -- and it is not in any of the six
 * border sprite families, which a tint pass established by leaving it visibly untinted and still
 * carrying its original metallic shading.
 * <p>
 * A partly-removed border looked worse than an untouched one, and finding the remaining sprite
 * would mean dumping the cache and searching it by eye. Reverting steel to stock was the better
 * trade. Anyone picking this up again should start from the cache dump, not from another sprite
 * family guess -- every family in the constants has already been ruled out by measurement.
 */
@Singleton
@Slf4j
public class SidePanelBorderHide implements PluginLifecycleComponent
{
	/**
	 * The stone style: the {@code V2StoneBorders} members whose names claim the side panel.
	 * Confirmed by painting them and watching the border change.
	 */
	private static final int[] STONE = {
		SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_TOP,
		SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_LEFT,
		SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_BOTTOM,
		SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_RIGHT,
		SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_TOP_LEFT,
		SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_TOP_RIGHT,
		SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_BOTTOM_LEFT,
		SpriteID.V2StoneBorders.SIDE_PANEL_CORNER_BOTTOM_RIGHT,
		SpriteID.V2StoneBorders.SIDE_PANEL_EDGE_HORIZONTAL,
		SpriteID.V2StoneBorders.SIDE_PANEL_INTERSECTION_LEFT,
		SpriteID.V2StoneBorders.SIDE_PANEL_INTERSECTION_RIGHT,
		SpriteID.V2StoneBorders.SIDE_PANEL_INTERSECTION_TOP,
		SpriteID.V2StoneBorders.SIDE_PANEL_INTERSECTION_BOTTOM,
	};

	private final Client client;
	private final ClientThread clientThread;

	/**
	 * Sprite ids currently overridden, so they can be removed cleanly.
	 */
	private final List<Integer> applied = new ArrayList<>();

	@Inject
	SidePanelBorderHide(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.hideSidePanelBorder();
	}

	@Override
	public void startUp()
	{
		clientThread.invokeLater(this::apply);
	}

	@Override
	public void shutDown()
	{
		clientThread.invokeLater(this::restore);
	}

	/**
	 * Re-applies on login. The widget sprite cache is rebuilt across a game state change, so an
	 * override put in place before logging in does not survive into the frame on its own.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(this::apply);
		}
	}

	private void apply()
	{
		restore();

		// No check for which style is selected. Blanking a sprite the current style never draws
		// costs nothing, where reading the wrong varbit to detect the style would silently do
		// nothing at all -- and on this feature every guess that could fail silently has.
		blank(STONE);

		resetCache();
	}

	private void blank(int[] spriteIds)
	{
		for (int spriteId : spriteIds)
		{
			SpritePixels original = sprite(spriteId);
			if (original == null)
			{
				log.debug("Border sprite {} did not resolve", spriteId);
				continue;
			}

			// Same dimensions, not 1x1: the border is assembled nine-slice style from these
			// pieces, so changing their size risks shifting what is left rather than removing it.
			// ImageUtil zeroes fully transparent pixels, which is exactly what the draw routines
			// treat as "skip" -- so this draws nothing rather than drawing black.
			BufferedImage empty = new BufferedImage(
				original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

			client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(empty, client));
			applied.add(spriteId);
		}
	}

	private void restore()
	{
		if (applied.isEmpty())
		{
			return;
		}

		for (int spriteId : applied)
		{
			client.getSpriteOverrides().remove(spriteId);
		}
		applied.clear();
		resetCache();
	}

	private SpritePixels sprite(int spriteId)
	{
		try
		{
			SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
			return sprites == null || sprites.length == 0 ? null : sprites[0];
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read border sprite {}", spriteId, e);
			return null;
		}
	}

	private void resetCache()
	{
		if (client.getWidgetSpriteCache() != null)
		{
			client.getWidgetSpriteCache().reset();
		}
	}
}
