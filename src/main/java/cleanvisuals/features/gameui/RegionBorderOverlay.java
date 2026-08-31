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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a border of your own around a region. A subclass supplies only which widget's bounds to
 * frame and what settings to frame it with.
 * <p>
 * This exists because the borders the game draws cannot be styled. They have no widgets -- the
 * frame routine paints their sprites directly -- so {@link SidePanelBorderHide} can only
 * substitute blank sprites, which comes with two limits an overlay does not have:
 * <ul>
 * <li><b>Scope.</b> Sprite overrides are keyed by id and apply wherever the sprite is drawn, so
 * hiding the side panel's border also strips the same chrome from the bank and settings window.
 * An overlay draws on one rectangle and touches nothing else.</li>
 * <li><b>Opacity.</b> Sprite transparency is binary: the draw routines test for a pixel value of
 * exactly {@code 0} rather than reading alpha, so a half-transparent sprite renders either fully
 * opaque or fully gone. Drawing here composites properly, so any opacity works.</li>
 * </ul>
 * <p>
 * Drawn at {@link OverlayLayer#ABOVE_WIDGETS} so it lands on top of the region rather than behind
 * it, and only in resizable layouts -- fixed puts these regions on game frame rather than over
 * the scene, where a per-frame overlay draw never reaches the screen.
 * <p>
 * Nothing is cached: every style is a handful of fills against bounds the client hands us, which
 * is cheaper than the bookkeeping caching them would need.
 */
public abstract class RegionBorderOverlay extends Overlay implements PluginLifecycleComponent
{
	protected final Client client;

	protected RegionBorderOverlay(Client client)
	{
		this.client = client;

		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	/**
	 * The widget whose bounds the border frames.
	 */
	protected abstract Widget boundsWidget();

	protected abstract BorderSettings settings();

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!client.isResized())
		{
			return null;
		}

		Widget widget = boundsWidget();
		if (widget == null || widget.isHidden())
		{
			return null;
		}

		Rectangle bounds = widget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return null;
		}

		BorderSettings settings = settings();
		float alpha = settings.getAlpha();
		if (alpha <= 0f)
		{
			// Fully transparent is a legitimate setting rather than a reason to draw nothing
			// visible at cost.
			return null;
		}

		int thickness = settings.getThickness();
		Color colour = settings.getColour();

		Composite previousComposite = graphics.getComposite();
		Object previousHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);

		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		graphics.setColor(colour);

		switch (settings.getStyle())
		{
			case DOUBLE:
				drawDouble(graphics, bounds, thickness);
				break;

			case INSET:
				drawBevel(graphics, bounds, thickness, colour, false);
				break;

			case OUTSET:
				drawBevel(graphics, bounds, thickness, colour, true);
				break;

			case ROUNDED:
				drawRounded(graphics, bounds, thickness);
				break;

			case GLOW:
				drawGlow(graphics, bounds, thickness, alpha);
				break;

			case CORNERS:
				drawCorners(graphics, bounds, thickness);
				break;

			case SOLID:
			default:
				drawRing(graphics, bounds, 0, thickness);
				break;
		}

		graphics.setComposite(previousComposite);
		if (previousHint != null)
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousHint);
		}
		return null;
	}

	/**
	 * A rectangular band, {@code inset} pixels in from the bounds and {@code t} pixels wide.
	 * <p>
	 * Four fills rather than a stroked rectangle: a stroke straddles the path it follows, so a
	 * thick one would sit half outside the region and need insetting by half its own width, with
	 * odd thicknesses landing on half a pixel. Filling the four edge bands puts every pixel
	 * exactly where it was asked for.
	 */
	private static void drawRing(Graphics2D graphics, Rectangle bounds, int inset, int t)
	{
		int x = bounds.x + inset;
		int y = bounds.y + inset;
		int width = bounds.width - inset * 2;
		int height = bounds.height - inset * 2;

		if (width <= 0 || height <= 0)
		{
			return;
		}

		// A band thicker than half the region would meet itself in the middle and fill it solid.
		t = Math.min(t, Math.min(width, height) / 2);
		if (t <= 0)
		{
			return;
		}

		graphics.fillRect(x, y, width, t);
		graphics.fillRect(x, y + height - t, width, t);
		graphics.fillRect(x, y, t, height);
		graphics.fillRect(x + width - t, y, t, height);
	}

	/**
	 * Two thin bands with a gap, all three sharing the thickness rather than adding up to more
	 * than it, so switching styles does not change how much of the region the border covers.
	 */
	private static void drawDouble(Graphics2D graphics, Rectangle bounds, int thickness)
	{
		int line = Math.max(1, thickness / 3);
		int gap = Math.max(1, thickness - line * 2);

		drawRing(graphics, bounds, 0, line);
		drawRing(graphics, bounds, line + gap, line);
	}

	/**
	 * The classic bevel: one pair of edges lighter than the chosen colour, the opposite pair
	 * darker, which reads as light falling on a raised or sunken frame.
	 */
	private static void drawBevel(Graphics2D graphics, Rectangle bounds, int thickness, Color colour,
		boolean raised)
	{
		int t = Math.min(thickness, Math.min(bounds.width, bounds.height) / 2);
		if (t <= 0)
		{
			return;
		}

		graphics.setColor(raised ? colour.brighter() : colour.darker());
		graphics.fillRect(bounds.x, bounds.y, bounds.width, t);
		graphics.fillRect(bounds.x, bounds.y, t, bounds.height);

		graphics.setColor(raised ? colour.darker() : colour.brighter());
		graphics.fillRect(bounds.x, bounds.y + bounds.height - t, bounds.width, t);
		graphics.fillRect(bounds.x + bounds.width - t, bounds.y, t, bounds.height);
	}

	/**
	 * An outer rounded rectangle with an inner one subtracted from it, filled as a single shape.
	 * Stroking a round rect instead would centre the line on the path and round the outside and
	 * inside by the same radius, which looks wrong as the border thickens.
	 */
	private static void drawRounded(Graphics2D graphics, Rectangle bounds, int thickness)
	{
		int t = Math.min(thickness, Math.min(bounds.width, bounds.height) / 2);
		if (t <= 0)
		{
			return;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Scaled off the thickness so a heavy border gets a correspondingly generous curve.
		float outerArc = 8f + t * 2f;
		float innerArc = Math.max(0f, outerArc - t * 2f);

		Area border = new Area(new RoundRectangle2D.Float(
			bounds.x, bounds.y, bounds.width, bounds.height, outerArc, outerArc));
		border.subtract(new Area(new RoundRectangle2D.Float(
			bounds.x + t, bounds.y + t, bounds.width - t * 2f, bounds.height - t * 2f,
			innerArc, innerArc)));

		graphics.fill(border);
	}

	/**
	 * Concentric single-pixel bands fading out towards the region edge, so the border has no hard
	 * outer line. Each band composites at its own fraction of the chosen opacity.
	 */
	private static void drawGlow(Graphics2D graphics, Rectangle bounds, int thickness, float alpha)
	{
		for (int i = 0; i < thickness; i++)
		{
			// Faintest at the outside, solid by the innermost band.
			float fade = (i + 1) / (float) thickness;
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * fade));
			drawRing(graphics, bounds, i, 1);
		}
	}

	/**
	 * Brackets at the four corners with the edges left open, which frames the region without
	 * closing it in.
	 */
	private static void drawCorners(Graphics2D graphics, Rectangle bounds, int thickness)
	{
		int t = Math.min(thickness, Math.min(bounds.width, bounds.height) / 2);
		if (t <= 0)
		{
			return;
		}

		// Proportional to the region so the brackets stay in scale as it resizes.
		int arm = Math.max(8, Math.min(bounds.width, bounds.height) / 6);

		int right = bounds.x + bounds.width;
		int bottom = bounds.y + bounds.height;

		graphics.fillRect(bounds.x, bounds.y, arm, t);
		graphics.fillRect(bounds.x, bounds.y, t, arm);

		graphics.fillRect(right - arm, bounds.y, arm, t);
		graphics.fillRect(right - t, bounds.y, t, arm);

		graphics.fillRect(bounds.x, bottom - t, arm, t);
		graphics.fillRect(bounds.x, bottom - arm, t, arm);

		graphics.fillRect(right - arm, bottom - t, arm, t);
		graphics.fillRect(right - t, bottom - arm, t, arm);
	}
}
