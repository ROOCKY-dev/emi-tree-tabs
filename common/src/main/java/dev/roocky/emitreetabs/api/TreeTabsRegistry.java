package dev.roocky.emitreetabs.api;

/**
 * What a consumer can add to Tree Tabs.
 *
 * <p>Obtained from {@link TreeTabsApi#registry(int)}, which returns null rather than an
 * incompatible registry. Every method here is safe to call more than once; registering under an id
 * that is already taken replaces the previous registration, so a mod reloading its own integration
 * does not accumulate duplicates.
 *
 * <p>New methods may be added in a future version without bumping
 * {@link TreeTabsApi#VERSION} — an older consumer simply never calls them.
 */
public interface TreeTabsRegistry {

	/**
	 * Adds a source of materials the player has somewhere other than their inventory.
	 *
	 * <p>Its stock joins the pool the crafting list is costed against, so a tree that needs 64 iron
	 * and finds it in a registered source stops asking for it. Shown distinctly from the player's
	 * own inventory, because "you have it" and "you have it three rooms away" are different answers.
	 *
	 * <p><strong>Off until the player turns it on.</strong> Silently changing what the crafting list
	 * asks for, on the strength of another mod's idea of what the player owns, is not something to
	 * do by default.
	 *
	 * @param id     a stable id, conventionally the consumer's mod id
	 * @param source queried during a crafting-list pass; must not block
	 */
	void registerStockSource(String id, StockSource source);

	/**
	 * Adds an answer to "where do I find this".
	 *
	 * <p>Surfaced from a shortfall: it is asked about a material the crafting list says the player
	 * is short of, which is the moment the question gets asked.
	 *
	 * @param id       a stable id, conventionally the consumer's mod id
	 * @param provider queried while a tooltip is being built; must not block
	 */
	void registerLocateProvider(String id, LocateProvider provider);

	/** Removes a registration made under this id, whichever kind it was. */
	void unregister(String id);

	/**
	 * Called after the crafting list has been rebuilt from every tracked tree.
	 *
	 * <p>Listeners are not keyed by id and cannot be removed individually; they live for the
	 * session. Keep them cheap — this fires whenever the list changes, which is often.
	 */
	void addCraftingListListener(CraftingListListener listener);

	/** How many trees are currently being worked on, which is what feeds the crafting list. */
	int craftingTreeCount();
}
