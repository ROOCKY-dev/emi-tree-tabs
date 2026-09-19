package dev.roocky.emitreetabs.tab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialNode;

/**
 * Which sub-craft inside a tree wants a shared material, and how much.
 *
 * <p>{@link CraftingFavorites} already answers "which tree", which was the first half of the
 * complaint: the list says you need 184 copper and not how much of it is for plates. The second
 * half is that a single tree wants copper in several places too — a mixer needs a fan needs plates
 * — so knowing the tree is not yet knowing what spending it now would starve.
 *
 * <h2>Why this is captured rather than calculated</h2>
 *
 * The obvious implementation is to walk the tree and add up leaves. It gives wrong numbers. EMI's
 * {@code TreeCost.calculateCost} subtracts remainders as it descends — by-products of one branch
 * pay for another, and what a leaf finally costs depends on the order the walk reached it and on
 * what was left in the pool by then. A second walk cannot reproduce that without reimplementing the
 * solver, and a tooltip whose whole purpose is arithmetic must not show arithmetic of its own.
 *
 * <p>So the numbers are taken from EMI's walk while it happens: the mixin keeps the node stack, and
 * every cost EMI records is attributed to the node that was consuming it. The amount is exactly the
 * one EMI put in its own totals.
 *
 * <p>The consumer is named by its <em>ingredient</em> rather than its recipe, because "the fan" is
 * what the question is about, and a recipe has no short name to show.
 */
public final class SubCraftCosts {

	/** One sub-craft's share of a material, inside one tree. */
	public record Share(EmiIngredient consumer, long needed) {
	}

	/** material -> tree -> consuming sub-craft -> amount. */
	private static Map<EmiIngredient, Map<TreeTab, Map<EmiIngredient, long[]>>> RESULT =
			new LinkedHashMap<>();

	private static Map<EmiIngredient, Map<TreeTab, Map<EmiIngredient, long[]>>> building =
			new LinkedHashMap<>();
	/**
	 * One walk's findings, material -> consuming sub-craft -> amount.
	 *
	 * <p>Separate from {@link #building} because EMI walks each tree <em>three times</em> per
	 * update — {@code calculateCost()}, then {@code calculateProgress(empty)} to get the untouched
	 * totals, then {@code calculateProgress(inventory)} for the real ones. Accumulating across all
	 * three tripled every figure. Only the last walk produces the numbers the sidebar shows, so
	 * each walk discards the one before it and whatever survives is the answer.
	 */
	private static Map<EmiIngredient, Map<EmiIngredient, long[]>> walk = new LinkedHashMap<>();
	private static final Deque<MaterialNode> STACK = new ArrayDeque<>();
	private static TreeTab capturing;

	private SubCraftCosts() {
	}

	/** True while a tree's costing is being watched. Checked first, so the mixin costs nothing. */
	public static boolean capturing() {
		return capturing != null;
	}

	/** Starts a fresh aggregation pass. */
	public static void beginPass() {
		building = new LinkedHashMap<>();
	}

	/** Watches one tree's costing. */
	public static void begin(TreeTab tab) {
		capturing = tab;
		STACK.clear();
		walk.clear();
	}

	/** A fresh walk of the same tree. Whatever the previous one found is superseded. */
	public static void beginWalk() {
		if (capturing != null) {
			STACK.clear();
			walk.clear();
		}
	}

	/** Keeps the last walk's findings and stops watching. */
	public static void end() {
		if (capturing != null) {
			for (Map.Entry<EmiIngredient, Map<EmiIngredient, long[]>> material : walk.entrySet()) {
				Map<EmiIngredient, long[]> into = building
						.computeIfAbsent(material.getKey(), k -> new LinkedHashMap<>())
						.computeIfAbsent(capturing, k -> new LinkedHashMap<>());
				for (Map.Entry<EmiIngredient, long[]> consumer : material.getValue().entrySet()) {
					into.computeIfAbsent(consumer.getKey(), k -> new long[1])[0]
							+= consumer.getValue()[0];
				}
			}
		}
		capturing = null;
		STACK.clear();
		walk.clear();
	}

	/**
	 * Publishes the pass.
	 *
	 * <p>Published even when it found nothing, so a run that produced no split clears the old one
	 * rather than leaving a stale answer to be read against the next tree.
	 */
	public static void endPass() {
		end();
		RESULT = building;
		building = new LinkedHashMap<>();
	}

	public static void push(MaterialNode node) {
		if (capturing != null && node != null) {
			STACK.push(node);
		}
	}

	public static void pop() {
		if (capturing != null && !STACK.isEmpty()) {
			STACK.pop();
		}
	}

	/**
	 * Records a cost EMI just added.
	 *
	 * <p>The stack's top is the leaf being costed, so the one below it is the sub-craft consuming
	 * it. A leaf with nothing below is the goal itself, which is its own consumer.
	 */
	public static void add(EmiIngredient stack, long amount) {
		TreeTab tab = capturing;
		if (tab == null || stack == null || amount <= 0) {
			return;
		}
		EmiIngredient consumer = consumer();
		if (consumer == null) {
			return;
		}
		walk.computeIfAbsent(stack, k -> new LinkedHashMap<>())
				.computeIfAbsent(consumer, k -> new long[1])[0] += amount;
	}

	private static EmiIngredient consumer() {
		int depth = 0;
		MaterialNode leaf = null;
		for (MaterialNode node : STACK) {
			if (depth == 0) {
				leaf = node;
			} else {
				return node.ingredient;
			}
			depth++;
		}
		return leaf == null ? null : leaf.ingredient;
	}

	/**
	 * How one tree's demand for a material splits across its sub-crafts, most demanding first.
	 *
	 * <p>Unfiltered: a single consumer is returned too. Whether one line is worth showing is the
	 * caller's decision, because it depends on what else is being drawn — inside a per-tree
	 * breakdown "60 into Oak Planks" is the answer, while on its own it is only restating the
	 * total. Filtering here drew a heading with nothing underneath it.
	 */
	public static List<Share> shares(EmiIngredient stack, TreeTab tab) {
		Map<TreeTab, Map<EmiIngredient, long[]>> byTab = RESULT.get(stack);
		if (byTab == null) {
			return List.of();
		}
		return sorted(byTab.get(tab));
	}

	/** The same merged across every tree, for when the per-tree split is not what is being shown. */
	public static List<Share> shares(EmiIngredient stack) {
		Map<TreeTab, Map<EmiIngredient, long[]>> byTab = RESULT.get(stack);
		if (byTab == null || byTab.isEmpty()) {
			return List.of();
		}
		Map<EmiIngredient, long[]> merged = new LinkedHashMap<>();
		for (Map<EmiIngredient, long[]> byConsumer : byTab.values()) {
			for (Map.Entry<EmiIngredient, long[]> e : byConsumer.entrySet()) {
				merged.computeIfAbsent(e.getKey(), k -> new long[1])[0] += e.getValue()[0];
			}
		}
		return sorted(merged);
	}

	private static List<Share> sorted(Map<EmiIngredient, long[]> byConsumer) {
		if (byConsumer == null || byConsumer.isEmpty()) {
			return List.of();
		}
		List<Share> out = new ArrayList<>();
		for (Map.Entry<EmiIngredient, long[]> e : byConsumer.entrySet()) {
			out.add(new Share(e.getKey(), e.getValue()[0]));
		}
		out.sort((a, b) -> Long.compare(b.needed(), a.needed()));
		return out;
	}
}
