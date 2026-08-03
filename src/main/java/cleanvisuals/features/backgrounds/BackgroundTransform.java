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

import lombok.Value;

/**
 * How an image is framed inside a region.
 * <p>
 * The focal point is stored in <b>normalised source-image coordinates</b> (0..1), not as pixel
 * offsets, and this is deliberate. Region bounds are not fixed: the chatbox is a different size
 * in fixed and resizable layouts, and in resizable its width tracks the window. A saved pixel
 * offset would silently misframe the moment any of that changed, and one preset could not serve
 * both layouts.
 * <p>
 * Expressed as "this point of the image sits at the centre of the region, at this zoom", the
 * framing stays meaningful whatever size the region turns out to be -- the region simply crops
 * around the focal point.
 */
@Value
public class BackgroundTransform
{
	/**
	 * Centred, unzoomed, cover the region.
	 */
	public static final BackgroundTransform DEFAULT = new BackgroundTransform(ImageFit.FILL, 100, 0.5, 0.5);

	ImageFit fit;

	/**
	 * Zoom as a percentage of the fitted size. 100 = exactly as {@link #fit} produced.
	 */
	int zoomPercent;

	/**
	 * Focal point X in the source image, 0 (left) .. 1 (right).
	 */
	double focalX;

	/**
	 * Focal point Y in the source image, 0 (top) .. 1 (bottom).
	 */
	double focalY;

	public double zoom()
	{
		return Math.max(1, zoomPercent) / 100d;
	}
}
