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

import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import cleanvisuals.CleanVisualsConfig;
import cleanvisuals.module.PluginLifecycleComponent;

/**
 * Gives a brand new installation something to look at.
 * <p>
 * Every feature here defaults to off, which is right for an update -- nobody's client should
 * change appearance because they installed a new version -- but it means switching the plugin on
 * for the first time does visibly nothing, and a plugin that appears to do nothing gets turned
 * back off. This applies the shipped starting look instead, once.
 *
 * <h2>Why not simply change the config defaults</h2>
 * A {@code default} in the config interface applies wherever a value has never been stored, and
 * RuneLite only stores a value once something changes it. Raising the defaults would therefore
 * reach past new installations and into every existing one that had not happened to touch those
 * particular settings -- their frame would turn greyscale and see-through on update, unasked.
 * Seeding stored values instead means the change lands only where there is nothing to disturb.
 */
@Singleton
@Slf4j
public class FirstRunSetup implements PluginLifecycleComponent
{
	private final ConfigManager configManager;
	private final PresetManager presetManager;

	@Inject
	FirstRunSetup(ConfigManager configManager, PresetManager presetManager)
	{
		this.configManager = configManager;
		this.presetManager = presetManager;
	}

	@Override
	public void startUp()
	{
		// Reads the stored profile rather than the defaults, so this is empty only on an
		// installation nobody has ever configured. Seeding writes keys, which is what stops it
		// running a second time -- including for someone who tries the look, turns it all off,
		// and restarts.
		if (!configManager.getConfigurationKeys(CleanVisualsConfig.GROUP_NAME + ".").isEmpty())
		{
			return;
		}

		try
		{
			presetManager.applyBundledDefaults();
			log.debug("Applied the bundled starting look to a fresh installation");
		}
		catch (IOException | RuntimeException e)
		{
			// Losing the starting look is a poor first impression. Losing the plugin to an
			// exception thrown during start-up would be worse.
			log.warn("Could not apply the bundled starting look", e);
		}
	}
}
