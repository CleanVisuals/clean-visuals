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

import lombok.Getter;
import net.runelite.api.gameval.SpriteID;

/**
 * Named clusters of game frame sprites that are recoloured together.
 * <p>
 * Every id here was read from a live dump rather than taken from a constant name, because the
 * names in this area are consistently misleading: the frame is drawn from 1173-1179, while the
 * {@code SIDE_BACKGROUND_TOP/BOTTOM/LEFT/RIGHT} family (1032-1036), which describes exactly
 * this job, is not used at all. The side panel's content backing is sprite 897,
 * {@code TRADEBACKING_DARK}.
 * <p>
 * Ids are grouped rather than listed flat so a control can say "the minimap" instead of
 * "sprites 1177, 1178 and 1179".
 */
public enum UiGroup
{
	MINIMAP("Minimap",
		sprites(1177, 1178, 1179),
		widgets(161, 32, 30, 29), widgets(164, 32, 30, 29)),

	/**
	 * Classic draws the strips from 1173/1174 and adds side edges 1175/1176; modern draws both
	 * strips from 1180 and has no edges at all. Both sets are listed together.
	 */
	SIDE_PANEL_FRAME("Side panel frame",
		sprites(1173, 1174, 1175, 1176, 1180),
		widgets(161, 41, 57, 39, 40), widgets(164, 36, 50)),

	/**
	 * The panel behind the tabs, and the stone border around it -- both come from the same
	 * sprite, which is why the border has no widget of its own.
	 * <p>
	 * Classic uses 897 (TRADEBACKING_DARK), modern uses 1040 (TRADEBACKING_LIGHT).
	 */
	SIDE_PANEL_BACKING("Side panel backing",
		sprites(897, 1040),
		widgets(161, 38), widgets(164, 70)),

	/**
	 * The icons on the side panel tabs -- combat, stats, quests, inventory, equipment, prayer,
	 * magic and the rest, in both tab rows.
	 * <p>
	 * Read from a live dump, where they appear as 33x36 sprites under interface 164 children
	 * 44-49 and 55-65. Without them the icons stay stock while the frame around them recolours,
	 * which reads as a seam rather than as a deliberate two-tone.
	 */
	TAB_ICONS("Tab icons",
		sprites(774, 779, 780, 781, 782, 898, 900, 901, 908, 909, 910, 1181, 1709, 2309),
		widgets(164, 44, 45, 46, 47, 48, 49, 55, 59, 60, 61, 62, 63, 64, 65),
		widgets(161, 44, 45, 46, 47, 48, 49, 55, 59, 60, 61, 62, 63, 64, 65)),

	CHAT_BAR("Chat bar",
		sprites(1018),
		widgets(162, 3)),

	ORBS("Orbs",
		sprites(1071),
		widgets(160, 8, 19, 27, 35)),

	/**
	 * The tabs under the chatbox -- All, Game, Public, Private and the rest -- plus the report
	 * button beside them. Measured at y=559 in the dump and confirmed by the names.
	 */
	CHAT_TABS("Chat tabs",
		sprites(SpriteID.ChatTabButton._0, SpriteID.ChatTabButton._1, SpriteID.ChatTabButton._2,
			SpriteID.ChatTabButton._3, SpriteID.ChatTabButton._4, SpriteID.ChatTabButton._5,
			SpriteID.ReportButton._0, SpriteID.ReportButton._1),
		widgets(162, 5, 8, 12, 16, 20, 24, 28, 32)),

	/**
	 * The 9-slice tiles OSRS builds its in-panel buttons from -- "All Settings", "Upgrade Now"
	 * and similar. Corner, edge and centre pieces in both the grey and red variants.
	 * <p>
	 * These are included from their names rather than from a dump, which has been unreliable
	 * elsewhere in this file. It is safe here in a way it is not for widget lookups: an unused
	 * sprite id simply recolours nothing, where a wrong widget id silently falls back and
	 * breaks behaviour.
	 */
	/**
	 * Scrollbars: troughs, draggers and separators, in the light, dark and horizontal variants.
	 * <p>
	 * Small individually, but they appear in nearly every interface in the game, so leaving them
	 * stock is the seam that gives away a half-finished theme.
	 * <p>
	 * No widget ids: scrollbars are built per interface rather than living at fixed components,
	 * so there is nothing stable to set opacity on. Colour works because sprite overrides are
	 * keyed by id and land wherever the sprite is drawn.
	 */
	SCROLLBARS("Scrollbars",
		sprites(773, 788, 789, 790, 791, 792, 801, 802,
			2954, 2955, 2956, 2957, 2958, 2959,
			4533, 4534, 4535, 4536, 4537, 4538),
		widgets(0)),

