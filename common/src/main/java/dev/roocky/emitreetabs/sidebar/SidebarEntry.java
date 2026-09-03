package dev.roocky.emitreetabs.sidebar;

import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * Base for the decorative entries we splice into the crafting sidebar.
 *
 * <p>EMI's sidebar renders a flat {@code List<EmiIngredient>} into a slot grid, so the only way to
 * add structure is to hand it entries that happen to draw something other than an item. These all
 * report themselves as empty ingredients, which keeps EMI's favouriting and recipe lookup from
 * treating them as real stacks.
 */
public abstract class SidebarEntry implements EmiIngredient {

	@Override
	public List<EmiStack> getEmiStacks() {
		return List.of();
	}

	@Override
	public boolean isEmpty() {
		return true;
	}

	@Override
	public EmiIngredient copy() {
		return this;
	}

	@Override
	public long getAmount() {
		return 0;
	}

	@Override
	public EmiIngredient setAmount(long amount) {
		return this;
	}

	@Override
	public float getChance() {
		return 1;
	}

	@Override
	public EmiIngredient setChance(float chance) {
		return this;
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		return List.of();
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
	}

	/** Occupies a slot and draws nothing. Used to push the next group onto a fresh row. */
	public static final class Blank extends SidebarEntry {
		public static final Blank INSTANCE = new Blank();

		private Blank() {
		}
	}
}
