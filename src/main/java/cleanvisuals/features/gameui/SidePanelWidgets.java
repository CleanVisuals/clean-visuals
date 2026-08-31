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

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Where the side panel is, for the features that need to draw on or around it.
 * <p>
 * Kept in one place because the mapping is not guessable and was established by measurement:
 * the interface in use is the opposite of what the names suggest. Modern runs 164
 * ({@code ToplevelPreEoc}) and classic runs 161 ({@code ToplevelOsrsStretch}). Whichever is not
 * in use reports hidden, so taking the first visible candidate resolves correctly without
 * relying on that mapping being remembered correctly at each call site.
 * <p>
 * {@code SIDE_STATIC_BACKGROUND} and {@code SIDE_MOVABLE_BACKGROUND} are deliberately not here:
 * measurement showed they are the two 36px tab icon strips ({@code [660,546 231x36]} and
 * {@code [660,510 231x36]} in modern), not panel backing. Treating them as backing set their
 * opacity to fully transparent, which is what removed the stone from behind the tab icons.
 */
public final class SidePanelWidgets
{
	private SidePanelWidgets()
	{
	}

	/**
	 * Every widget that could be the side panel backing, in either layout. Callers that set
	 * properties on the panel want all of these, since the wrong-layout one is simply hidden.
	 */
	public static Widget[] candidates(Client client)
	{
		return new Widget[]{
			client.getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_BACKGROUND),
			client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_BACKGROUND)
		};
	}

	/**
	 * The side panel as currently laid out, or null when it is not on screen -- on the login
	 * screen, or in a layout that does not have one.
	 */
	public static Widget visible(Client client)
	{
		for (Widget candidate : candidates(client))
		{
			if (candidate != null && !candidate.isHidden())
			{
				return candidate;
			}
		}
		return null;
	}
}
