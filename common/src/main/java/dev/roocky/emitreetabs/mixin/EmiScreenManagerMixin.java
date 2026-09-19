package dev.roocky.emitreetabs.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.sidebar.GroupHeader;
import dev.roocky.emitreetabs.sidebar.SidebarEntry;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Makes a section title in the crafting sidebar fold its section away when clicked.
 *
 * <p>The title is drawn across the whole row but the header itself only occupies the row's first
 * slot, so the click is accepted from the padding slots the title is painted over as well —
 * otherwise clicking the words does nothing and folding looks broken.
 */
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
			GroupHeader header = hovered == null ? null : emitreetabs$headerAt(hovered.getStack());
			if (header != null) {
				header.toggle();
				Minecraft.getInstance().getSoundManager()
						.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
				cir.setReturnValue(true);
			}
		} catch (Throwable ignored) {
			// Folding is a convenience; never let it swallow or break a normal sidebar click.
		}
	}

	/** The header a slot belongs to: the header's own slot, or any slot its title is drawn over. */
	private static GroupHeader emitreetabs$headerAt(Object stack) {
		if (stack instanceof GroupHeader header) {
			return header;
		}
		if (stack instanceof SidebarEntry.HeaderPad pad) {
			return pad.header;
		}
		return null;
	}
}
