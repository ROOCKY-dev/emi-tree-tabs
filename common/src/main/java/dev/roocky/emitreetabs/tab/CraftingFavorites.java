package dev.roocky.emitreetabs.tab;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.sidebar.CraftingGroups;
import dev.roocky.emitreetabs.sidebar.ChoiceEntry;
import dev.roocky.emitreetabs.sidebar.CraftingSidebarType;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiResolutionRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Feeds EMI's crafting mode from every tracked tree instead of only the visible one.
 *
 * <p>EMI's {@code EmiFavorites.updateSynthetic} reads {@code BoM.tree}, so rather than
 * reimplementing its accounting we run <em>EMI's own</em> pass once per tab with that field pointed
 * at each tree, then merge the results. That keeps the arithmetic EMI's problem, not ours.
 *
 * <p>Trees are costed against a <em>shrinking</em> pool rather than each getting the whole
 * inventory: whatever the first tree does not consume is what the second one gets. Without that,
 * two trees each needing 10 iron with 10 in the chest would both report themselves satisfied and
 * the merged list would ask for none. The pool carries crafting by-products forward too, so a
 * tree's leftovers are available to the next one.
 */
public final class CraftingFavorites {
	/** Set while we are driving EMI's own updateSynthetic, so our injector lets it through. */
	private static boolean reentrant;
	/**
	 * Set while other mods' listeners are being told about a finished list. A listener that asks
	 * for another rebuild from in there (which the listener contract forbids) gets the list as it
	 * stands rather than a nested pass that would rebuild it under the listeners still iterating.
	 */
	private static boolean notifying;
	/** Whether the last published list was empty, so a run of empty passes notifies only once. */
	private static boolean publishedEmpty = true;

	private CraftingFavorites() {
	}

	/**
	 * @return true when we produced the crafting list ourselves and EMI's own body should be skipped.
	 */
	public static boolean aggregate(EmiPlayerInventory inventory) {
		if (reentrant) {
			return false;
		}
		if (notifying) {
			return true;
		}
		// Cleared up front so every exit below leaves the sections consistent with the list. Missing
		// this is what left the sidebar showing a tree that had already been emptied: the flat list
		// was cleared by EMI but our sections still held the old contents.
		CraftingGroups.begin();
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.aggregateCraftingFavorites) {
			return false;
		}
		// Deliberately not gated on BoM.craftingMode: a tree being worked on should keep feeding the
		// crafting list even while you are looking at a different tab.
		List<TreeTab> included = new ArrayList<>();
		for (TreeTab tab : TreeTabs.tabs()) {
			// A parked group is set aside: its trees stay open and keep their own crafting flags,
			// but they stop asking for materials. That is the entire point of parking a phase.
			if (tab.craftingMode && !TreeTabs.isParked(tab)
					&& tab.tree != null && tab.tree.goal != null) {
				included.add(tab);
			}
		}
		if (included.isEmpty()) {
			if (!isOwnedByATab(BoM.tree)) {
				// A tree no tab has adopted yet (set directly by another addon): EMI's own pass
				// is the only thing that knows what to do with it.
				return false;
			}
			// Nothing is being worked on, so the list is empty. Produced here rather than left to
			// EMI's own body: EMI would still build a list from the active tree when that tree is in
			// crafting mode but its phase is parked, and parked phases are meant to ask for nothing.
			ATTRIBUTION = new LinkedHashMap<>();
			EmiFavorites.syntheticFavorites.clear();
			publish(true);
			return true;
		}

