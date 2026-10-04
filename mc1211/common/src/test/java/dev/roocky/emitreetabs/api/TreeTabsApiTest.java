package dev.roocky.emitreetabs.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The version gate, which is the part of the API that has to be right.
 *
 * <p>Everything else degrades visibly — an integration does not show up, and someone notices. This
 * is the piece whose failure mode is a crash on a user's machine in a build that compiled cleanly,
 * so it is the piece worth testing without a game.
 */
class TreeTabsApiTest {

	@Test
	@DisplayName("a consumer built against this version gets the registry")
	void currentVersionIsServed() {
		TreeTabsApi.install(new NoopRegistry());
		assertNotNull(TreeTabsApi.registry(TreeTabsApi.VERSION));
	}

	@Test
	@DisplayName("a consumer built against a newer Tree Tabs is turned away, not half-served")
	void futureVersionIsRefused() {
		TreeTabsApi.install(new NoopRegistry());
		assertNull(TreeTabsApi.registry(TreeTabsApi.VERSION + 1),
				"a newer consumer would call methods this build does not have");
	}

	@Test
	@DisplayName("a version below anything we ever shipped is refused")
	void nonsenseVersionIsRefused() {
		TreeTabsApi.install(new NoopRegistry());
		assertNull(TreeTabsApi.registry(0));
		assertNull(TreeTabsApi.registry(-1));
	}

	@Test
	@DisplayName("asking before Tree Tabs has installed anything returns null rather than throwing")
	void nullBeforeInstall() {
		TreeTabsApi.install(null);
		assertNull(TreeTabsApi.registry(TreeTabsApi.VERSION));
	}

	@Test
	@DisplayName("the same registry comes back every time, so consumers can hold it")
	void registryIsStable() {
		NoopRegistry registry = new NoopRegistry();
		TreeTabsApi.install(registry);
		assertSame(registry, TreeTabsApi.registry(TreeTabsApi.VERSION));
		assertSame(registry, TreeTabsApi.registry(TreeTabsApi.VERSION));
	}

	/** Implemented here on purpose: if a method is added to the interface, this stops compiling. */
	private static final class NoopRegistry implements TreeTabsRegistry {
		@Override
		public void registerStockSource(String id, StockSource source) {
		}

		@Override
		public void registerLocateProvider(String id, LocateProvider provider) {
		}

		@Override
		public void unregister(String id) {
		}

		@Override
		public void addCraftingListListener(CraftingListListener listener) {
		}

		@Override
		public int craftingTreeCount() {
			return 0;
		}
	}
}
