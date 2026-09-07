package dev.roocky.emitreetabs.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.CraftingFavorites;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * Says what a shared material is actually <em>for</em>.
 *
 * <p>The crafting list can tell you that you need 184 copper. It cannot tell you how much of that is
 * for plates and how much for pipes, so deciding whether to spend copper now or save it means doing
 * the arithmetic by hand — which is the whole complaint this answers. The split is only knowable
 * while the totals are being summed, so {@link CraftingFavorites} keeps it and this shows it.
 *
 * <p>Answered on the tooltip rather than a separate screen because that is where the question gets
 * asked: mid-decision, with the cursor already on the item.
 *
 * <p>{@code require = 0}: losing the breakdown if EMI renames this is a small loss, and much smaller
 * than refusing to load.
 */
@Mixin(value = EmiFavorite.Synthetic.class, remap = false)
public class SyntheticTooltipMixin {

	/** Beyond this the tooltip becomes the problem rather than the answer. */
	private static final int MAX_LINES = 6;

	@Inject(method = "getTooltip", at = @At("TAIL"), cancellable = true, require = 0)
	private void emitreetabs$attribution(CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.aggregateCraftingFavorites) {
			return;
		}
		EmiFavorite self = (EmiFavorite) (Object) this;
		if (self.getRecipe() != null) {
			// A recipe entry is already attributed by being a recipe; only raw materials are shared.
			return;
		}
		List<CraftingFavorites.Attribution> split = CraftingFavorites.attribution(self.getStack());
		if (split.size() < 2) {
			// One tree wanting all of it is not a split, and saying so would be noise.
			return;
		}
		List<ClientTooltipComponent> lines = cir.getReturnValue();
		if (lines == null) {
			return;
		}
		lines.add(ClientTooltipComponent.create(
				Component.translatable("emi.tree_tabs.attribution.title")
						.withStyle(ChatFormatting.GRAY).getVisualOrderText()));

		int shown = 0;
		for (CraftingFavorites.Attribution a : split) {
			if (shown >= MAX_LINES) {
				lines.add(ClientTooltipComponent.create(
						Component.translatable("emi.tree_tabs.attribution.more", split.size() - shown)
								.withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText()));
				break;
			}
			lines.add(ClientTooltipComponent.create(
					Component.translatable("emi.tree_tabs.attribution.line",
									a.needed(), a.tab().displayName())
							.withStyle(ChatFormatting.AQUA).getVisualOrderText()));
			shown++;
		}
	}
}