		// [batches, amount, total] per recipe, and [needed, total] per leftover material.
		Map<EmiRecipe, long[]> recipeTotals = new LinkedHashMap<>();
		Map<EmiIngredient, long[]> costTotals = new LinkedHashMap<>();
		// Which tabs each material is for, and how much each of them wants. Free here because we are
		// already walking every tree, and it is the only place the split is knowable: once the totals
		// are summed the question "how much of this copper is for plates" cannot be answered.
		Map<EmiIngredient, Map<TreeTab, long[]>> costOwners = new LinkedHashMap<>();
		// [consumed, missing if each tree had the whole stock] per recipe, over every tree in the
		// pass, and the item each recipe is making. See countDemand.
		Map<EmiRecipe, long[]> demand = new HashMap<>();
		Map<EmiRecipe, EmiStack> products = new HashMap<>();
		// The order the merged list is shown in. See orderCrafts.
		Set<EmiRecipe> order = new LinkedHashSet<>();
		EmiPlayerInventory stock = inventory;
		MaterialTree restore = BoM.tree;
		boolean anything = false;

		reentrant = true;
		SubCraftCosts.beginPass();
		try {
			// Materials another mod says the player has elsewhere join the pool before any tree
			// draws on it, so a chest three rooms away counts the same as a pocket - which is the
			// point. Off until the player turns it on: see TreeTabsConfig.useExternalStock.
			stock = withExternalStock(inventory);
			EmiPlayerInventory pool = stock;
			for (TreeTab tab : included) {
				EmiPlayerInventory before = pool;
				BoM.tree = tab.tree;
				BoM.craftingMode = true;
				// Watch this tree's costing, so each raw material can be attributed to the
				// sub-craft that wanted it. Only knowable from inside EMI's own walk.
				SubCraftCosts.begin(tab);
				EmiFavorites.updateSynthetic(pool);
				SubCraftCosts.end();
				// Before the done check below: a tree with nothing left to make still consumed
				// its share, and leaving it out is what made "/4 obtained" drop to "/3".
				countDemand(tab.tree, before, stock, demand, products);
				orderCrafts(tab.tree, order);
				// Costed against the pool, so whatever this tree claimed is gone for the next one.
				// Valid even when the tree had nothing to do: it still earmarked what it consumed.
				if (TreeTabsConfig.sharedCraftingInventory) {
					pool = leftovers(tab.tree);
				}
				if (!BoM.craftingMode) {
					// EMI turns the flag off for a tree with nothing left to do; that tab is done,
					// so drop it back to viewing.
					tab.craftingMode = false;
					continue;
				}
				anything = true;
				for (EmiFavorite.Synthetic entry : EmiFavorites.syntheticFavorites) {
					if (entry.getRecipe() != null) {
						long[] totals = recipeTotals.computeIfAbsent(entry.getRecipe(), key -> new long[3]);
						totals[0] += entry.batches;
						totals[1] += entry.amount;
						totals[2] += entry.total;
					} else {
						long[] totals = costTotals.computeIfAbsent(entry.getStack(), key -> new long[2]);
						totals[0] += entry.amount;
						totals[1] += entry.total;
						long[] mine = costOwners
								.computeIfAbsent(entry.getStack(), key -> new LinkedHashMap<>())
								.computeIfAbsent(tab, key -> new long[2]);
						mine[0] += entry.amount;
						mine[1] += entry.total;
					}
				}
			}
		} finally {
			reentrant = false;
			SubCraftCosts.endPass();
			// EMI's globals go back on every exit, including a throw: otherwise BoM.tree is left
			// pointing at whichever tree the pass had reached. Listeners are not told here; see
			// publish().
			BoM.tree = restore;
			// The global flag is only about what the open screen shows, so restore the active tab's.
			TreeTab activeTab = TreeTabs.activeTab();
			BoM.craftingMode = activeTab != null && activeTab.craftingMode;
		}

