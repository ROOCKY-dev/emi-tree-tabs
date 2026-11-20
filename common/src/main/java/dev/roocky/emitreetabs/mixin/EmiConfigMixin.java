package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.emi.emi.config.EmiConfig;
import dev.roocky.emitreetabs.sidebar.CraftingSidebarType;

/**
 * Makes sure the Crafting page type exists before EMI reads {@code emi.css}.
 *
 * <p>EMI parses sidebar pages with {@code SidebarType.fromName}, which turns a name it does not know
 * into {@code NONE}; {@code SidebarPages.unique()} then deletes {@code NONE} pages, and the startup
 * load writes the file straight back. So if EMI loads its config before our constant is added, the
 * player's Crafting page is erased from disk. On NeoForge EMI loads at client setup, after our mod
 * constructor, but on Fabric both run as client initializers in whatever order the loader picks.
 * Installing here takes the order out of it. {@code install} is idempotent.
 */
@Mixin(value = EmiConfig.class, remap = false)
public class EmiConfigMixin {

	@Inject(method = "loadConfig()V", at = @At("HEAD"), require = 0)
	private static void emitreetabs$installPageType(CallbackInfo ci) {
		CraftingSidebarType.install();
	}
}