	BUTTONS("Buttons",
		sprites(SpriteID.MISCGRAPHICS_BUTTONMIDDLE_GREY, SpriteID.MISCGRAPHICS_BUTTONMIDDLE_RED,
		SpriteID.MISCGRAPHICS_BUTTONTILE_NW_GREY, SpriteID.MISCGRAPHICS_BUTTONTILE_NC_GREY,
		SpriteID.MISCGRAPHICS_BUTTONTILE_NE_GREY, SpriteID.MISCGRAPHICS_BUTTONTILE_CW_GREY,
		SpriteID.MISCGRAPHICS_BUTTONTILE_CC_GREY, SpriteID.MISCGRAPHICS_BUTTONTILE_CE_GREY,
		SpriteID.MISCGRAPHICS_BUTTONTILE_SW_GREY, SpriteID.MISCGRAPHICS_BUTTONTILE_SC_GREY,
		SpriteID.MISCGRAPHICS_BUTTONTILE_SE_GREY,
		SpriteID.MISCGRAPHICS_BUTTONTILE_NW_RED, SpriteID.MISCGRAPHICS_BUTTONTILE_NC_RED,
		SpriteID.MISCGRAPHICS_BUTTONTILE_NE_RED, SpriteID.MISCGRAPHICS_BUTTONTILE_CW_RED,
		SpriteID.MISCGRAPHICS_BUTTONTILE_CC_RED, SpriteID.MISCGRAPHICS_BUTTONTILE_CE_RED,
		SpriteID.MISCGRAPHICS_BUTTONTILE_SW_RED, SpriteID.MISCGRAPHICS_BUTTONTILE_SC_RED,
		SpriteID.MISCGRAPHICS_BUTTONTILE_SE_RED,
		SpriteID.BUTTON_BROWN, SpriteID.BUTTON_BROWN_BIG, SpriteID.BUTTON_RED),
		widgets(0));

	@Getter
	private final String label;

	private final int[] spriteIds;

	/**
	 * Packed widget component ids, for opacity. Sprite transparency is binary, so opacity
	 * cannot come from the sprite and has to be applied to the widget instead.
	 */
	private final int[] widgetIds;

	// Explicit rather than Lombok-generated: @RequiredArgsConstructor cannot produce varargs.
	UiGroup(String label, int[] spriteIds, int[]... widgetIdGroups)
	{
		this.label = label;
		this.spriteIds = spriteIds;

		int total = 0;
		for (int[] group : widgetIdGroups)
		{
			total += group.length;
		}

		int[] merged = new int[total];
		int at = 0;
		for (int[] group : widgetIdGroups)
		{
			System.arraycopy(group, 0, merged, at, group.length);
			at += group.length;
		}
		this.widgetIds = merged;
	}

	public int[] spriteIds()
	{
		return spriteIds.clone();
	}

	public int[] widgetIds()
	{
		return widgetIds.clone();
	}

	private static int[] sprites(int... ids)
	{
		return ids;
	}

	/**
	 * Packs an interface group and its children into component ids.
	 * <p>
	 * Both layouts' ids are listed for every group. Including the one not currently in use is
	 * harmless -- it resolves to null and is skipped -- and avoids depending on a mapping from
	 * layout to interface, which has been wrong every time it was assumed.
	 */
	private static int[] widgets(int group, int... children)
	{
		int[] ids = new int[children.length];
		for (int i = 0; i < children.length; i++)
		{
			ids[i] = (group << 16) | children[i];
		}
		return ids;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
