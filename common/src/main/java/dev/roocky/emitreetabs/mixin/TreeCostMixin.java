package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.roocky.emitreetabs.tab.SubCraftCosts;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.ChanceState;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.bom.TreeCost;

/**
 * Watches EMI cost a tree, so a material's demand can be attributed to the sub-craft wanting it.
 *
 * <p>Reading rather than changing: nothing here writes to EMI's state, and with
 * {@link SubCraftCosts#capturing()} false — which is every call EMI makes for its own reasons — the
 * handlers return on their first line.
 *
 * <p>Why the numbers have to come from inside the walk rather than from a second pass of our own is
 * in {@link SubCraftCosts}: {@code calculateCost} spends remainders as it descends, so what a leaf
 * costs depends on what the walk had left by the time it arrived.
 *
 * <p>{@code require = 0} on both, and {@code remap = false} for the class: none of TreeCost's
 * members come from Minecraft, and losing the sub-craft split if EMI renames one of these is a far
 * smaller loss than refusing to load. The feature simply does not appear.
 */
@Mixin(value = TreeCost.class, remap = false)
public class TreeCostMixin {

	@Inject(method = "calculateCost(Ldev/emi/emi/bom/MaterialNode;JLdev/emi/emi/bom/ChanceState;Z)V",
			at = @At("HEAD"), require = 0)
	private void emitreetabs$enter(MaterialNode node, long amount, ChanceState chance,
			boolean trackProgress, CallbackInfo ci) {
		SubCraftCosts.push(node);
	}

	/**
	 * Pops on every exit, including the two early ones — a node fully covered by remainders, and
	 * the resolution-recipe shortcut — because an unbalanced stack would misattribute every cost
	 * after it.
	 */
	@Inject(method = "calculateCost(Ldev/emi/emi/bom/MaterialNode;JLdev/emi/emi/bom/ChanceState;Z)V",
			at = @At("RETURN"), require = 0)
	private void emitreetabs$exit(MaterialNode node, long amount, ChanceState chance,
			boolean trackProgress, CallbackInfo ci) {
		SubCraftCosts.pop();
	}

	@Inject(method = "addCost(Ldev/emi/emi/api/stack/EmiIngredient;JJLdev/emi/emi/bom/ChanceState;)V",
			at = @At("HEAD"), require = 0)
	private void emitreetabs$cost(EmiIngredient stack, long amount, long minBatch,
			ChanceState chance, CallbackInfo ci) {
		SubCraftCosts.add(stack, amount);
	}
}
