package dev.roocky.emitreetabs.fabric;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.config.YaclConfigScreen;
import dev.roocky.emitreetabs.ui.ConfigScreenHook;
import dev.roocky.emitreetabs.ui.TabUi;
import net.fabricmc.loader.api.FabricLoader;
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
		registerConfigScreen();

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
			TabUi.reset();
			TreeTabs.releaseTrees();
		});
	}

	/**
	 * Wires up the settings screen, which Fabric did not have at all before: the Cloth screen this
	 * replaces was written in the Forge module and never ported.
	 *
	 * <p>Guarded on YACL being loaded, for the reason the Forge side is: {@code YaclConfigScreen}
	 * is the only class touching YACL types and is never referenced unless the check passes.
	 *
	 * <p>Mod Menu's own entrypoint is deliberately not implemented — that would be a second
	 * optional dependency for a second way into the same screen. The sidebar's settings button
	 * opens it, and Mod Menu can be added later if anyone asks.
	 */
	private static void registerConfigScreen() {
		if (!FabricLoader.getInstance().isModLoaded(EmiTreeTabs.YACL)) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] {} not present, edit config/{}.json by hand",
					EmiTreeTabs.YACL, EmiTreeTabs.MOD_ID);
			return;
		}
		try {
			ConfigScreenHook.set(YaclConfigScreen::create);
		} catch (Throwable t) {
			// A settings screen is not worth taking the game down for.
			EmiTreeTabs.LOGGER.warn("[emitreetabs] could not register the config screen", t);
		}
	}
}
