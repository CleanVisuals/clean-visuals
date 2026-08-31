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

package cleanvisuals.features.presets;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named, saved setup: one {@link RegionStyle} per region.
 * <p>
 * Keyed by region id rather than having a field per region, so adding the inventory, bank or
 * login screen later is purely additive -- no change to this class, and presets saved before
 * those regions existed keep loading.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Preset
{
	public static final String REGION_CHATBOX = "chatbox";
	public static final String REGION_INVENTORY = "inventory";
	public static final String REGION_LOGIN = "login";

	private String name = "";

	private Map<String, RegionStyle> regions = new LinkedHashMap<>();

	/**
	 * Game UI colour, tints and opacity. Null on presets written before this existed, which
	 * {@link #gameUi()} turns into defaults rather than a crash.
	 */
	private GameUiStyle gameUi;

	/**
	 * Custom borders, keyed by the same region ids as {@link #regions}. Absent on presets written
	 * before borders existed, which is why applying one skips any region it has no entry for --
	 * loading an old preset should not switch off a feature it has never heard of.
	 */
	private Map<String, BorderPreset> borders = new LinkedHashMap<>();

	public Preset(String name)
	{
		this.name = name;
		this.regions = new LinkedHashMap<>();
	}

	/**
	 * The Game UI style, or a default one if this preset predates it.
	 */
	public GameUiStyle gameUi()
	{
		if (gameUi == null)
		{
			gameUi = new GameUiStyle();
		}
		return gameUi;
	}

	/**
	 * The style for a region, or a default one if this preset predates that region.
	 */
	public RegionStyle region(String regionId)
	{
		if (regions == null)
		{
			regions = new LinkedHashMap<>();
		}
		return regions.computeIfAbsent(regionId, id -> new RegionStyle());
	}
}
