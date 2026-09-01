package dev.ahmed.emitreetabs.tab;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.ahmed.emitreetabs.TreeTabsConfig;
import dev.ahmed.emitreetabs.sidebar.CraftingGroups;
import dev.ahmed.emitreetabs.sidebar.CraftingSidebarType;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.FlatMaterialCost;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import net.minecraft.network.chat.Component;

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

	private CraftingFavorites() {
	}

	/**
	 * @return true when we produced the crafting list ourselves and EMI's own body should be skipped.
	 */
	public static boolean aggregate(EmiPlayerInventory inventory) {
		if (reentrant) {
			return false;
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
			if (tab.craftingMode && tab.tree != null && tab.tree.goal != null) {
				included.add(tab);
			}
		}
		if (included.isEmpty()) {
			// No tree is being worked on. Let EMI handle it.
			return false;
		}

		// [batches, amount, total] per recipe, and [needed, total] per leftover material.
		Map<EmiRecipe, long[]> recipeTotals = new LinkedHashMap<>();
		Map<EmiIngredient, long[]> costTotals = new LinkedHashMap<>();
		// Which tabs each material is for, so a material wanted by two trees can be told from one
		// wanted by a single tree. Free here because we are already walking every tree.
		Map<EmiIngredient, Set<TreeTab>> costOwners = new LinkedHashMap<>();
		MaterialTree restore = BoM.tree;
		boolean anything = false;

		reentrant = true;
		try {
			EmiPlayerInventory pool = inventory;
			for (TreeTab tab : included) {
				BoM.tree = tab.tree;
				BoM.craftingMode = true;
				EmiFavorites.updateSynthetic(pool);
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
						costOwners.computeIfAbsent(entry.getStack(), key -> new LinkedHashSet<>()).add(tab);
					}
				}
			}
		} finally {
			reentrant = false;
			BoM.tree = restore;
			// The global flag is only about what the open screen shows, so restore the active tab's.
			TreeTab activeTab = TreeTabs.activeTab();
			BoM.craftingMode = activeTab != null && activeTab.craftingMode;
		}

		EmiFavorites.syntheticFavorites.clear();
		if (!anything) {
			return true;
		}
		// Recipes first, then raw materials, matching the order EMI emits for a single tree.
		CraftingGroups.Group toCraft = CraftingGroups.group("craft",
				Component.translatable("emi.tree_tabs.group.craft"));
		for (Map.Entry<EmiRecipe, long[]> entry : recipeTotals.entrySet()) {
			EmiRecipe recipe = entry.getKey();
			long[] totals = entry.getValue();
			int state = inventory.canCraft(recipe, totals[0]) ? 2 : inventory.canCraft(recipe) ? 1 : 0;
			EmiFavorite.Synthetic synthetic =
					new EmiFavorite.Synthetic(recipe, totals[0], totals[1], totals[2], state);
			EmiFavorites.syntheticFavorites.add(synthetic);
			toCraft.entries.add(synthetic);
		}

		CraftingGroups.Group shared = CraftingGroups.group("shared",
				Component.translatable("emi.tree_tabs.group.shared"));
		for (Map.Entry<EmiIngredient, long[]> entry : costTotals.entrySet()) {
			long[] totals = entry.getValue();
			EmiFavorite.Synthetic synthetic =
					new EmiFavorite.Synthetic(entry.getKey(), totals[0], totals[1]);
			EmiFavorites.syntheticFavorites.add(synthetic);

			Set<TreeTab> owners = costOwners.get(entry.getKey());
			if (owners == null || owners.size() != 1) {
				shared.entries.add(synthetic);
			} else {
				TreeTab owner = owners.iterator().next();
				CraftingGroups.group("tab:" + System.identityHashCode(owner), owner.displayName())
						.entries.add(synthetic);
			}
		}
		return true;
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
	 * Identical to EMI's own compound list (favourites then crafting entries) unless
	 * {@code separateCraftingFavorites} is on, in which case the two never appear together: crafting
	 * mode shows only the crafting list, and the rest of the time only real favourites.
	 */
	private static final class SidebarView extends AbstractList<EmiFavorite> {

		private boolean separated() {
			if (!TreeTabsConfig.enabled) {
				return false;
			}
			// A dedicated crafting panel already shows this list; never show it twice.
			if (!"NONE".equalsIgnoreCase(TreeTabsConfig.craftingPanelSide)) {
				return true;
			}
			return TreeTabsConfig.separateCraftingFavorites && BoM.craftingMode;
		}

		@Override
		public EmiFavorite get(int index) {
			if (dedicatedPanel()) {
				return EmiFavorites.favorites.get(index);
			}
			return getInner(index);
		}

		/**
		 * Whether something other than the favourites panel is already showing the crafting list.
		 *
		 * <p>Checked against EMI's live sidebar config rather than assumed, so placing a Crafting
		 * page or subpanel anywhere automatically stops favourites from duplicating it — while
		 * someone who has not placed one still sees it there rather than nowhere at all.
		 */
		private boolean dedicatedPanel() {
			if (!TreeTabsConfig.enabled) {
				return false;
			}
			if (!TreeTabsConfig.craftingInFavorites) {
				return true;
			}
			if (!"NONE".equalsIgnoreCase(TreeTabsConfig.craftingPanelSide)) {
				return true;
			}
			return CraftingSidebarType.isPlacedInSidebar();
		}

		private EmiFavorite getInner(int index) {
			if (separated()) {
				return EmiFavorites.syntheticFavorites.get(index);
			}
			List<EmiFavorite> favorites = EmiFavorites.favorites;
			if (index >= favorites.size()) {
				return EmiFavorites.syntheticFavorites.get(index - favorites.size());
			}
			return favorites.get(index);
		}

		@Override
		public int size() {
			if (dedicatedPanel()) {
				return EmiFavorites.favorites.size();
			}
			if (separated()) {
				return EmiFavorites.syntheticFavorites.size();
			}
			return EmiFavorites.favorites.size() + EmiFavorites.syntheticFavorites.size();
		}
	}
}
