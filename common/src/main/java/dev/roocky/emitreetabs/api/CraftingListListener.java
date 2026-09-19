package dev.roocky.emitreetabs.api;

/**
 * Told when the aggregated crafting list changes.
 *
 * <p>The list is rebuilt from every tree being worked on whenever anything that feeds it moves — a
 * tree is edited, a tab is parked, the player's inventory changes. So this fires often, and a
 * listener that does real work in it will be felt.
 */
@FunctionalInterface
public interface CraftingListListener {

	/**
	 * The list has been rebuilt.
	 *
	 * <p>Called on the client thread, during the rebuild's own pass. Do not register or unregister
	 * anything from inside it, and do not open a screen.
	 */
	void craftingListChanged();
}
