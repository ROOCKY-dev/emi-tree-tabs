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

	/** @return the rebuilt tab, or null if the goal recipe no longer exists in this world. */
	public static TreeTab load(JsonObject obj) {
		if (obj == null || !obj.has("goal")) {
			return null;
		}
		ResourceLocation goalId = tryId(obj.get("goal").getAsString());
		if (goalId == null) {
			return null;
		}
		EmiRecipe goal = EmiApi.getRecipeManager().getRecipe(goalId);
		if (goal == null) {
			EmiTreeTabs.LOGGER.info("[emitreetabs] dropping tab, recipe {} is gone", goalId);
			return null;
		}

		MaterialTree tree = new MaterialTree(goal);
		if (obj.has("resolutions")) {
			for (JsonElement element : obj.getAsJsonArray("resolutions")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject resolution = element.getAsJsonObject();
				EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(resolution.get("ingredient"));
				if (ingredient == null || ingredient.isEmpty()) {
					continue;
				}
				if (resolution.has("recipe")) {
					ResourceLocation id = tryId(resolution.get("recipe").getAsString());
					EmiRecipe recipe = id == null ? null : EmiApi.getRecipeManager().getRecipe(id);
					if (recipe == null) {
						// Recipe vanished; leave this branch on EMI's default rather than blanking it.
						continue;
					}
					tree.resolutions.put(ingredient, recipe);
				} else {
					tree.resolutions.put(ingredient, null);
				}
			}
		}
		if (obj.has("batches")) {
			tree.batches = Math.max(1, obj.get("batches").getAsLong());
		}
		tree.recalculate();

		if (obj.has("folds")) {
			for (JsonElement element : obj.getAsJsonArray("folds")) {
				if (!element.isJsonObject()) {
					continue;
				}
				JsonObject fold = element.getAsJsonObject();
				MaterialNode node = walk(tree.goal, fold.getAsJsonArray("path"));
				if (node != null) {
					node.state = parseFold(fold.get("state").getAsString());
				}
			}
		}

		TreeTab tab = new TreeTab(tree);
		if (obj.has("name")) {
			tab.customName = obj.get("name").getAsString();
		}
		if (obj.has("mode")) {
			tab.craftingMode = "craft".equals(obj.get("mode").getAsString());
		}
		if (obj.has("offX") && obj.has("offY")) {
			tab.offX = obj.get("offX").getAsDouble();
			tab.offY = obj.get("offY").getAsDouble();
			tab.zoom = obj.has("zoom") ? obj.get("zoom").getAsInt() : 0;
			tab.viewportSet = true;
		}
		tab.snapshot = obj;
		return tab;
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
}
