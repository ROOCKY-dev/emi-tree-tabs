package dev.ahmed.emitreetabs;

import dev.ahmed.emitreetabs.tab.TreeTabs;
import dev.ahmed.emitreetabs.TreeTabsConfig;
import dev.ahmed.emitreetabs.ui.TabBar;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client lifecycle glue: retry deferred tab restores, and drop world-scoped state on disconnect.
 */
@Mod.EventBusSubscriber(modid = EmiTreeTabs.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

	private ClientEvents() {
	}

	/**
	 * Tabs cannot be rebuilt until EMI has finished indexing recipes, which happens after
	 * {@code BoM.reload()} runs. This retries until it can. The common case is a null check.
	 */
	private static int tickCounter;

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
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
		TabBar.reset();
		TreeTabs.releaseTrees();
	}
}
