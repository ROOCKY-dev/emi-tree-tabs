package dev.roocky.emitreetabs.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.roocky.emitreetabs.EmiTreeTabs;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.bom.FoldState;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.MaterialTree;
import net.minecraft.resources.ResourceLocation;

/**
 * Turns a {@link TreeTab} into json and back.
 *
 * <p>A tree is not stored node by node. Everything EMI needs to rebuild an identical tree is the
 * goal recipe, the resolutions the player picked for individual ingredients, the batch count, and
 * which branches are folded shut; the node graph itself is derived. That also means a tab survives
 * a resource reload, where every {@code EmiRecipe} object is thrown away and recreated.
 */
public final class TabCodec {
	private TabCodec() {
	}

	public static JsonObject save(TreeTab tab) {
		if (tab.tree == null || tab.tree.goal == null || tab.tree.goal.recipe == null) {
			return tab.snapshot;
		}
		ResourceLocation goalId = tab.tree.goal.recipe.getId();
		if (goalId == null) {
			// A synthetic recipe with no id cannot be looked back up, so this tab is not persistable.
			return null;
		}
		JsonObject obj = new JsonObject();
		obj.addProperty("goal", goalId.toString());
		obj.addProperty("batches", tab.tree.batches);
		if (tab.customName != null && !tab.customName.isBlank()) {
			obj.addProperty("name", tab.customName);
		}
		if (tab.groupId >= 0) {
			obj.addProperty("group", tab.groupId);
		}
		if (tab.craftingMode) {
			// Only written for tabs being worked on, so the common case stays out of the file.
			obj.addProperty("mode", "craft");
		}
		if (tab.viewportSet) {
			obj.addProperty("offX", tab.offX);
			obj.addProperty("offY", tab.offY);
			obj.addProperty("zoom", tab.zoom);
		}

		JsonArray resolutions = new JsonArray();
		for (Map.Entry<EmiIngredient, EmiRecipe> entry : tab.tree.resolutions.entrySet()) {
			JsonElement ingredient = EmiIngredientSerializer.getSerialized(entry.getKey());
			if (ingredient == null) {
				continue;
			}
			JsonObject resolution = new JsonObject();
			resolution.add("ingredient", ingredient);
			EmiRecipe recipe = entry.getValue();
			if (recipe != null) {
				if (recipe.getId() == null) {
					// Unpersistable choice. Dropping it just means this branch falls back to the
					// default recipe next session, which is better than dropping the whole tab.
					continue;
				}
				resolution.addProperty("recipe", recipe.getId().toString());
			}
			// A missing "recipe" is meaningful: the player explicitly cleared this branch.
			resolutions.add(resolution);
		}
		if (!resolutions.isEmpty()) {
			obj.add("resolutions", resolutions);
		}

		JsonArray folds = new JsonArray();
		collectFolds(tab.tree.goal, new ArrayList<>(), folds);
		if (!folds.isEmpty()) {
			obj.add("folds", folds);
		}

		tab.snapshot = obj;
		return obj;
	}

	/**
	 * The goal recipe id a snapshot names, for messages. Never null and never throws, so it is safe
	 * to call on exactly the snapshots that just failed to load.
	 */
	public static String goalId(JsonObject obj) {
		String goal = obj == null ? null : string(obj, "goal");
		return goal == null ? "(no recipe id)" : goal;
	}

