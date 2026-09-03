package dev.roocky.emitreetabs.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.sidebar.CraftingGroups;
import dev.roocky.emitreetabs.sidebar.CraftingSidebarType;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarSide;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;

/**
 * Serves the grouped crafting list to whichever sidebar space is showing it.
 *
 * <p>Two ways in. Normally the space's page type is our own {@code Crafting} type, added to EMI's
 * enum at runtime, which means it can be picked as a page or a subpanel in EMI's own settings and
 * EMI draws its native divider around it. Failing that (if the enum could not be extended) the
 * player can still nominate a side and have its Favourites panel taken over.
 */
@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class ScreenSpaceMixin {

	@Shadow(remap = false)
	@Final
	public int[] widths;

	@Shadow(remap = false)
	public abstract SidebarType getType();

	@Shadow(remap = false)
	public abstract int getX(int row, int column);

	@Inject(method = "getStacks", at = @At("HEAD"), cancellable = true, require = 0)
	private void emitreetabs$craftingPage(CallbackInfoReturnable<List<? extends EmiIngredient>> cir) {
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.groupCraftingList || CraftingGroups.isEmpty()) {
			return;
		}
		if (!emitreetabs$showsCrafting()) {
			return;
		}
		cir.setReturnValue(CraftingGroups.layout(widths, emitreetabs$slotPitch()));
	}

	private boolean emitreetabs$showsCrafting() {
		SidebarType type = getType();
		if (CraftingSidebarType.isOurs(type)) {
			return true;
		}
		// The nominated-panel fallback exists only for when the page type could not be registered.
		// With a real Crafting page available, honouring it as well would take over the Favourites
		// page on the very sidebar the player put both pages on.
		if (CraftingSidebarType.TYPE != null) {
			return false;
		}
		return type == SidebarType.FAVORITES && emitreetabs$isNominatedPanel();
	}

	/** Measured rather than assumed, so the divider spans the row whatever EMI's spacing is. */
	private int emitreetabs$slotPitch() {
		int pitch = Math.abs(getX(0, 1) - getX(0, 0));
		return pitch > 0 ? pitch : 18;
	}

	private boolean emitreetabs$isNominatedPanel() {
		SidebarSide side;
		try {
			side = SidebarSide.valueOf(TreeTabsConfig.craftingPanelSide.toUpperCase());
		} catch (IllegalArgumentException e) {
			return false;
		}
		if (side == SidebarSide.NONE) {
			return false;
		}
		EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(side);
		if (panel == null) {
			return false;
		}
		Object self = this;
		return panel.space == self || (panel.getSpaces() != null && panel.getSpaces().contains(self));
	}
}
