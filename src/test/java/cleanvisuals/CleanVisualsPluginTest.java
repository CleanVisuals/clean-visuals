package cleanvisuals;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CleanVisualsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CleanVisualsPlugin.class);
		RuneLite.main(args);
	}
}
