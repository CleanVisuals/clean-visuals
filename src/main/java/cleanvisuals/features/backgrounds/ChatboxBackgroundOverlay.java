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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import cleanvisuals.CleanVisualsConfig;

/**
 * Background behind the chatbox.
 */
@Singleton
public class ChatboxBackgroundOverlay extends RegionBackgroundOverlay
{
	private final CleanVisualsConfig config;

	@Inject
	ChatboxBackgroundOverlay(Client client, ClientThread clientThread, CleanVisualsConfig config)
	{
		super(client, clientThread);
		this.config = config;
	}

	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.chatboxBackground();
	}

	@Override
	protected Widget boundsWidget()
	{
		return client.getWidget(InterfaceID.Chatbox.CHAT_BACKGROUND);
	}

	@Override
	protected int obstructionSpriteId()
	{
		return SpriteID.CHAT_BACKGROUND;
	}

	@Override
	protected String regionName()
	{
		return "chatbox";
	}

	@Override
	protected boolean diagnosticsEnabled()
	{
		return config.showDiagnostics();
	}

	@Override
	protected RegionSettings settings()
	{
		return new RegionSettings(
			config.chatboxImagePath(),
			config.chatboxFit(),
			config.chatboxZoom(),
			config.chatboxFocalX(),
			config.chatboxFocalY(),
			config.chatboxHue(),
			config.chatboxSaturation(),
			config.chatboxGrayscale(),
			config.chatboxImageOpacity(),
			config.chatboxWidgetTransparency());
	}
}
