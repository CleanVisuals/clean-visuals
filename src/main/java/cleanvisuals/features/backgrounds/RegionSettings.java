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
 * The settings for one region, read from config.
 * <p>
 * Regions differ only in these values and in which widget and sprite they target, so the
 * rendering logic can be shared entirely.
 */
@Value
public class RegionSettings
{
	String imagePath;
	ImageFit fit;
	int zoomPercent;
	int focalXPercent;
	int focalYPercent;
	int hueShiftDegrees;
	int saturationPercent;
	boolean grayscale;
	int imageOpacity;
	int widgetTransparency;

	public BackgroundTransform transform()
	{
		return new BackgroundTransform(
			fit == null ? ImageFit.FILL : fit,
			zoomPercent,
			clampPercent(focalXPercent) / 100d,
			clampPercent(focalYPercent) / 100d);
	}

	public ImageAdjustments adjustments()
	{
		return new ImageAdjustments(hueShiftDegrees, saturationPercent, grayscale);
	}

	public float alpha()
	{
		return clampPercent(imageOpacity) / 100f;
	}

	static int clampPercent(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
