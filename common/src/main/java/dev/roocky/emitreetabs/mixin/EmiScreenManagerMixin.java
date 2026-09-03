package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.sidebar.GroupHeader;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Makes a section title in the crafting sidebar fold its section away when clicked. */
@Mixin(value = EmiScreenManager.class, remap = false)
public class EmiScreenManagerMixin {

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
	private static void emitreetabs$foldGroup(double mouseX, double mouseY, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.collapsibleGroups || button != 0) {
			return;
		}
		try {
			EmiStackInteraction hovered = EmiScreenManager.getHoveredStack((int) mouseX, (int) mouseY, false);
			if (hovered != null && hovered.getStack() instanceof GroupHeader header) {
				header.toggle();
				Minecraft.getInstance().getSoundManager()
						.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				cir.setReturnValue(true);
			}
		} catch (Throwable ignored) {
			// Folding is a convenience; never let it swallow or break a normal sidebar click.
		}
	}
}
