package dev.roocky.emitreetabs.neoforge;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.TabUi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client lifecycle glue: retry deferred tab restores, and drop world-scoped state on disconnect.
 *
 * <p>No {@code bus} attribute on the annotation: NeoForge subscribes game-bus listeners by default,
 * and naming the bus explicitly is deprecated for removal there.
 */
@EventBusSubscriber(modid = EmiTreeTabs.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

	private ClientEvents() {
	}

	/**
	 * Tabs cannot be rebuilt until EMI has finished indexing recipes, which happens after
	 * {@code BoM.reload()} runs. This retries until it can. The common case is a null check.
	 */
	private static int tickCounter;

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		TreeTabs.tryRestore();
		// Pick up hand edits to the json. Two seconds is often enough to feel live while keeping
		// this to one file stat rather than one per tick.
		if (++tickCounter >= 40) {
			tickCounter = 0;
			TreeTabsConfig.reloadIfChanged();
		}
	}

	/**
	 * Releases everything world-scoped when the player disconnects. Without this the mod would sit
	 * at the main menu still holding the previous world's {@code EmiRecipe} graph through its
	 * material trees. Tabs are kept as json and rebuilt on the next world.
	 */
	@SubscribeEvent
	public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		TabUi.reset();
		TreeTabs.releaseTrees();
	}
}
