package dev.roocky.emitreetabs.fabric;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.TabBar;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Fabric entrypoint. Everything of substance lives in the shared module; this only wires up the
 * events, which is why they were never abstracted behind the platform interface.
 */
public class EmiTreeTabsFabric implements ClientModInitializer {

	private static int tickCounter;

	@Override
	public void onInitializeClient() {
		EmiTreeTabs.initClient();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Tabs cannot be rebuilt until EMI has finished indexing recipes, which happens after
			// BoM.reload runs. This retries until it can; the common case is a null check.
			TreeTabs.tryRestore();
			// Pick up hand edits to the json, one file stat every couple of seconds.
			if (++tickCounter >= 40) {
				tickCounter = 0;
				TreeTabsConfig.reloadIfChanged();
			}
		});

		// Leaving a world must drop every live MaterialTree: they hold EmiRecipe objects belonging
		// to the world that just went away.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TabBar.reset();
			TreeTabs.releaseTrees();
		});
	}
}
