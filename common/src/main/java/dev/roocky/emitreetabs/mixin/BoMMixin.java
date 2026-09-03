package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.bom.BoM;

/**
 * EMI keeps one tree in a static field. These two hooks are the whole integration: notice when a
 * tree is created, and rebuild our tabs whenever EMI reloads and invalidates every recipe object.
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
}
