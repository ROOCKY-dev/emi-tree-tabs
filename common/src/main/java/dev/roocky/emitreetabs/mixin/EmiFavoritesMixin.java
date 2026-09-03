package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.roocky.emitreetabs.tab.CraftingFavorites;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.runtime.EmiFavorites;

/**
 * Makes crafting mode consider every tracked tree. When we handle it we cancel EMI's own pass,
 * having already driven it once per tree from {@link CraftingFavorites#aggregate}.
 */
@Mixin(value = EmiFavorites.class, remap = false)
public class EmiFavoritesMixin {

	@Inject(method = "updateSynthetic", at = @At("HEAD"), cancellable = true)
	private static void emitreetabs$aggregateAcrossTabs(EmiPlayerInventory inventory, CallbackInfo ci) {
		if (CraftingFavorites.aggregate(inventory)) {
			ci.cancel();
		}
	}
}
