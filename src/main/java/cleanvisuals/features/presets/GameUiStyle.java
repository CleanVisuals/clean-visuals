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
import lombok.Data;

/**
 * The Game UI half of a preset: frame colour, tints, opacity and the border toggle.
 * <p>
 * Presets used to cover backgrounds only, so saving a look captured the wallpaper but not the
 * interface colour that went with it -- loading "night" restored the image and left the frame
 * whatever the last thing you fiddled with had made it. A theme is both halves.
 * <p>
 * Field defaults match the config defaults, so a preset saved before this existed loads as
 * "Game UI untouched" rather than stamping zeros over the user's settings.
 */
@Data
public class GameUiStyle
{
	private boolean recolourEnabled = false;
	private int hueShiftDegrees = 0;
	private int saturationPercent = 100;
	private boolean grayscale = false;

	private int tintStrength = 100;

	/**
	 * Per-group tint colours as {@code #AARRGGBB} strings, keyed by {@code UiGroup} name.
	 * <p>
	 * A map rather than a field per group so adding a group later does not invalidate every
	 * preset already written -- an unknown key is ignored and a missing one defaults to no tint.
	 */
	private Map<String, String> tints = new LinkedHashMap<>();

	/**
	 * Which groups the colour treatment applies to, keyed by {@code UiGroup} name.
	 */
	private Map<String, Boolean> groups = new LinkedHashMap<>();

	private boolean opacityEnabled = false;
	private int opacityPercent = 0;

	private boolean hideSidePanelBorder = false;

	/**
	 * Which groups are hidden outright, keyed by {@code UiGroup} name.
	 * <p>
	 * A map for the same reason as {@link #tints}: a group added later leaves older presets valid
	 * rather than needing a migration, and a missing entry means "leave that toggle alone".
	 */
	private Map<String, Boolean> hidden = new LinkedHashMap<>();

	/**
	 * The chat tab colour exceptions -- the report button and the unread-message flash keeping
	 * their own colour through a black and white frame.
	 * <p>
	 * Boxed rather than primitive, unlike the fields above, because these were added after presets
	 * already existed in the wild. A primitive would read as {@code false} on every older preset
	 * and switch the exceptions off on load; null lets applying skip them instead.
	 */
	private Boolean reportColourEnabled;
	private String reportColour;
	private Boolean notifyColourEnabled;
	private String notifyColour;
}
