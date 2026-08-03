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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.SpritePixels;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.module.PluginLifecycleComponent;
import net.runelite.client.util.ImageUtil;

/**
 * Replaces the login screen background, and optionally hides or recolours the furniture on top
 * of it -- the flames, the logo and the login box.
 * <p>
 * The login screen is not a widget surface, so neither region strategy applies: there is
 * nothing to draw an overlay beneath and no backing widget whose opacity could be lowered. The
 * client exposes {@code setLoginScreen} directly instead, which is simpler than either.
 * <p>
 * Framing and colour come from {@link RegionBackground}, so fit, zoom, focal point, hue and
 * saturation behave exactly as they do for the chatbox and side panel.
 */
@Singleton
@Slf4j
public class LoginScreenBackground implements PluginLifecycleComponent
{
	/**
	 * Native size of the login screen artwork. Composing to it avoids the client rescaling and
	 * softening the result.
	 */
	private static final int WIDTH = 765;
	private static final int HEIGHT = 503;

	/**
	 * How often to check whether an animated background should advance. Not the frame rate --
	 * the frame is chosen from wall clock, so this only bounds how late a frame can be.
	 */
	private static final long TICK_MS = 40;

	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final CleanVisualsConfig config;

	private final RegionBackground background = new RegionBackground();

	/**
	 * Converted sprites per frame, so a looping animation costs a lookup rather than a full
	 * image conversion on every push.
	 */
	private final Map<Integer, SpritePixels> frameSprites = new HashMap<>();

	private String appliedKey;
	private int lastFrame = -1;
	private long startedAtMs = System.currentTimeMillis();
	private Future<?> animation;

	@Inject
	LoginScreenBackground(Client client, ClientThread clientThread, ScheduledExecutorService executor,
		CleanVisualsConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.config = config;
	}

	/**
	 * Runs for the flames toggle as well as the background, since hiding the flames is useful on
	 * its own and gating it behind the background feature would make it silently do nothing.
	 */
	@Override
	public boolean isEnabled(CleanVisualsConfig config)
	{
		return config.loginBackground() || config.loginHideFlames();
	}

	@Override
	public void startUp()
	{
		appliedKey = null;
		startedAtMs = System.currentTimeMillis();
		clientThread.invokeLater(this::apply);
	}

	@Override
	public void shutDown()
	{
		stopAnimation();
		appliedKey = null;
		frameSprites.clear();
		background.clear();

		clientThread.invokeLater(() ->
		{
			client.setLoginScreen(null);
			client.setShouldRenderLoginScreenFire(true);
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Logging out rebuilds the login screen, and a sprite set while logged in does not
		// survive that, so it has to be reapplied on arrival.
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			clientThread.invokeLater(this::apply);
		}
		else
		{
			stopAnimation();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CleanVisualsConfig.GROUP_NAME.equals(event.getGroup()))
		{
			clientThread.invokeLater(this::apply);
		}
	}

	private void apply()
	{
		applyFurniture();

		if (!config.loginBackground())
		{
			// Flames only. Reached when the flames toggle is on without a custom background, so
			// the background must be left alone -- and released if it was previously pushed,
			// otherwise turning the option off would leave the last image on screen forever.
			stopAnimation();
			if (appliedKey != null)
			{
				appliedKey = null;
				lastFrame = -1;
				frameSprites.clear();
				background.clear();
				client.setLoginScreen(null);
			}
			return;
		}

		background.setImagePath(config.loginImagePath());

		BackgroundTransform transform = currentTransform();
		ImageAdjustments adjustments = currentAdjustments();

		String key = config.loginImagePath() + "|" + transform + "|" + adjustments;
		if (!key.equals(appliedKey))
		{
			appliedKey = key;
			frameSprites.clear();
			lastFrame = -1;
			startedAtMs = System.currentTimeMillis();
		}

		pushFrame(transform, adjustments, background.frameAt(System.currentTimeMillis() - startedAtMs));

		if (background.isAnimated())
		{
			startAnimation();
		}
		else
		{
			stopAnimation();
		}
	}

	private BackgroundTransform currentTransform()
	{
		return new BackgroundTransform(
			config.loginFit(),
			config.loginZoom(),
			clampPercent(config.loginFocalX()) / 100d,
			clampPercent(config.loginFocalY()) / 100d);
	}

	private ImageAdjustments currentAdjustments()
	{
		return new ImageAdjustments(
			config.loginHue(), config.loginSaturation(), config.loginGrayscale());
	}

	/**
	 * The login screen has no per-frame render hook -- overlays do not render there -- so an
	 * animation has to be driven by pushing a new sprite on a timer.
	 */
	private void startAnimation()
	{
		if (animation != null && !animation.isDone())
		{
			return;
		}

		animation = executor.scheduleWithFixedDelay(() -> clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGIN_SCREEN)
			{
				return;
			}

			// Settings are read fresh on every tick rather than captured when the animation
			// started. Capturing them pinned the animation to whatever was configured at load
			// time, so any later adjustment was applied once and then immediately overwritten
			// by the next tick -- which looked like the image snapping back on its own.
			pushFrame(currentTransform(), currentAdjustments(),
				background.frameAt(System.currentTimeMillis() - startedAtMs));

			// Reassert the furniture. Pushing a login screen sprite repeatedly appears to let
			// the client rebuild parts of the screen from its own sprites, which is why the
			// logo and login box work with a still image but not behind an animation.
			applyFurniture();
		}), TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
	}

	private void stopAnimation()
	{
		if (animation != null)
		{
			animation.cancel(false);
			animation = null;
		}
	}

	private void pushFrame(BackgroundTransform transform, ImageAdjustments adjustments, int frame)
	{
		if (frame == lastFrame)
		{
			return;
		}

		SpritePixels sprite = frameSprites.get(frame);
		if (sprite == null)
		{
			BufferedImage composed = background.composeFor(WIDTH, HEIGHT, transform, adjustments, frame);
			if (composed == null)
			{
				client.setLoginScreen(null);
				return;
			}
			sprite = ImageUtil.getImageSpritePixels(composed, client);
			frameSprites.put(frame, sprite);
		}

		lastFrame = frame;
		client.setLoginScreen(sprite);
	}

	/**
	 * The flames, which are the only piece of login furniture the client lets us touch.
	 *
	 * <h2>Why the logo and login box are not here</h2>
	 *
	 * They were, via sprite overrides, and it never worked. A diagnostic confirmed the overrides
	 * were written correctly -- all six logo sprites and all eight box sprites present in
	 * {@code getSpriteOverrides()}, while the game state was {@code LOGIN_SCREEN} -- and both still
	 * drew on screen unchanged.
	 * <p>
	 * The title screen is rendered by the client from its own sprites, outside the override path,
	 * so writing into that map has no effect there. Three things point the same way: {@code Client}
	 * exposes only {@link Client#setLoginScreen} and {@link Client#setShouldRenderLoginScreenFire}
	 * for this screen, RuneLite's own login screen plugin offers no logo or box hiding, and the
	 * pack-based {@code LoginScreenOverride} reaches for {@code setLoginScreen} rather than a
	 * sprite override even for the background.
	 * <p>
	 * <b>Do not reimplement this with sprite overrides.</b> Recolouring the furniture is
	 * unreachable for the same reason, and so is replacing it with a custom image.
	 */
	private void applyFurniture()
	{
		// Set unconditionally: the client resets this whenever the login screen rebuilds, so it
		// cannot be applied once and left.
		client.setShouldRenderLoginScreenFire(!config.loginHideFlames());
	}

	private static int clampPercent(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
}