	/**
	 * Rebuilds a tab from its snapshot.
	 *
	 * <p>Every field is checked for the shape it should have before it is read. The file is plain
	 * json a player can edit, and Gson throws on a value of the wrong type, so one hand-edited or
	 * damaged entry used to throw out of here and stop every tab after it from being restored. Now
	 * a missing or malformed goal rejects the tab, and anything else that is malformed (one
	 * resolution, one fold, the viewport) is skipped on its own and the rest of the tab still loads.
	 *
	 * <p>Numbers are held to the same bounds as interactive edits: batches to
	 * 1..{@link Formula#MAX}, as {@code TreeTabs.setBatches} does, and viewport offsets to finite
	 * values, since an infinite offset would draw the tree nowhere. Zoom needs no check here; EMI
	 * clamps it when drawing.
	 *
	 * @return the rebuilt tab, or null if the snapshot has no usable goal or its recipe is not
	 *     loaded in this world.
	 */
	public static TreeTab load(JsonObject obj) {
		if (obj == null) {
			return null;
		}
		String rawGoal = string(obj, "goal");
		ResourceLocation goalId = rawGoal == null ? null : tryId(rawGoal);
		if (goalId == null) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] a saved tab has no valid goal recipe: {}", obj.get("goal"));
			return null;
		}
		EmiRecipe goal = EmiApi.getRecipeManager().getRecipe(goalId);
		if (goal == null) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] cannot rebuild a tab, recipe {} is not loaded", goalId);
			return null;
		}

		MaterialTree tree = new MaterialTree(goal);
		JsonArray resolutions = array(obj, "resolutions");
		if (resolutions != null) {
			for (JsonElement element : resolutions) {
				loadResolution(tree, element);
			}
		}
		Long batches = whole(obj, "batches");
		if (batches != null) {
			tree.batches = Math.max(1, Math.min(batches, Formula.MAX));
		}
		tree.recalculate();

		JsonArray folds = array(obj, "folds");
		if (folds != null) {
			for (JsonElement element : folds) {
				loadFold(tree, element);
			}
		}

		TreeTab tab = new TreeTab(tree);
		tab.customName = string(obj, "name");
		tab.craftingMode = "craft".equals(string(obj, "mode"));
		Long group = whole(obj, "group");
		if (group != null && group >= 0 && group <= Integer.MAX_VALUE) {
			tab.groupId = group.intValue();
		}
		Double offX = finite(obj, "offX");
		Double offY = finite(obj, "offY");
		if (offX != null && offY != null) {
			tab.offX = offX;
			tab.offY = offY;
			Long zoom = whole(obj, "zoom");
			// No range check needed: BoMScreen.getScale clamps zoom to -6..4 itself.
			tab.zoom = zoom == null ? 0 : (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, zoom));
			tab.viewportSet = true;
		}
		tab.snapshot = obj;
		return tab;
	}

	/**
	 * One saved resolution. A malformed one is skipped and the branch falls back to EMI's default
	 * recipe, exactly as it does when the chosen recipe has vanished.
	 */
	private static void loadResolution(MaterialTree tree, JsonElement element) {
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject resolution = element.getAsJsonObject();
		EmiIngredient ingredient;
		try {
			ingredient = EmiIngredientSerializer.getDeserialized(resolution.get("ingredient"));
		} catch (RuntimeException e) {
			// EMI's deserialiser trusts its input; a damaged ingredient must not cost the whole tab.
			return;
		}
		if (ingredient == null || ingredient.isEmpty()) {
			return;
		}
		if (!resolution.has("recipe")) {
			// A missing "recipe" is meaningful: the player explicitly cleared this branch.
			tree.resolutions.put(ingredient, null);
			return;
		}
		String rawRecipe = string(resolution, "recipe");
		ResourceLocation id = rawRecipe == null ? null : tryId(rawRecipe);
		EmiRecipe recipe = id == null ? null : EmiApi.getRecipeManager().getRecipe(id);
		if (recipe == null) {
			// Recipe vanished; leave this branch on EMI's default rather than blanking it.
			return;
		}
		tree.resolutions.put(ingredient, recipe);
	}

	private static void loadFold(MaterialTree tree, JsonElement element) {
		if (!element.isJsonObject()) {
			return;
		}
		JsonObject fold = element.getAsJsonObject();
		MaterialNode node = walk(tree.goal, array(fold, "path"));
		String state = string(fold, "state");
		if (node != null && state != null) {
			node.state = parseFold(state);
		}
	}

	private static void collectFolds(MaterialNode node, List<Integer> path, JsonArray out) {
		if (node.state != FoldState.EXPANDED) {
			JsonObject fold = new JsonObject();
			JsonArray encoded = new JsonArray();
			for (int index : path) {
				encoded.add(index);
			}
			fold.add("path", encoded);
			fold.addProperty("state", node.state.name());
			out.add(fold);
		}
		if (node.children == null) {
			return;
		}
		for (int i = 0; i < node.children.size(); i++) {
			path.add(i);
			collectFolds(node.children.get(i), path, out);
			path.remove(path.size() - 1);
		}
	}

	private static MaterialNode walk(MaterialNode root, JsonArray path) {
		if (path == null) {
			return null;
		}
		MaterialNode node = root;
		for (JsonElement step : path) {
			if (!step.isJsonPrimitive() || !step.getAsJsonPrimitive().isNumber()) {
				return null;
			}
			int index = step.getAsInt();
			if (node.children == null || index < 0 || index >= node.children.size()) {
				return null;
			}
			node = node.children.get(index);
		}
		return node;
	}

	private static FoldState parseFold(String name) {
		try {
			return FoldState.valueOf(name);
		} catch (IllegalArgumentException e) {
			return FoldState.COLLAPSED;
		}
	}

	private static ResourceLocation tryId(String raw) {
		try {
			return new ResourceLocation(raw);
		} catch (Exception e) {
			return null;
		}
	}

	// Typed reads that answer null instead of throwing when a key is missing or has the wrong shape.

	private static String string(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
			return null;
		}
		return element.getAsString();
	}

	private static JsonArray array(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	private static Double finite(JsonObject obj, String key) {
		JsonElement element = obj.get(key);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		double value = element.getAsDouble();
		return Double.isFinite(value) ? value : null;
	}

	/** A whole number, or null. Fractions are truncated, as Gson's own getAsLong did. */
	private static Long whole(JsonObject obj, String key) {
		Double value = finite(obj, key);
		if (value == null) {
			return null;
		}
		// Clamped through double so an absurd number saturates instead of wrapping.
		return (long) Math.max(Long.MIN_VALUE, Math.min(Long.MAX_VALUE, value));
	}
}
