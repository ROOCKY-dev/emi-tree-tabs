package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;

/**
 * EMI keeps one tree in a static field. These hooks are the whole integration: notice when a tree
 * is created, rebuild our tabs whenever EMI reloads and invalidates every recipe object, and
 * rebuild the inactive trees when a default recipe changes.
 *
 * <p>{@code remap = false} for the whole class because none of BoM's members come from Minecraft.
 */
@Mixin(value = BoM.class, remap = false)
public class BoMMixin {

	@Inject(method = "setGoal", at = @At("TAIL"))
	private static void emitreetabs$onSetGoal(EmiRecipe recipe, CallbackInfo ci) {
		TreeTabs.onGoalSet();
	}

	@Inject(method = "reload", at = @At("TAIL"))
	private static void emitreetabs$onReload(CallbackInfo ci) {
		TreeTabs.onEmiReload();
	}

	/**
	 * Every change to EMI's default recipes (the heart button, the slot buttons, removing one) ends
	 * here, and EMI only rebuilds {@code BoM.tree}. The other tabs need the same treatment.
	 */
	@Inject(method = "recalculate", at = @At("TAIL"))
	private static void emitreetabs$onDefaultsChanged(CallbackInfo ci) {
		TreeTabs.onDefaultsChanged();
	}
}
