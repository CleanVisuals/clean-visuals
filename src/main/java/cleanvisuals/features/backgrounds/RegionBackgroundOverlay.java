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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

/**
 * Draws a framed image behind an in-game region.
 * <p>
 * Everything region-independent lives here: the two render strategies, sprite management,
 * animation timing and diagnostics. A subclass supplies only <i>which</i> widget bounds the
 * image fills, <i>which</i> sprite would otherwise obstruct it, and its settings.
 *
 * <h2>Why there are two render strategies</h2>
 * {@link OverlayLayer#UNDER_WIDGETS} is rendered from {@code Hooks.drawAboveOverheads}, part
 * of the scene render path. In resizable layouts the region is repainted every frame, so
 * drawing there works and supports continuous opacity.
 * <p>
 * In fixed layout the client only blits a region when it marks that region dirty, so a
 * per-frame overlay draw never reaches the screen. Worse, blanking the obstructing sprite
 * removes what was clearing the area, so text and icons smear across frames. This is the same
 * reason the game itself will not offer a transparent chatbox in fixed mode -- the area
 * genuinely is not repainted.
 * <p>
 * Fixed layout therefore hands the image to the client <i>as</i> the region's background
 * sprite and lets the client draw it during its own repaint. Losing continuous opacity there
 * costs nothing, because in fixed layout the region sits on game frame rather than scene, so
 * there is nothing behind to blend with.
 */
@Slf4j
public abstract class RegionBackgroundOverlay extends Overlay implements PluginLifecycleComponent
{
	private static final String BLANK_KEY = "blank";

	protected final Client client;
	protected final ClientThread clientThread;

	private final RegionBackground background = new RegionBackground();

	/**
	 * Backing widgets' opacity before we touched them, keyed by component id so shutDown can
	 * put them back.
	 */
	private final Map<Integer, Integer> originalOpacities = new HashMap<>();

	private String appliedSpriteKey;
	private long startedAtMs = System.currentTimeMillis();

	protected RegionBackgroundOverlay(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;

		setLayer(OverlayLayer.UNDER_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
		setPriority(PRIORITY_LOW);
	}

	/**
	 * The widget whose bounds the image fills, or null when the region is not on screen.
	 */
	protected abstract Widget boundsWidget();

	/**
	 * The sprite the client would otherwise paint over this region.
	 */
	protected abstract int obstructionSpriteId();

	/**
	 * Widgets whose opacity must be lowered for the overlay beneath to show through.
	 * <p>
	 * Defaults to the bounds widget, which is correct when a region's backing is the same
	 * widget that defines its area -- true for the chatbox. It is not true in general: the
	 * inventory's bounds come from its item container while its backing belongs to the
	 * layout's side panel, so it overrides this.
	 */
	protected Widget[] backingWidgets()
	{
		Widget widget = boundsWidget();
		return widget == null ? new Widget[0] : new Widget[]{widget};
	}

	/**
	 * Whether the obstruction sprite must be blanked in resizable layouts.
	 * <p>
	 * Needed where the region's backing is painted from the sprite regardless of widget
	 * opacity, as with the chatbox. Not needed where lowering the backing widget's opacity is
	 * enough -- and actively harmful when the sprite is shared with neighbouring furniture,
	 * since blanking it strips that too.
	 */
	protected boolean blanksSpriteWhenResizable()
	{
		return true;
	}

	protected abstract RegionSettings settings();

	protected abstract boolean diagnosticsEnabled();

	/**
	 * Human-readable region name, used only in diagnostics.
	 */
	protected abstract String regionName();

	@Override
	public void startUp()
	{
		background.setImagePath(settings().getImagePath());
		// Which strategy applies depends on the layout, so it is decided on first render
		// rather than guessed here.
		appliedSpriteKey = null;
		startedAtMs = System.currentTimeMillis();
	}

	@Override
	public void shutDown()
	{
		restoreOpacity();
		appliedSpriteKey = null;
		clientThread.invokeLater(this::restoreSprite);
		background.clear();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget widget = boundsWidget();
		if (widget == null || widget.isHidden())
		{
			// Region is gone (e.g. on the login screen). Forget stored opacities so they are
			// re-captured from freshly built widgets rather than restoring stale values.
			originalOpacities.clear();
			return null;
		}

		Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}

		// Resizable only. Fixed layout put the region on game frame rather than over the scene,
		// which needed an entirely separate strategy: compose a region-sized sprite and install
		// it, rather than draw per frame. That strategy could never animate -- rebuilding the
		// sprite every frame means resetting the shared widget sprite cache every frame, forcing
		// a re-decode of every widget sprite in the client -- so GIFs and video showed their
		// first frame and nothing else. Dropping the layout removes the limitation with it.
		if (!client.isResized())
		{
			return null;
		}

		RegionSettings settings = settings();
		background.setImagePath(settings.getImagePath());

		if (blanksSpriteWhenResizable())
		{
			applyBlankSprite();
		}
		applyOpacity(settings);

		int frame = background.frameAt(System.currentTimeMillis() - startedAtMs);
		background.draw(graphics, bounds, settings.transform(), settings.adjustments(),
			settings.alpha(), frame);

		if (diagnosticsEnabled())
		{
			drawDiagnostics(graphics, bounds);
		}

		return null;
	}

