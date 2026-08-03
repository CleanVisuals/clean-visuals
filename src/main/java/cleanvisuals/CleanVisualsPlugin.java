package cleanvisuals;

import com.google.inject.Binder;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import cleanvisuals.module.ComponentManager;
import cleanvisuals.module.CleanVisualsModule;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Clean Visuals"
)
@Slf4j
public class CleanVisualsPlugin extends Plugin
{
	@Inject
	private ComponentManager componentManager;

	@Override
	public void configure(Binder binder)
	{
		binder.install(new CleanVisualsModule());
	}

	@Override
	protected void startUp() throws Exception
	{
		componentManager.onPluginStart();
	}

	@Override
	protected void shutDown()
	{
		componentManager.onPluginStop();
	}
}
