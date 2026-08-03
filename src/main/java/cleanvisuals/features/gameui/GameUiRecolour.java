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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.features.backgrounds.ImageAdjustments;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.util.ImageUtil;

/**
 * Recolours and hides parts of the game frame -- minimap surround, side panel edges, chat bar,
 * orbs, tab icons, scrollbars.
 * <p>
 * Hiding lives here rather than in its own component because both operations write the same
 * sprite overrides. Split across two components they would overwrite each other in whatever order
 * they happened to run, and whichever was disabled last would remove the other's work.
 * <p>
 * <b>Hiding is global.</b> Sprite overrides are keyed by id and consulted wherever the sprite is
 * drawn, and this chrome is shared -- the side panel backing is also the trade and bank backdrop.
 * Removing it here removes it everywhere, which is inherent to the mechanism rather than a
 * shortcut.
 * <p>
 * Works by reading each sprite from the cache, applying the same {@link ImageAdjustments} used
 * for background images, and injecting the result as a sprite override. Recolouring is a good
 * fit for sprites: unlike opacity, which is binary at the sprite level and has to come from
 * widget transparency, colour survives the round trip intact.
 * <p>
 * Only the resizable layouts are supported, by choice. Fixed layout does not repaint the frame
 * per frame, which is what made the chatbox awkward, and the frame there is not worth the same
 * fight.
 */
@Singleton
@Slf4j
public class GameUiRecolour implements PluginLifecycleComponent
{
	private final Client client;
	private final ClientThread clientThread;
	private final CleanVisualsConfig config;
	private final ScheduledExecutorService executor;

	/**
	 * The scheduled refresh, cancelled and replaced by each further change while a drag is in
	 * progress.
	 */
	private ScheduledFuture<?> pending;

	/**
	 * Untouched sprites, read once so our own overrides never become the source for the next
	 * adjustment -- otherwise repeated changes would compound.
	 */
	private final Map<Integer, BufferedImage> originals = new HashMap<>();

	/**
	 * Sprite ids currently overridden, so they can be removed cleanly.
	 */
	private final List<Integer> applied = new ArrayList<>();

	private String appliedKey;

