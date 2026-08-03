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
import java.awt.image.BufferedImage;
import lombok.Value;

/**
 * Colour treatment applied to an image after it has been framed into a region.
 * <p>
 * Region-agnostic, like the rest of this package: the chatbox, inventory, bank and login
 * screen all use the same adjustments rather than each implementing their own.
 */
@Value
public class ImageAdjustments
{
	public static final ImageAdjustments NONE = new ImageAdjustments(0, 100, false, null, 100);

	/**
	 * Rotation around the colour wheel in degrees, -180..180. 0 leaves hue alone.
	 */
	int hueShiftDegrees;

	/**
	 * Saturation as a percentage. 0 drains all colour, 100 is unchanged, 200 doubles it.
	 */
	int saturationPercent;

	/**
	 * Greyscale. Takes precedence over hue and saturation, which have no visible effect once
	 * colour has been removed.
	 */
	boolean grayscale;

	/**
	 * Colour to tint towards, or null for none.
	 * <p>
	 * This exists because hue and saturation cannot touch grey. Saturation is applied as
	 * {@code existing * scale}, and a grey pixel's saturation is zero -- zero times any multiplier
	 * is still zero, and a hue rotation has nothing to rotate. Most of the OSRS interface is
	 * desaturated stone and steel, so the relative controls could only ever lighten or darken it.
	 * <p>
	 * Tinting instead <i>sets</i> hue and saturation from this colour while keeping each pixel's
	 * original brightness, so shading and detail survive but the metal comes out blue. That is the
	 * difference between "rotate the colours that are there" and "make this thing that colour".
	 */
	Color tint;

	/**
	 * How strongly {@link #tint} is mixed in, 0..100. 100 replaces hue and saturation outright;
	 * lower values blend toward the original so a subtle wash is possible.
	 */
	int tintStrength;

	public ImageAdjustments(int hueShiftDegrees, int saturationPercent, boolean grayscale)
	{
		this(hueShiftDegrees, saturationPercent, grayscale, null, 100);
	}

	public ImageAdjustments(int hueShiftDegrees, int saturationPercent, boolean grayscale,
		Color tint, int tintStrength)
	{
		this.hueShiftDegrees = hueShiftDegrees;
		this.saturationPercent = saturationPercent;
		this.grayscale = grayscale;
		this.tint = tint;
		this.tintStrength = tintStrength;
	}

	private boolean hasTint()
	{
		// Alpha zero is how "no colour chosen" arrives from a config colour picker, and is
		// treated as unset rather than as a fully transparent tint.
		return tint != null && tint.getAlpha() > 0 && tintStrength > 0;
	}

	public boolean isIdentity()
	{
		return !grayscale && hueShiftDegrees == 0 && saturationPercent == 100 && !hasTint();
	}

	/**
	 * Applies the adjustments in place.
	 * <p>
	 * Runs on the region-sized image rather than the full-resolution source. A chatbox is a
	 * few tens of thousands of pixels where a source photo can be tens of millions, and this
	 * runs again on every slider movement -- doing it at source resolution would make the
	 * controls feel sluggish for no visible benefit.
	 */
	public void applyTo(BufferedImage image)
	{
		if (isIdentity())
		{
			return;
		}

		final int width = image.getWidth();
		final int height = image.getHeight();
		final float saturationScale = Math.max(0, saturationPercent) / 100f;
		final float hueShift = hueShiftDegrees / 360f;

		final boolean tinting = hasTint();
		final float tintMix = tinting ? Math.min(100, tintStrength) / 100f : 0f;
		final float[] tintHsb = new float[3];
		if (tinting)
		{
			Color.RGBtoHSB(tint.getRed(), tint.getGreen(), tint.getBlue(), tintHsb);
		}

		int[] row = new int[width];
		float[] hsb = new float[3];

		for (int y = 0; y < height; y++)
		{
			image.getRGB(0, y, width, 1, row, 0, width);

			for (int x = 0; x < width; x++)
			{
				int argb = row[x];
				int alpha = argb >>> 24;
				if (alpha == 0)
				{
					continue;
				}

				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				if (grayscale)
				{
					// Rec. 601 luma: weights the channels by perceived brightness rather than
					// averaging, which would wash out reds and darken greens.
					int luma = Math.min(255, Math.round(0.299f * r + 0.587f * g + 0.114f * b));
					row[x] = (alpha << 24) | (luma << 16) | (luma << 8) | luma;
					continue;
				}

				Color.RGBtoHSB(r, g, b, hsb);

				float hue = hsb[0] + hueShift;
				hue = hue - (float) Math.floor(hue); // wrap into 0..1
				float saturation = Math.min(1f, hsb[1] * saturationScale);

				if (tinting)
				{
					// Brightness is deliberately untouched. It carries every bevel, shadow and
					// rivet in the sprite; replacing it would flatten the chrome into a solid
					// block of colour rather than recolouring it.
					hue = lerpHue(hue, tintHsb[0], tintMix);
					saturation = saturation + (tintHsb[1] - saturation) * tintMix;
				}

				row[x] = (alpha << 24) | (Color.HSBtoRGB(hue, saturation, hsb[2]) & 0x00FFFFFF);
			}

			image.setRGB(0, y, width, 1, row, 0, width);
		}
	}

	/**
	 * Blends two hues the short way around the colour wheel.
	 * <p>
	 * Hue is circular, so a plain linear blend from 0.9 to 0.1 travels backwards through the
	 * entire spectrum instead of the short hop across red. At partial strength that shows up as
	 * the wrong colour entirely.
	 */
	private static float lerpHue(float from, float to, float mix)
	{
		float delta = to - from;
		if (delta > 0.5f)
		{
			delta -= 1f;
		}
		else if (delta < -0.5f)
		{
			delta += 1f;
		}

		float result = from + delta * mix;
		return result - (float) Math.floor(result);
	}
}
