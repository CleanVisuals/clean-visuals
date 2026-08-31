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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import cleanvisuals.CleanVisualsConfig;

/**
 * A custom border around the side panel.
 * <p>
 * Pairs with {@link SidePanelBorderHide}: that removes the game's own border, this draws one in
 * its place. They are independent settings because either is useful alone -- a border removed and
 * nothing put back is the clean look, and a custom border over the top of the original is a way
 * to have one without the global side effects of hiding it.
 */
@Singleton
public class SidePanelBorder extends RegionBorderOverlay
{
	private final CleanVisualsConfig config;

	@Inject
	SidePanelBorder(Client client, CleanVisualsConfig config)
	{
		super(client);
		this.config = config;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.sidePanelBorder();
	}

	@Override
	protected Widget boundsWidget()
	{
		return SidePanelWidgets.visible(client);
	}

	@Override
	protected BorderSettings settings()
	{
		return new BorderSettings(
			config.sidePanelBorderStyle(),
			config.sidePanelBorderColour(),
			config.sidePanelBorderThickness(),
			config.sidePanelBorderOpacity());
	}
}
