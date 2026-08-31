package cleanvisuals;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import cleanvisuals.features.backgrounds.ImageFit;
import cleanvisuals.features.gameui.BorderStyle;

@ConfigGroup(CleanVisualsConfig.GROUP_NAME)
public interface CleanVisualsConfig extends Config
{
	String GROUP_NAME = "cleanvisuals";

	/**
	 * "No tint chosen" for the per-group colour pickers.
	 * <p>
	 * Zero alpha rather than a separate enable checkbox per group: eight groups would otherwise
	 * need sixteen config items, and a fully transparent colour has no other sensible meaning
	 * here anyway.
	 */
	Color NO_TINT = new Color(0, 0, 0, 0);

	@ConfigSection(name = "Game UI colour",
		description = "Recolours the game frame",
		position = 12,
		closedByDefault = true
	)
	String gameUiSection = "gameUiSection";

	@ConfigSection(name = "Hide UI parts",
		description = "Removes interface decoration entirely. Affects every interface sharing those sprites",
		position = 13,
		closedByDefault = true
	)
	String hideSection = "hideSection";

	@ConfigSection(name = "Side panel border",
		description = "Draws a border of your own around the side panel",
		position = 14,
		closedByDefault = true
	)
	String borderSection = "borderSection";

	@ConfigSection(name = "Chatbox border",
		description = "Draws a border of your own around the chatbox",
		position = 15,
		closedByDefault = true
	)
	String chatboxBorderSection = "chatboxBorderSection";

	@ConfigSection(name = "Chat tabs",
		description = "Exceptions to the colour treatment, for the parts of the chat tabs that carry meaning",
		position = 16,
		closedByDefault = true
	)
	String chatTabsSection = "chatTabsSection";

