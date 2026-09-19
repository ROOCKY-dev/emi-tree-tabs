package dev.roocky.emitreetabs.sidebar;

import java.util.List;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.ListEmiIngredient;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A crafting-list entry that shows whether it is one particular item or a choice of several.
 *
 * <p>The list correctly keeps a tag and a plain item in separate sections — a furnace wants
 * {@code #stone_tool_materials} while a piston wants Cobblestone, and they are not the same
 * requirement. But a tag is drawn by cycling through its members, so for most of every cycle the
 * two are the same grey block, and the list looks as though it listed one thing twice. The only
 * way to tell them apart was to hover each one and read the tooltip.
 *
 * <p>So a choice is framed. The frame sits in the slot's own 1px padding rather than over the
 * icon, which means it never hides the thing it is describing, and it cannot be confused with the
 * amount EMI paints across the bottom-right corner.
 */
public class ChoiceEntry extends EmiFavorite.Synthetic {

	/** Light enough to read against item art, cool enough not to read as a count or a state. */
	private static final int COLOR_FRAME = 0xFF8A8AC8;

	private ChoiceEntry(EmiIngredient stack, long amount, long total) {
		super(stack, amount, total);
	}

	/**
	 * A plain synthetic for one item, a framed one for anything that is a choice.
	 *
	 * <p>Returning the base class for the common case keeps the extra drawing off every slot in
	 * the list, and keeps the entry identical to what EMI would have built by itself.
	 */
	public static EmiFavorite.Synthetic of(EmiIngredient stack, long amount, long total) {
		if (TreeTabsConfig.markChoiceEntries && isChoice(stack)) {
			return new ChoiceEntry(stack, amount, total);
		}
		return new EmiFavorite.Synthetic(stack, amount, total);
	}

	/** A tag, or any other ingredient that would accept more than one item. */
	private static boolean isChoice(EmiIngredient stack) {
		if (stack instanceof TagEmiIngredient) {
			return true;
		}
		if (stack instanceof ListEmiIngredient list) {
			List<? extends EmiIngredient> options = list.getIngredients();
			return options != null && options.size() > 1;
		}
		return false;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
		super.render(graphics, x, y, delta, flags);
		// EMI draws the icon as 16x16 at (x, y); the slot is 18 wide, so this lands on the gap.
		int left = x - 1;
		int top = y - 1;
		int right = x + 17;
		int bottom = y + 17;
		graphics.fill(left, top, right, top + 1, COLOR_FRAME);
		graphics.fill(left, bottom - 1, right, bottom, COLOR_FRAME);
		graphics.fill(left, top + 1, left + 1, bottom - 1, COLOR_FRAME);
		graphics.fill(right - 1, top + 1, right, bottom - 1, COLOR_FRAME);
	}
}
