package dev.roocky.emitreetabs.tab;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.api.CraftingListListener;
import dev.roocky.emitreetabs.api.LocateProvider;
import dev.roocky.emitreetabs.api.StockSource;
import dev.roocky.emitreetabs.api.TreeTabsRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Holds what other mods have registered, and calls it without trusting it.
 *
 * <p>Every call out to a consumer is wrapped. A third-party mod throwing inside a crafting-list
 * pass would otherwise take out the crafting list, the sidebar and the tree screen with it, and the
 * player would have no way of knowing which mod did it. So a source that throws is logged once and
 * dropped for the session — quietly degrading to "that integration is not working" rather than
 * loudly to "EMI is broken".
 */
public final class ApiRegistry implements TreeTabsRegistry {

	/** One line per consumer, once. A source that throws every frame must not fill the log. */
	private static final Map<String, Boolean> REPORTED = new LinkedHashMap<>();

	private final Map<String, StockSource> stockSources = new LinkedHashMap<>();
	private final Map<String, LocateProvider> locateProviders = new LinkedHashMap<>();
	private final List<CraftingListListener> listeners = new ArrayList<>();

	private static final ApiRegistry INSTANCE = new ApiRegistry();

	private ApiRegistry() {
	}

	public static ApiRegistry get() {
		return INSTANCE;
	}

	// ------------------------------------------------------- the public side

	@Override
	public void registerStockSource(String id, StockSource source) {
		if (id == null || source == null) {
			return;
		}
		stockSources.put(id, source);
		EmiTreeTabs.LOGGER.info("[emitreetabs] stock source registered by {}", id);
	}

	@Override
	public void registerLocateProvider(String id, LocateProvider provider) {
		if (id == null || provider == null) {
			return;
		}
		locateProviders.put(id, provider);
		EmiTreeTabs.LOGGER.info("[emitreetabs] locate provider registered by {}", id);
	}

	@Override
	public void unregister(String id) {
		stockSources.remove(id);
		locateProviders.remove(id);
	}

	@Override
	public void addCraftingListListener(CraftingListListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	@Override
	public int craftingTreeCount() {
		return TreeTabs.craftingCount();
	}

	// ----------------------------------------------------- the internal side

	/** Whether anything at all is registered, so the common case costs one null check. */
	public boolean hasStock() {
		return TreeTabsConfig.useExternalStock && !stockSources.isEmpty();
	}

	public boolean hasLocate() {
		return !locateProviders.isEmpty();
	}

	/**
	 * Everything every source is offering, flattened.
	 *
	 * <p>Not cached: a chest's contents can change between passes and a stale pool would tell the
	 * player they already have something they spent. The sources are asked to be cheap for exactly
	 * this reason.
	 */
	public List<ItemStack> stock(java.util.function.BiConsumer<ItemStack, Component> label) {
		List<ItemStack> out = new ArrayList<>();
		for (Map.Entry<String, StockSource> entry : stockSources.entrySet()) {
			List<ItemStack> offered = guard(entry.getKey(), () -> entry.getValue().stock());
			if (offered == null) {
				continue;
			}
			// Asked once per pass, not once per item: a source's name does not change mid-pass,
			// and the tooltip must not be asking a third-party mod anything every frame.
			Component name = guard(entry.getKey(), () -> entry.getValue().label());
			for (ItemStack stack : offered) {
				if (stack != null && !stack.isEmpty()) {
					out.add(stack);
					if (label != null && name != null) {
						label.accept(stack, name);
					}
				}
			}
		}
		return out;
	}

	/** Where a shortfall can be found, from every provider, in registration order. */
	public List<Component> locate(ItemStack stack) {
		List<Component> out = new ArrayList<>();
		for (Map.Entry<String, LocateProvider> entry : locateProviders.entrySet()) {
			List<Component> found = guard(entry.getKey(), () -> entry.getValue().locate(stack));
			if (found != null) {
				out.addAll(found);
			}
		}
		return out;
	}

	/** Tells every listener the list changed. One throwing must not stop the others. */
	public void craftingListChanged() {
		for (CraftingListListener listener : listeners) {
			try {
				listener.craftingListChanged();
			} catch (Throwable t) {
				report("a crafting-list listener", t);
			}
		}
	}

	private <T> T guard(String id, java.util.function.Supplier<T> call) {
		try {
			return call.get();
		} catch (Throwable t) {
			report(id, t);
			// Dropped for the session rather than asked again every frame.
			stockSources.remove(id);
			locateProviders.remove(id);
			return null;
		}
	}

	private static void report(String id, Throwable t) {
		if (REPORTED.putIfAbsent(id, Boolean.TRUE) == null) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] {} threw; dropping its integration for this "
					+ "session. This is a bug in that mod, not in EMI.", id, t);
		}
	}
}