	@ConfigItem(
		keyName = "chatboxBackground",
		name = "Enable chatbox background",
		description = "Draws an image behind the chatbox and makes the chatbox background see-through",
		position = 1,
		hidden = true
	)
	default boolean chatboxBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatboxImagePath",
		name = "Image file",
		description = "Full path to an image. Leave empty for a magenta/cyan test pattern",
		position = 2,
		hidden = true
	)
	default String chatboxImagePath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "chatboxFit",
		name = "Fit",
		description = "How the image is sized against the region before zoom and position",
		position = 3,
		hidden = true
	)
	default ImageFit chatboxFit()
	{
		return ImageFit.FILL;
	}

	@Range(min = 10, max = 400)
	@ConfigItem(
		keyName = "chatboxZoom",
		name = "Zoom %",
		description = "Zoom relative to the fitted size. 100 = as Fit produced it",
		position = 4,
		hidden = true
	)
	default int chatboxZoom()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "chatboxFocalX",
		name = "Position X %",
		description = "Which point of the image sits at the centre. 0 = left edge, 100 = right edge",
		position = 5,
		hidden = true
	)
	default int chatboxFocalX()
	{
		return 50;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "chatboxFocalY",
		name = "Position Y %",
		description = "Which point of the image sits at the centre. 0 = top edge, 100 = bottom edge",
		position = 6,
		hidden = true
	)
	default int chatboxFocalY()
	{
		return 50;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "chatboxImageOpacity",
		name = "Image opacity",
		description = "How strongly the background image is drawn (100 = fully visible)",
		position = 7,
		hidden = true
	)
	default int chatboxImageOpacity()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "chatboxWidgetTransparency",
		name = "Chatbox see-through",
		description = "How see-through the chatbox background becomes (0 = unchanged, 100 = fully see-through)",
		position = 8,
		hidden = true
	)
	default int chatboxWidgetTransparency()
	{
		return 100;
	}
	@ConfigItem(
		keyName = "chatboxHue",
		name = "Hue shift",
		description = "Rotates colours around the colour wheel, in degrees. 0 = unchanged",
		position = 9,
		hidden = true
	)
	@Range(min = -180, max = 180)
	default int chatboxHue()
	{
		return 0;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "chatboxSaturation",
		name = "Saturation %",
		description = "0 = grey, 100 = unchanged, 200 = double intensity",
		position = 10,
		hidden = true
	)
	default int chatboxSaturation()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "chatboxGrayscale",
		name = "Black & white",
		description = "Converts the image to greyscale. Overrides hue and saturation",
		position = 11,
		hidden = true
	)
	default boolean chatboxGrayscale()
	{
		return false;
	}


	@ConfigItem(
		keyName = "invBackground",
		name = "Enable side panel background",
		description = "Draws an image behind the side panel, visible on every tab",
		position = 1,
		hidden = true
	)
	default boolean invBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "invImagePath",
		name = "Image file",
		description = "Full path to an image. Leave empty for a test pattern",
		position = 2,
		hidden = true
	)
	default String invImagePath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "invFit",
		name = "Fit",
		description = "How the image is sized against the region before zoom and position",
		position = 3,
		hidden = true
	)
	default ImageFit invFit()
	{
		return ImageFit.FILL;
	}

	@Range(min = 10, max = 400)
	@ConfigItem(
		keyName = "invZoom",
		name = "Zoom %",
		description = "Zoom relative to the fitted size",
		position = 4,
		hidden = true
	)
	default int invZoom()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "invFocalX",
		name = "Position X %",
		description = "Which point of the image sits at the centre. 0 = left, 100 = right",
		position = 5,
		hidden = true
	)
	default int invFocalX()
	{
		return 50;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "invFocalY",
		name = "Position Y %",
		description = "Which point of the image sits at the centre. 0 = top, 100 = bottom",
		position = 6,
		hidden = true
	)
	default int invFocalY()
	{
		return 50;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "invImageOpacity",
		name = "Image opacity",
		description = "How strongly the image is drawn",
		position = 7,
		hidden = true
	)
	default int invImageOpacity()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "invWidgetTransparency",
		name = "Side panel see-through",
		description = "How see-through the side panel backing becomes",
		position = 8,
		hidden = true
	)
	default int invWidgetTransparency()
	{
		return 100;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "invHue",
		name = "Hue shift",
		description = "Rotates colours around the colour wheel, in degrees",
		position = 10,
		hidden = true
	)
	default int invHue()
	{
		return 0;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "invSaturation",
		name = "Saturation %",
		description = "0 = grey, 100 = unchanged, 200 = double intensity",
		position = 11,
		hidden = true
	)
	default int invSaturation()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "invGrayscale",
		name = "Black & white",
		description = "Converts the image to greyscale. Overrides hue and saturation",
		position = 12,
		hidden = true
	)
	default boolean invGrayscale()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideSidePanelBorder",
		name = "Side panel border",
		description = "Removes the border around the side panel in modern layout. Stone style only -- steel leaves a "
			+ "divider that cannot be identified. These sprites are shared with other interfaces, so their borders go too",
		position = 4,
		section = hideSection
	)
	default boolean hideSidePanelBorder()
	{
		return false;
	}


	@ConfigItem(
		keyName = "loginBackground",
		name = "Enable login screen background",
		description = "Replaces the login screen background with your own image",
		position = 1,
		hidden = true
	)
	default boolean loginBackground()
	{
		return false;
	}

	@ConfigItem(
		keyName = "loginImagePath",
		name = "Image file",
		description = "Full path to an image. Leave empty for a test pattern",
		position = 2,
		hidden = true
	)
	default String loginImagePath()
	{
		return "";
	}

	@ConfigItem(
		keyName = "loginFit",
		name = "Fit",
		description = "How the image is sized against the login screen before zoom and position",
		position = 3,
		hidden = true
	)
	default ImageFit loginFit()
	{
		return ImageFit.FILL;
	}

	@Range(min = 10, max = 400)
	@ConfigItem(
		keyName = "loginZoom",
		name = "Zoom %",
		description = "Zoom relative to the fitted size",
		position = 4,
		hidden = true
	)
	default int loginZoom()
	{
		return 100;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "loginFocalX",
		name = "Position X %",
		description = "Which point of the image sits at the centre. 0 = left, 100 = right",
		position = 5,
		hidden = true
	)
	default int loginFocalX()
	{
		return 50;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "loginFocalY",
		name = "Position Y %",
		description = "Which point of the image sits at the centre. 0 = top, 100 = bottom",
		position = 6,
		hidden = true
	)
	default int loginFocalY()
	{
		return 50;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "loginHue",
		name = "Hue shift",
		description = "Rotates colours around the colour wheel, in degrees",
		position = 7,
		hidden = true
	)
	default int loginHue()
	{
		return 0;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "loginSaturation",
		name = "Saturation %",
		description = "0 = grey, 100 = unchanged, 200 = double intensity",
		position = 8,
		hidden = true
	)
	default int loginSaturation()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "loginGrayscale",
		name = "Black & white",
		description = "Converts the image to greyscale. Overrides hue and saturation",
		position = 9,
		hidden = true
	)
	default boolean loginGrayscale()
	{
		return false;
	}

	@ConfigItem(
		keyName = "loginHideFlames",
		name = "Login screen flames",
		description = "Hides the burning braziers either side of the login screen",
		position = 10,
		section = hideSection
	)
	default boolean loginHideFlames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gameUiRecolour",
		name = "Enable game UI colour",
		description = "Recolours the game frame",
		position = 1,
		section = gameUiSection
	)
	default boolean gameUiRecolour()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gameUiOpacityEnabled",
		name = "Enable see-through UI",
		description = "Makes the game frame see-through",
		position = 6,
		section = gameUiSection
	)
	default boolean gameUiOpacityEnabled()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "gameUiOpacity",
		name = "UI see-through",
		description = "0 = unchanged, 100 = fully see-through. Applies to the groups ticked below",
		position = 7,
		section = gameUiSection
	)
	default int gameUiOpacity()
	{
		return 0;
	}

	@Range(min = -180, max = 180)
	@ConfigItem(
		keyName = "gameUiHue",
		name = "Hue shift",
		description = "Rotates the frame's colours around the colour wheel, in degrees",
		position = 2,
		section = gameUiSection
	)
	default int gameUiHue()
	{
		return 0;
	}

	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = "gameUiSaturation",
		name = "Saturation %",
		description = "0 = grey, 100 = unchanged, 200 = double intensity",
		position = 3,
		section = gameUiSection
	)
	default int gameUiSaturation()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "gameUiGrayscale",
		name = "Black & white",
		description = "Converts the frame to greyscale. Overrides hue and saturation",
		position = 4,
		section = gameUiSection
	)
	default boolean gameUiGrayscale()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gameUiMinimap",
		name = "Minimap",
		description = "Minimap surround, inner ring and compass",
		position = 10,
		section = gameUiSection
	)
	default boolean gameUiMinimap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiSidePanelFrame",
		name = "Side panel frame",
		description = "Side panel edges and the tab icon strips",
		position = 12,
		section = gameUiSection
	)
	default boolean gameUiSidePanelFrame()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiSidePanelBacking",
		name = "Side panel backing",
		description = "The panel behind the inventory and other tabs",
		position = 14,
		section = gameUiSection
	)
	default boolean gameUiSidePanelBacking()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiChatBar",
		name = "Chat bar",
		description = "The bar holding the chat tabs",
		position = 18,
		section = gameUiSection
	)
	default boolean gameUiChatBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiOrbs",
		name = "Orbs",
		description = "Hitpoints, prayer, run energy and special attack orbs",
		position = 20,
		section = gameUiSection
	)
	default boolean gameUiOrbs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "gameUiChatTabs",
		name = "Chat tabs",
		description = "The All / Game / Public / Private tabs under the chatbox, and the report button",
		position = 22,
		section = gameUiSection
	)
	default boolean gameUiChatTabs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiButtons",
		name = "Buttons",
		description = "In-panel buttons such as All Settings, Upgrade Now and View Inbox",
		position = 26,
		section = gameUiSection
	)
	default boolean gameUiButtons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gameUiTabIcons",
		name = "Tab icons",
		description = "The combat, stats, quests, inventory, prayer and magic tab icons",
		position = 16,
		section = gameUiSection
	)
	default boolean gameUiTabIcons()
	{
		return false;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "gameUiTintStrength",
		name = "Tint strength",
		description = "How strongly the tint colours below are applied. 0 disables tinting entirely",
		position = 5,
		section = gameUiSection
	)
	default int gameUiTintStrength()
	{
		return 100;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintMinimap",
		name = "Minimap tint",
		description = "Recolours the minimap surround to this colour. Fully transparent means no tint",
		position = 11,
		section = gameUiSection
	)
	default Color gameUiTintMinimap()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintSidePanelFrame",
		name = "Side panel frame tint",
		description = "Recolours the side panel edges and tab strips. Fully transparent means no tint",
		position = 13,
		section = gameUiSection
	)
	default Color gameUiTintSidePanelFrame()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintSidePanelBacking",
		name = "Side panel backing tint",
		description = "Recolours the panel behind the tabs. Fully transparent means no tint",
		position = 15,
		section = gameUiSection
	)
	default Color gameUiTintSidePanelBacking()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintTabIcons",
		name = "Tab icons tint",
		description = "Recolours the side panel tab icons. Fully transparent means no tint",
		position = 17,
		section = gameUiSection
	)
	default Color gameUiTintTabIcons()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintChatBar",
		name = "Chat bar tint",
		description = "Recolours the bar holding the chat tabs. Fully transparent means no tint",
		position = 19,
		section = gameUiSection
	)
	default Color gameUiTintChatBar()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintOrbs",
		name = "Orbs tint",
		description = "Recolours the hitpoints, prayer, run and special orbs. Fully transparent means no tint",
		position = 21,
		section = gameUiSection
	)
	default Color gameUiTintOrbs()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintChatTabs",
		name = "Chat tabs tint",
		description = "Recolours the All / Game / Public tabs. Fully transparent means no tint",
		position = 23,
		section = gameUiSection
	)
	default Color gameUiTintChatTabs()
	{
		return NO_TINT;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintButtons",
		name = "Buttons tint",
		description = "Recolours in-panel buttons. Fully transparent means no tint",
		position = 27,
		section = gameUiSection
	)
	default Color gameUiTintButtons()
	{
		return NO_TINT;
	}

	@ConfigItem(
		keyName = "gameUiScrollbars",
		name = "Scrollbars",
		description = "Scrollbar troughs, draggers and separators across every interface",
		position = 24,
		section = gameUiSection
	)
	default boolean gameUiScrollbars()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "gameUiTintScrollbars",
		name = "Scrollbars tint",
		description = "Recolours scrollbars. Fully transparent means no tint",
		position = 25,
		section = gameUiSection
	)
	default Color gameUiTintScrollbars()
	{
		return NO_TINT;
	}

	@ConfigItem(
		keyName = "hideMinimap",
		name = "Minimap surround",
		description = "Removes the stone frame around the minimap",
		position = 1,
		section = hideSection
	)
	default boolean hideMinimap()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideSidePanelFrame",
		name = "Side panel frame",
		description = "Removes the side panel edges and the tab icon strips",
		position = 2,
		section = hideSection
	)
	default boolean hideSidePanelFrame()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideSidePanelBacking",
		name = "Side panel backing",
		description = "Removes the panel behind the tabs. Also affects the trade and bank backdrops",
		position = 3,
		section = hideSection
	)
	default boolean hideSidePanelBacking()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideChatBar",
		name = "Chat bar",
		description = "Removes the bar holding the chat tabs",
		position = 5,
		section = hideSection
	)
	default boolean hideChatBar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideChatTabs",
		name = "Chat tabs",
		description = "Removes the All / Game / Public tab buttons and the report button",
		position = 7,
		section = hideSection
	)
	default boolean hideChatTabs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideOrbs",
		name = "Orb surrounds",
		description = "Removes the frames around the hitpoints, prayer, run and special orbs",
		position = 6,
		section = hideSection
	)
	default boolean hideOrbs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideScrollbars",
		name = "Scrollbars",
		description = "Removes scrollbar troughs and draggers. They still work, they are just invisible",
		position = 8,
		section = hideSection
	)
	default boolean hideScrollbars()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideButtons",
		name = "Button backgrounds",
		description = "Removes the tiled backing behind in-panel buttons, leaving their text",
		position = 9,
		section = hideSection
	)
	default boolean hideButtons()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sidePanelBorder",
		name = "Enable custom border",
		description = "Draws your own border around the side panel. Resizable layouts only. Looks best with the "
			+ "game's own border hidden, under Hide UI parts",
		position = 1,
		section = borderSection
	)
	default boolean sidePanelBorder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sidePanelBorderStyle",
		name = "Style",
		description = "How the border is drawn",
		position = 2,
		section = borderSection
	)
	default BorderStyle sidePanelBorderStyle()
	{
		return BorderStyle.SOLID;
	}

	@ConfigItem(
		keyName = "sidePanelBorderColour",
		name = "Colour",
		description = "The border's colour",
		position = 3,
		section = borderSection
	)
	default Color sidePanelBorderColour()
	{
		return new Color(60, 60, 60);
	}

	@Range(min = 1, max = 12)
	@ConfigItem(
		keyName = "sidePanelBorderThickness",
		name = "Thickness",
		description = "How thick the border is, in pixels",
		position = 4,
		section = borderSection
	)
	default int sidePanelBorderThickness()
	{
		return 3;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "sidePanelBorderOpacity",
		name = "Opacity",
		description = "How solid the border is. 0 = invisible, 100 = fully solid",
		position = 5,
		section = borderSection
	)
	default int sidePanelBorderOpacity()
	{
		return 100;
	}
	@ConfigItem(
		keyName = "chatboxBorder",
		name = "Enable custom border",
		description = "Draws your own border around the chatbox. Resizable layouts only",
		position = 1,
		section = chatboxBorderSection
	)
	default boolean chatboxBorder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatboxBorderStyle",
		name = "Style",
		description = "How the border is drawn",
		position = 2,
		section = chatboxBorderSection
	)
	default BorderStyle chatboxBorderStyle()
	{
		return BorderStyle.SOLID;
	}

	@ConfigItem(
		keyName = "chatboxBorderColour",
		name = "Colour",
		description = "The border's colour",
		position = 3,
		section = chatboxBorderSection
	)
	default Color chatboxBorderColour()
	{
		return new Color(60, 60, 60);
	}

	@Range(min = 1, max = 12)
	@ConfigItem(
		keyName = "chatboxBorderThickness",
		name = "Thickness",
		description = "How thick the border is, in pixels",
		position = 4,
		section = chatboxBorderSection
	)
	default int chatboxBorderThickness()
	{
		return 3;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "chatboxBorderOpacity",
		name = "Opacity",
		description = "How solid the border is. 0 = invisible, 100 = fully solid",
		position = 5,
		section = chatboxBorderSection
	)
	default int chatboxBorderOpacity()
	{
		return 100;
	}
	@ConfigItem(
		keyName = "chatReportColourEnabled",
		name = "Keep report button coloured",
		description = "Gives the report button its own colour, so black & white and the chat tab tint do not "
			+ "apply to it. Opacity still does",
		position = 1,
		section = chatTabsSection
	)
	default boolean chatReportColourEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatReportColour",
		name = "Report button colour",
		description = "The colour the report button is tinted",
		position = 2,
		section = chatTabsSection
	)
	default Color chatReportColour()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "chatNotifyColourEnabled",
		name = "Keep new message flash visible",
		description = "The flash that marks unread messages keeps its own colour instead of being greyscaled, "
			+ "so a notification is still noticeable on a black & white frame. It fades with UI "
			+ "see-through like everything else",
		position = 3,
		section = chatTabsSection
	)
	default boolean chatNotifyColourEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chatNotifyColour",
		name = "New message flash colour",
		description = "The colour the unread message flash is tinted",
		position = 4,
		section = chatTabsSection
	)
	default Color chatNotifyColour()
	{
		return Color.RED;
	}
}
