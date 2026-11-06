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
	 * <p>Called on the client thread once the rebuild is complete: the list, its sections and the
	 * per-tree split are already final when this runs. Also called once when the list becomes
	 * empty, but not again for each further pass that is still empty. Not called for a rebuild
	 * that failed.
	 *
	 * <p>Do not register or unregister anything from inside it, and do not open a screen. Asking
	 * for another rebuild from in here is ignored. A listener that throws is dropped for the
	 * session.
	 */
	void craftingListChanged();
}