		// Published before the early return, so a run that produced nothing also clears the old split
		// rather than leaving a stale one to be read against the next tree.
		ATTRIBUTION = costOwners;
		EmiFavorites.syntheticFavorites.clear();
		if (!anything) {
			publish(true);
			return true;
		}
		// Recipes first, then raw materials, matching the order EMI emits for a single tree. Recipes
		// come out in EMI's order too: a product before the parts it is made from.
		CraftingGroups.Group toCraft = CraftingGroups.group("craft",
				Component.translatable("emi.tree_tabs.group.craft"));
		for (EmiRecipe recipe : order) {
			long[] totals = recipeTotals.get(recipe);
			if (totals == null) {
				continue;
			}
			long amount = totals[1];
			long total = totals[2];
			long[] consumed = demand.get(recipe);
			EmiStack product = products.get(recipe);
			if (consumed != null && product != null && !product.isEmpty()) {
				// Crafting only happens once the pool has run dry, so whenever anything is crafted the
				// stock was used up first: what is missing is simply demand minus stock.
				long missing = TreeTabsConfig.sharedCraftingInventory
						? Math.max(0, consumed[0] - held(stock, product))
						: consumed[1];
				// EMI's own count can only be lower than the real one, never higher; keeping the larger
				// is a guard against a case this arithmetic has not foreseen.
				amount = Math.max(amount, missing);
				total = Math.max(consumed[0], amount);
			}
			int state = inventory.canCraft(recipe, totals[0]) ? 2 : inventory.canCraft(recipe) ? 1 : 0;
			EmiFavorite.Synthetic synthetic =
					new EmiFavorite.Synthetic(recipe, totals[0], amount, total, state);
			EmiFavorites.syntheticFavorites.add(synthetic);
			toCraft.entries.add(synthetic);
		}