	@Inject
	GameUiRecolour(Client client, ClientThread clientThread, CleanVisualsConfig config,
		ScheduledExecutorService executor)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.executor = executor;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		// Hiding is independent of colour: ticking "hide minimap surround" should work without
		// also having to enable a colour treatment that is not wanted.
		return config.gameUiRecolour() || anyGroupHidden();
	}

	@Override
	public void startUp()
	{
		appliedKey = null;
		clientThread.invokeLater(this::refresh);
	}

	@Override
	public void shutDown()
	{
		appliedKey = null;

		// Cancelled so a refresh scheduled moments before shutdown cannot land after restore and
		// leave overrides in place with nothing tracking them.
		synchronized (this)
		{
			if (pending != null)
			{
				pending.cancel(false);
				pending = null;
			}
		}

		clientThread.invokeLater(this::restore);
	}

	/**
	 * Coalesces config changes before doing any work.
	 * <p>
	 * A refresh re-reads and recolours every sprite in every enabled group, then calls
	 * {@code WidgetSpriteCache.reset()}, which forces the client to re-decode <i>every</i> widget
	 * sprite it holds -- not just ours. Dragging a hue slider fires a config change per
	 * intermediate value, so doing this eagerly meant dozens of full cache rebuilds for values
	 * nobody sees, and a visible stutter while dragging.
	 * <p>
	 * Waiting for the drag to settle costs a barely perceptible delay and does the work once.
	 */
	private static final long DEBOUNCE_MS = 200;

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CleanVisualsConfig.GROUP_NAME.equals(event.getGroup()))
		{
			return;
		}

		synchronized (this)
		{
			if (pending != null)
			{
				pending.cancel(false);
			}
			pending = executor.schedule(
				() -> clientThread.invokeLater(this::refresh), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
		}
	}

	private void refresh()
	{
		if (!client.isResized())
		{
			// Resizable only, by choice. Leave the fixed frame alone entirely rather than
			// half-applying something that cannot repaint.
			restore();
			return;
		}

		// Adjustments are per group now, not one set shared by all of them. The hue, saturation
		// and greyscale controls stay global -- they are relative tweaks that read naturally
		// applied everywhere -- while each group gets its own tint colour, which is what makes a
		// blue minimap above a bronze chat bar possible.
		// Hidden groups are blanked rather than recoloured, and are collected first: this class
		// owns these sprite ids, so hiding and recolouring cannot end up as two components
		// overwriting each other's overrides in whichever order they happened to run.
		List<UiGroup> hidden = new ArrayList<>();
		for (UiGroup group : UiGroup.values())
		{
			if (isGroupHidden(group))
			{
				hidden.add(group);
			}
		}

		Map<UiGroup, ImageAdjustments> plan = new LinkedHashMap<>();
		for (UiGroup group : UiGroup.values())
		{
			if (!isGroupEnabled(group) || hidden.contains(group))
			{
				continue;
			}

			ImageAdjustments adjustments = adjustmentsFor(group);
			if (!adjustments.isIdentity())
			{
				plan.put(group, adjustments);
			}
		}

		String key = hidden + "|" + plan;
		if (key.equals(appliedKey))
		{
			return;
		}
		appliedKey = key;

		restore();

		if (plan.isEmpty() && hidden.isEmpty())
		{
			return;
		}

		for (UiGroup group : hidden)
		{
			for (int spriteId : group.spriteIds())
			{
				BufferedImage original = original(spriteId);
				if (original == null)
				{
					continue;
				}

				// Same dimensions, fully transparent. Size matters because interfaces are laid
				// out around these sprites -- a 1x1 replacement would shift what is left rather
				// than cleanly removing it. ImageUtil zeroes transparent pixels, which is what
				// the draw routines skip on.
				BufferedImage blank = new BufferedImage(
					original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

				client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(blank, client));
				applied.add(spriteId);
			}
		}

		for (Map.Entry<UiGroup, ImageAdjustments> entry : plan.entrySet())
		{
			for (int spriteId : entry.getKey().spriteIds())
			{
				BufferedImage original = original(spriteId);
				if (original == null)
				{
					continue;
				}

				// Copy before adjusting: applyTo mutates in place, and the cached original has to
				// stay pristine for the next change.
				BufferedImage recoloured = copy(original);
				entry.getValue().applyTo(recoloured);

				client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(recoloured, client));
				applied.add(spriteId);
			}
		}

		resetCache();
	}

	/**
	 * The global relative adjustments plus this group's own tint colour.
	 */
	private ImageAdjustments adjustmentsFor(UiGroup group)
	{
		return new ImageAdjustments(
			config.gameUiHue(),
			config.gameUiSaturation(),
			config.gameUiGrayscale(),
			tintFor(group),
			config.gameUiTintStrength());
	}

	private Color tintFor(UiGroup group)
	{
		switch (group)
		{
			case MINIMAP:
				return config.gameUiTintMinimap();
			case SIDE_PANEL_FRAME:
				return config.gameUiTintSidePanelFrame();
			case SIDE_PANEL_BACKING:
				return config.gameUiTintSidePanelBacking();
			case TAB_ICONS:
				return config.gameUiTintTabIcons();
			case CHAT_BAR:
				return config.gameUiTintChatBar();
			case ORBS:
				return config.gameUiTintOrbs();
			case CHAT_TABS:
				return config.gameUiTintChatTabs();
			case BUTTONS:
				return config.gameUiTintButtons();
			case SCROLLBARS:
				return config.gameUiTintScrollbars();
			default:
				return null;
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
			case BUTTONS:
				return config.gameUiButtons();
			case TAB_ICONS:
				return config.gameUiTabIcons();
			case SCROLLBARS:
				return config.gameUiScrollbars();
			default:
				return false;
		}
	}

	/**
	 * Whether this group is hidden outright.
	 * <p>
	 * Independent of the "apply to" ticks, which govern colour: hiding the minimap surround should
	 * not require also enabling colour for it.
	 * <p>
	 * Not every group has a hide toggle. Tab icons are deliberately absent -- blanking them leaves
	 * unlabelled tabs nobody can navigate.
	 */
	private boolean isGroupHidden(UiGroup group)
	{
		switch (group)
		{
			case MINIMAP:
				return config.hideMinimap();
			case SIDE_PANEL_FRAME:
				return config.hideSidePanelFrame();
			case SIDE_PANEL_BACKING:
				return config.hideSidePanelBacking();
			case CHAT_BAR:
				return config.hideChatBar();
			case CHAT_TABS:
				return config.hideChatTabs();
			case ORBS:
				return config.hideOrbs();
			case SCROLLBARS:
				return config.hideScrollbars();
			case BUTTONS:
				return config.hideButtons();
			default:
				return false;
		}
	}

	private boolean anyGroupHidden()
	{
		for (UiGroup group : UiGroup.values())
		{
			if (isGroupHidden(group))
			{
				return true;
			}
		}
		return false;
	}

	private BufferedImage original(int spriteId)
	{
		BufferedImage cached = originals.get(spriteId);
		if (cached != null)
		{
			return cached;
		}

		try
		{
			SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
			if (sprites == null || sprites.length == 0 || sprites[0] == null)
			{
				log.debug("Sprite {} did not resolve", spriteId);
				return null;
			}

			SpritePixels sprite = sprites[0];
			BufferedImage image = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
			sprite.toBufferedImage(image);

			originals.put(spriteId, image);
			return image;
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read sprite {}", spriteId, e);
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

	private static BufferedImage copy(BufferedImage source)
	{
		BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		var g = result.createGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return result;
	}
}