	private void applyBlankSprite()
	{
		if (BLANK_KEY.equals(appliedSpriteKey))
		{
			return;
		}
		appliedSpriteKey = BLANK_KEY;
		clientThread.invokeLater(() -> installSprite(client.createSpritePixels(new int[1], 1, 1)));
	}

	private void installSprite(SpritePixels sprite)
	{
		client.getSpriteOverrides().put(obstructionSpriteId(), sprite);
		resetSpriteCache();
	}

	private void restoreSprite()
	{
		client.getSpriteOverrides().remove(obstructionSpriteId());
		resetSpriteCache();
	}

	private void resetSpriteCache()
	{
		if (client.getWidgetSpriteCache() != null)
		{
			client.getWidgetSpriteCache().reset();
		}
	}

	private void applyOpacity(RegionSettings settings)
	{
		// Widget opacity is 0 = fully opaque, 255 = fully transparent, the inverse of how the
		// config slider reads.
		int target = Math.round(RegionSettings.clampPercent(settings.getWidgetTransparency()) * 255f / 100f);

		for (Widget widget : backingWidgets())
		{
			if (widget == null || widget.isHidden())
			{
				continue;
			}

			// Keyed by component id rather than identity: widgets are rebuilt on layout
			// changes, and a stale reference would restore onto the wrong object.
			originalOpacities.putIfAbsent(widget.getId(), widget.getOpacity());

			if (widget.getOpacity() != target)
			{
				widget.setOpacity(target);
			}
		}
	}

	private void restoreOpacity()
	{
		for (Map.Entry<Integer, Integer> entry : originalOpacities.entrySet())
		{
			Widget widget = client.getWidget(entry.getKey());
			if (widget != null)
			{
				widget.setOpacity(entry.getValue());
			}
		}
		originalOpacities.clear();
	}

	private void drawDiagnostics(Graphics2D graphics, Rectangle bounds)
	{
		// Both resizable arrangements are still reported. They are not different strategies, but
		// they do resolve through different top level interfaces, and knowing which one is live
		// has mattered every time something failed to resolve.
		String mode = client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
			? "RESIZABLE_MODERN"
			: "RESIZABLE_CLASSIC";

		String animation = background.isAnimated()
			? background.frameCount() + "f animated"
			: "still";

		String text = String.format("%s | %s | %dx%d | %s",
			regionName(), mode, bounds.width, bounds.height, animation);

		int x = bounds.x + 4;
		int y = bounds.y + 14;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(Color.YELLOW);
		graphics.drawString(text, x, y);
	}
}
