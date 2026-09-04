package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.MaterialTree;

/**
 * Notices when a recipe choice is made on one tree, so the same choice can be offered to the others.
 *
 * <p>{@code remap = false} for the whole class: none of MaterialTree's members come from Minecraft.
 *
 * <p>{@code addResolution} is only ever called when the player picks a recipe for an ingredient —
 * EMI's own {@code recalculate} reads resolutions but never writes them — so this fires on a
 * deliberate action and nothing else.
 *
 * <p>Nothing is changed here. EMI's slot accepts only an unmodified click ("use for this recipe
 * tree") and {@code Ctrl}+click ("toggle default for all trees"), and it swallows every other
 * modifier, so there is no gesture on EMI's own control left to hook. Instead this records what
 * happened and lets {@link TreeTabs} offer the player a follow-up on a key this mod owns.
 */
@Mixin(value = MaterialTree.class, remap = false)
public class MaterialTreeMixin {

	@Inject(method = "addResolution(Ldev/emi/emi/api/stack/EmiIngredient;Ldev/emi/emi/api/recipe/EmiRecipe;)V",
			at = @At("TAIL"))
	private void emitreetabs$offerSync(EmiIngredient ingredient, EmiRecipe recipe, CallbackInfo ci) {
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.offerResolutionSync) {
			return;
		}
		TreeTabs.noteResolution((MaterialTree) (Object) this, ingredient, recipe);
	}
}
