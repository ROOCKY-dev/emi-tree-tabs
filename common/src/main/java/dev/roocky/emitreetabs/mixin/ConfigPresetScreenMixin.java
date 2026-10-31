package dev.roocky.emitreetabs.mixin;

import java.lang.reflect.Field;

import dev.emi.emi.screen.ConfigPresetScreen;
import dev.roocky.emitreetabs.sidebar.TreeTabsPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Adds our annotated preset to the fields EMI already renders in its preset screen.
 * The init override must be remapped for Fabric; only the Java reflection call stays unmapped.
 */
@Mixin(ConfigPresetScreen.class)
public class ConfigPresetScreenMixin {
	@Redirect(method = "init()V", at = @At(value = "INVOKE",
			target = "Ljava/lang/Class;getFields()[Ljava/lang/reflect/Field;", remap = false))
	private Field[] emitreetabs$includePreset(Class<?> owner) {
		return TreeTabsPreset.withPreset(owner.getFields());
	}
}
