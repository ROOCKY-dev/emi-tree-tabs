package dev.roocky.emitreetabs.tab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;

/**
 * Where the open trees disagree about how to make something.
 *
 * <p>The problem, unchanged: progression unlocks a cheaper way to make an intermediate part, you
 * change it on the tree in front of you, and the other eight machines quietly carry on using the
 * expensive one. There is no way to find them short of opening each tree and hunting.
 *
 * <h2>Why this replaced the old approach</h2>
 *
 * The first version watched {@code MaterialTree.addResolution} through a mixin and, at the moment
 * you picked a recipe, threw up a toast offering a keybind. Two things were wrong with that, and
 * they are the two things that were reported:
 *
 * <ul>
 *   <li><b>It acted on its own.</b> Something appeared and started talking about changing other
 *       trees whenever you picked a recipe, including every time you did not mean anything by
 *       it.</li>
 *   <li><b>There was no way to use it.</b> The offer lived for as long as a toast, the gesture was
 *       a keybind nobody was told about, and if you missed the moment the feature simply was not
 *       there any more.</li>
 * </ul>
 *
 * <p>So this asks rather than tells. Disagreement is not an event to be caught — it is a property
 * of the open trees, true until someone fixes it, and computable whenever anyone wants to look.
 * Nothing here runs unless the player opens the screen, and what it finds is the same whether they
 * look one second or one hour after the change that caused it.
 */
public final class RecipeChoices {

	/**
	 * One way of making something, and every open tree that makes it that way.
	 *
	 * @param recipe the recipe, or <b>null</b> for "gathered, not crafted" — which is a real
	 *               answer and the one most disagreements are with. A tree that mines redstone and
	 *               a tree that breaks a redstone block disagree, and saying so is the entire
	 *               point; skipping the null side left every such case invisible.
	 */
	public record Option(EmiRecipe recipe, List<TreeTab> trees) {
	}

	/**
	 * One ingredient the open trees make in more than one way.
	 *
	 * @param options most-used first, so the majority reading is the first thing on the row
	 */
	public record Conflict(EmiIngredient ingredient, List<Option> options) {

		/** How many trees would change if every one of them took {@code option}. */
		public int wouldChange(Option option) {
			int n = 0;
			for (Option other : options) {
				if (other != option) {
					n += other.trees().size();
				}
			}
			return n;
		}
	}

	private RecipeChoices() {
	}

	/**
	 * Every ingredient the open trees resolve differently, most contested first.
	 *
	 * <p>Pure: it reads the trees and changes nothing. Called when the screen opens and after each
	 * change, never on a timer and never from an injection.
	 */
	public static List<Conflict> scan() {
		List<TreeTab> tabs = new ArrayList<>();
		for (TreeTab tab : TreeTabs.tabs()) {
			if (tab.tree != null && tab.tree.goal != null) {
				tabs.add(tab);
			}
		}
		if (tabs.size() < 2) {
			// One tree cannot disagree with anything.
			return List.of();
		}

		// Only ingredients somebody has actually chosen a recipe for are worth asking about. An
		// ingredient every tree leaves at its default cannot be in disagreement.
		Set<EmiIngredient> candidates = new LinkedHashSet<>();
		for (TreeTab tab : tabs) {
			candidates.addAll(tab.tree.resolutions.keySet());
		}

		List<Conflict> out = new ArrayList<>();
		for (EmiIngredient ingredient : candidates) {
			Map<EmiRecipe, List<TreeTab>> byRecipe = new LinkedHashMap<>();
			for (TreeTab tab : tabs) {
				if (!uses(tab.tree, ingredient)) {
					continue;
				}
				// null is kept, not skipped: it means "gather it raw", and a tree that gathers
				// disagreeing with a tree that crafts is the commonest disagreement there is.
				// LinkedHashMap takes a null key, which is why this is not a HashMap of Optional.
				EmiRecipe recipe = tab.tree.getRecipe(ingredient);
				byRecipe.computeIfAbsent(recipe, k -> new ArrayList<>()).add(tab);
			}
			if (byRecipe.size() < 2) {
				continue;
			}
			List<Option> options = new ArrayList<>();
			for (Map.Entry<EmiRecipe, List<TreeTab>> e : byRecipe.entrySet()) {
				options.add(new Option(e.getKey(), List.copyOf(e.getValue())));
			}
			options.sort((a, b) -> Integer.compare(b.trees().size(), a.trees().size()));
			out.add(new Conflict(ingredient, List.copyOf(options)));
		}
		out.sort((a, b) -> Integer.compare(b.options().size(), a.options().size()));
		return out;
	}

	/**
	 * Makes every open tree that uses {@code ingredient} use {@code recipe} for it.
	 *
	 * <p>Only the one sub-craft, never the whole tree, and only trees that actually contain the
	 * ingredient — writing a resolution into a tree that never reads it would be dead weight in
	 * the save file.
	 *
	 * @return how many trees changed
	 */
	public static int applyToAll(EmiIngredient ingredient, EmiRecipe recipe) {
		if (ingredient == null) {
			return 0;
		}
		int changed = 0;
		for (TreeTab tab : TreeTabs.tabs()) {
			MaterialTree tree = tab.tree;
			if (tree == null || tree.goal == null || !uses(tree, ingredient)) {
				continue;
			}
			EmiRecipe current = tree.getRecipe(ingredient);
			if (current == recipe || (current != null && current.equals(recipe))) {
				continue;
			}
			// A null recipe is EMI's own way of saying "no resolution, gather it", so applying
			// "gathered" is the same call with a null.
			tree.addResolution(ingredient, recipe);
			tree.recalculate();
			tab.labelVersion++;
			changed++;
		}
		if (changed > 0) {
			TreeTabs.markDirty();
		}
		return changed;
	}

	/** Whether this tree has a node for the given ingredient anywhere under its goal. */
	private static boolean uses(MaterialTree tree, EmiIngredient ingredient) {
		if (tree == null || tree.goal == null || ingredient == null) {
			return false;
		}
		// Iterative, with a visited set: a malformed tree must not be able to hang the client.
		Deque<MaterialNode> queue = new ArrayDeque<>();
		Set<MaterialNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		queue.add(tree.goal);
		seen.add(tree.goal);
		while (!queue.isEmpty()) {
			MaterialNode node = queue.poll();
			if (ingredient.equals(node.ingredient)) {
				return true;
			}
			if (node.children != null) {
				for (MaterialNode child : node.children) {
					if (child != null && seen.add(child)) {
						queue.add(child);
					}
				}
			}
		}
		return false;
	}
}