		CraftingGroups.Group shared = CraftingGroups.group("shared",
				Component.translatable("emi.tree_tabs.group.shared"));
		for (Map.Entry<EmiIngredient, long[]> entry : costTotals.entrySet()) {
			long[] totals = entry.getValue();
			EmiFavorite.Synthetic synthetic =
					ChoiceEntry.of(entry.getKey(), totals[0], totals[1]);
			EmiFavorites.syntheticFavorites.add(synthetic);

			Set<TreeTab> owners = costOwners.containsKey(entry.getKey())
					? costOwners.get(entry.getKey()).keySet() : null;
			if (owners == null || owners.size() != 1) {
				shared.entries.add(synthetic);
			} else {
				TreeTab owner = owners.iterator().next();
				CraftingGroups.group("tab:" + System.identityHashCode(owner), owner.displayName())
						.entries.add(synthetic);
			}
		}
		publish(false);
		return true;
	}

	private static boolean isOwnedByATab(MaterialTree tree) {
		if (tree == null) {
			return true;
		}
		for (TreeTab tab : TreeTabs.tabs()) {
			if (tab.tree == tree) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Tells other mods the list has been rebuilt, once it really has been.
	 *
	 * <p>Called only after the merged list, its sections and the attribution are all in place, and
	 * after EMI's globals are restored. The call used to sit in the pass's {@code finally}, which
	 * ran it before any of that was assigned (a listener reading the list saw the previous one) and
	 * also on a pass that had thrown, announcing a rebuild that never happened.
	 *
	 * <p>Empty results are announced too, so a listener sees the list empty out when the last tree
	 * stops being crafted, but only on the change to empty. With nothing being crafted every
	 * inventory change still runs a pass, and repeating "still empty" to every listener each time
	 * would be noise.
	 */
	private static void publish(boolean empty) {
		if (empty && publishedEmpty) {
			return;
		}
		publishedEmpty = empty;
		notifying = true;
		try {
			ApiRegistry.get().craftingListChanged();
		} finally {
			notifying = false;
		}
	}

	/**
	 * How much of a shared material each tree wants, most demanding first.
	 *
	 * <p>The crafting list can say "184 copper" but not what it is for, so deciding whether to spend
	 * it on plates now or save it for pipes means doing the arithmetic by hand. This is the split,
	 * kept from the one moment it is knowable: after the totals are summed it is gone.
	 */
	public static List<Attribution> attribution(EmiIngredient stack) {
		Map<TreeTab, long[]> owners = ATTRIBUTION.get(stack);
		if (owners == null || owners.isEmpty()) {
			return List.of();
		}
		List<Attribution> out = new ArrayList<>();
		for (Map.Entry<TreeTab, long[]> e : owners.entrySet()) {
			out.add(new Attribution(e.getKey(), e.getValue()[0], e.getValue()[1]));
		}
		out.sort((a, b) -> Long.compare(b.needed(), a.needed()));
		return out;
	}

	/** One tree's share of a shared material. */
	public record Attribution(TreeTab tab, long needed, long total) {
	}

	private static Map<EmiIngredient, Map<TreeTab, long[]>> ATTRIBUTION = new LinkedHashMap<>();

	/**
	 * Which registered source is offering each item, from the last pass.
	 *
	 * <p>Kept rather than asked for, because the tooltip that wants it is rebuilt every frame the
	 * cursor is on the item and a third-party mod should be called once per crafting-list pass, not
	 * sixty times a second.
	 */
	private static Map<EmiStack, Component> STOCK_LABELS = new LinkedHashMap<>();

	/**
	 * What to call the place this material is coming from, or null when it is the player's own
	 * inventory.
	 *
	 * <p>"You have it" and "you have it three rooms away" are different answers, and a crafting
	 * list that silently stops asking for something on the strength of the second one is worse
	 * than one that never counted it.
	 */
	public static Component stockLabel(EmiIngredient ingredient) {
		if (STOCK_LABELS.isEmpty() || ingredient == null) {
			return null;
		}
		for (EmiStack stack : ingredient.getEmiStacks()) {
			Component label = STOCK_LABELS.get(stack.copy().setAmount(1));
			if (label != null) {
				return label;
			}
		}
		return null;
	}

	/**
	 * The player's inventory plus whatever registered stock sources are offering.
	 *
	 * <p>Built as a copy rather than by adding to EMI's own inventory object, which belongs to EMI
	 * and is handed back to it for its own uses.
	 */
	private static EmiPlayerInventory withExternalStock(EmiPlayerInventory inventory) {
		ApiRegistry api = ApiRegistry.get();
		if (!api.hasStock()) {
			return inventory;
		}
		Map<EmiStack, Component> labels = new LinkedHashMap<>();
		List<ItemStack> offered = api.stock((stack, label) ->
				labels.putIfAbsent(EmiStack.of(stack).copy().setAmount(1), label));
		STOCK_LABELS = labels;
		if (offered.isEmpty()) {
			return inventory;
		}
		EmiPlayerInventory merged = new EmiPlayerInventory(List.of());
		merged.inventory.clear();
		merged.inventory.putAll(inventory.inventory);
		for (ItemStack stack : offered) {
			EmiStack key = EmiStack.of(stack).copy().setAmount(1);
			EmiStack existing = merged.inventory.get(key);
			long have = existing == null ? 0 : existing.getAmount();
			EmiStack combined = EmiStack.of(stack).copy().setAmount(have + stack.getCount());
			merged.inventory.put(key, combined);
		}
		return merged;
	}

	/**
	 * Drops everything this class remembers about the world that just went away.
	 *
	 * <p>Both maps are keyed or valued by {@link TreeTab}, and a TreeTab holds a
	 * {@code MaterialTree} — which is the whole node graph, and every {@code EmiRecipe} in it. Left
	 * alone they pin the previous world's recipes for as long as the client runs, which is the
	 * exact leak {@code TreeTabs.releaseTrees} was written to prevent.
	 */
	public static void release() {
		ATTRIBUTION = new LinkedHashMap<>();
		STOCK_LABELS = new LinkedHashMap<>();
	}

	/**
	 * Moves every recipe in the tree to the end of {@code order}, in EMI's own order for that tree.
	 *
	 * <p>Within one tree EMI lists a product before the parts it is made from: its
	 * {@code countRecipes} moves a recipe to the end each time it meets it again. Ordering by last
	 * sighting across trees keeps that true for the merged list, because every tree already places a
	 * recipe after everything that consumes it.
	 *
	 * <p>Taken from {@code countRecipes} rather than from the tree's crafting list, which leaves out
	 * whatever that tree no longer has to make. A part covered by an earlier tree's leftovers was
	 * then never sighted in the later tree, so a product made from it there could move past it:
	 * small piles of a dust ended up listed after the dust they are crafted from.
	 */
	private static void orderCrafts(MaterialTree tree, Set<EmiRecipe> order) {
		Object2LongMap<EmiRecipe> batches = new Object2LongLinkedOpenHashMap<>();
		EmiFavorites.countRecipes(batches, new Object2LongLinkedOpenHashMap<>(), tree.goal);
		for (EmiRecipe recipe : batches.keySet()) {
			order.remove(recipe);
			order.add(recipe);
		}
	}

	/**
	 * Adds how many of each crafted item this tree actually uses to {@code demand}.
	 *
	 * <p>EMI's own "remaining" count adds up what each node asked for at the moment it crafted, so a
	 * node that was handed the spare half of another node's batch counts nothing. A recipe making
	 * two steel rods per craft, wanted in six places, reads "4 remaining" and "3 crafts": the crafts
	 * are right, but six rods are needed, and that is the number the player is counting towards.
	 * Holding four does not finish the list, holding five still leaves one missing, and after
	 * stocking some of them the count barely moves. Plain EMI does this too.
	 *
	 * <p>The real use falls out of the walk EMI has just done: whatever was available before, plus
	 * what the crafts make, minus what is left over afterwards. Unlike EMI's per-node counts this
	 * adds up across trees, so the merged list can be right where summing EMI's could not.
	 *
	 * <p>Read after {@code updateSynthetic}, while the nodes and remainders still hold its last
	 * pass, which is the one against {@code before}.
	 */
	private static void countDemand(MaterialTree tree, EmiPlayerInventory before, EmiPlayerInventory stock,
			Map<EmiRecipe, long[]> demand, Map<EmiRecipe, EmiStack> products) {
		Map<EmiRecipe, long[]> made = new LinkedHashMap<>();
		collectCrafts(tree.goal, made, products);
		for (Map.Entry<EmiRecipe, long[]> entry : made.entrySet()) {
			EmiStack product = products.get(entry.getKey());
			if (product == null || product.isEmpty()) {
				continue;
			}
			if (tree.cost.chanceRemainders.containsKey(product)) {
				// Leftovers of a chanced craft are kept apart, where the arithmetic below cannot see
				// them; EMI's own count stands for that recipe.
				products.put(entry.getKey(), EmiStack.EMPTY);
				continue;
			}
			FlatMaterialCost left = tree.cost.remainders.get(product);
			long used = held(before, product) + entry.getValue()[0] * entry.getValue()[1]
					- (left == null ? 0 : left.amount);
			used = Math.max(0, used);
			long[] totals = demand.computeIfAbsent(entry.getKey(), key -> new long[2]);
			totals[0] += used;
			totals[1] += Math.max(0, used - held(stock, product));
		}
	}

	/**
	 * [batches, items per batch] for every recipe in the tree, and the item each one is there for.
	 *
	 * <p>The item is the first stack of the node's ingredient, which is the key EMI files a craft's
	 * spare output under. A recipe used for two different outputs is marked with an empty stack and
	 * left to EMI's count, since one leftover figure cannot describe both.
	 */
	private static void collectCrafts(MaterialNode node, Map<EmiRecipe, long[]> made,
			Map<EmiRecipe, EmiStack> products) {
		if (node == null || node.recipe == null || node.children == null) {
			return;
		}
		if (node.recipe instanceof EmiResolutionRecipe) {
			// Only a pointer to the item actually chosen, as in EMI's own countRecipes.
			collectCrafts(node.children.get(0), made, products);
			return;
		}
		if (node.divisor > 0) {
			EmiStack product = node.ingredient.getEmiStacks().get(0);
			products.merge(node.recipe, product, (a, b) -> a.equals(b) ? a : EmiStack.EMPTY);
			made.computeIfAbsent(node.recipe, key -> new long[] {0, node.divisor})[0] += node.neededBatches;
		}
		for (MaterialNode child : node.children) {
			collectCrafts(child, made, products);
		}
	}

	private static long held(EmiPlayerInventory inventory, EmiStack stack) {
		EmiStack held = inventory.inventory.get(stack);
		return held == null ? 0 : held.getAmount();
	}

	/**
	 * What a tree left unconsumed, as an inventory for the next tree to draw on.
	 *
	 * <p>{@code TreeCost.remainders} starts life as the inventory and is decremented as the tree
	 * claims materials, so after a progress pass it is exactly the unclaimed remainder, plus any
	 * by-products the tree's own recipes would produce.
	 *
	 * <p>Chanced remainders are deliberately ignored — a maybe-drop is not something to promise the
	 * next tree it already has.
	 */
	private static EmiPlayerInventory leftovers(MaterialTree tree) {
		// The list constructor also folds in the cursor stack, so clear it and fill the map
		// directly. EMI does the same thing when it needs an inventory it fully controls.
		EmiPlayerInventory next = new EmiPlayerInventory(List.of());
		next.inventory.clear();
		for (Map.Entry<EmiStack, FlatMaterialCost> entry : tree.cost.remainders.entrySet()) {
			long amount = entry.getValue().amount;
			if (amount <= 0) {
				continue;
			}
			EmiStack stack = entry.getKey().copy().setAmount(amount);
			next.inventory.put(stack, stack);
		}
		return next;
	}

	/**
	 * Swaps EMI's favourites sidebar list for one we control. {@code EmiFavorites.favoriteSidebar} is
	 * a plain public static field that EMI reads fresh on every sidebar query, so this needs no mixin.
	 */
	public static void installSidebarView() {
		if (!(EmiFavorites.favoriteSidebar instanceof SidebarView)) {
			EmiFavorites.favoriteSidebar = new SidebarView();
		}
	}

	/**
	 * EMI's own compound list (favourites, then the crafting entries), with the crafting half
	 * dropped once something else is showing it.
	 */
	private static final class SidebarView extends AbstractList<EmiFavorite> {

		/**
		 * Whether the favourites panel should carry the crafting list as well.
		 *
		 * <p>Note what this deliberately cannot do: replace favourites. Favourites either has the
		 * crafting list appended, exactly as stock EMI does, or it has only favourites. An earlier
		 * option swapped one for the other while crafting mode was on, which read as favourites
		 * vanishing and fought the dedicated Crafting panel; it is gone.
		 */
		private boolean carriesCraftingList() {
			if (!TreeTabsConfig.enabled) {
				return true;
			}
			if (!TreeTabsConfig.craftingInFavorites) {
				return false;
			}
			// A panel of our own is already showing it; showing it twice is worse than not at all.
			if (CraftingSidebarType.TYPE == null
					&& !"NONE".equalsIgnoreCase(TreeTabsConfig.craftingPanelSide)) {
				return false;
			}
			return !CraftingSidebarType.isPlacedInSidebar();
		}

		@Override
		public EmiFavorite get(int index) {
			List<EmiFavorite> favorites = EmiFavorites.favorites;
			if (carriesCraftingList() && index >= favorites.size()) {
				return EmiFavorites.syntheticFavorites.get(index - favorites.size());
			}
			return favorites.get(index);
		}

		@Override
		public int size() {
			int size = EmiFavorites.favorites.size();
			return carriesCraftingList() ? size + EmiFavorites.syntheticFavorites.size() : size;
		}
	}
}
