package dev.roocky.emitreetabs.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.sidebar.CraftingSidebarType;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiSidebars;

/**
 * Answers for our sidebar type before EMI's switch sees it.
 *
 * <p>This is not optional. {@code getStacks} switches on the enum through a synthetic
 * {@code $SwitchMap} array that is sized when its class initialises, so an ordinal added afterwards
 * would index past the end and throw {@code ArrayIndexOutOfBoundsException}. Returning early keeps
 * our constant away from that switch entirely.
 */
@Mixin(value = EmiSidebars.class, remap = false)
public class EmiSidebarsMixin {

	@Inject(method = "getStacks", at = @At("HEAD"), cancellable = true, require = 0)
	private static void emitreetabs$ourType(SidebarType type,
			CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
		if (CraftingSidebarType.isOurs(type)) {
			// The laid-out, grouped form is produced per panel in ScreenSpaceMixin; this flat list
			// is what any other caller gets.
			cir.setReturnValue(EmiFavorites.syntheticFavorites);
		}
	}
}
