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

/**
 * One region's border settings, read fresh from config each frame.
 * <p>
 * Clamping lives here rather than at each call site, so a value edited directly in the config
 * file cannot reach the drawing code out of range.
 */
public class BorderSettings
{
	private static final int MIN_THICKNESS = 1;
	private static final int MAX_THICKNESS = 12;

	private final BorderStyle style;
	private final Color colour;
	private final int thickness;
	private final int opacity;

	public BorderSettings(BorderStyle style, Color colour, int thickness, int opacity)
	{
		this.style = style;
		this.colour = colour;
		this.thickness = thickness;
		this.opacity = opacity;
	}

	public BorderStyle getStyle()
	{
		return style == null ? BorderStyle.SOLID : style;
	}

	public Color getColour()
	{
		return colour == null ? Color.BLACK : colour;
	}

	public int getThickness()
	{
		return Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, thickness));
	}

	/**
	 * Opacity as a composite alpha, 0 to 1.
	 */
	public float getAlpha()
	{
		return Math.max(0, Math.min(100, opacity)) / 100f;
	}
}
