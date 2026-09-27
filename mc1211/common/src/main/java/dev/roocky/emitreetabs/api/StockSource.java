package dev.roocky.emitreetabs.api;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Materials the player has somewhere that is not their inventory — a chest, a drawer, a backpack.
 *
 * <p>Plain {@link ItemStack}s rather than EMI types on purpose. A container holds stacks, not
 * ingredients, and a consumer of this API should not have to learn EMI's stack model to say what is
 * in a box. Matching a stack against a tag or a choice of items is Tree Tabs' job, because Tree
 * Tabs is the one that already carries the EMI dependency.
 */
public interface StockSource {

	/**
	 * Everything this source can offer right now.
	 *
	 * <p>Called during a crafting-list pass, which happens on the client thread while a screen is
	 * open, so it must be cheap and must not block. Return a snapshot: Tree Tabs may hold the list
	 * for the length of the pass but will not mutate it.
	 *
	 * <p>Amounts are summed, so the same item may appear more than once.
	 */
	List<ItemStack> stock();

	/**
	 * What to call this on a tooltip — "In chests", "In your backpack".
	 *
	 * <p>Shown beside the amount that came from here, which is the whole point of the distinction:
	 * the crafting list saying you need nothing because it is all in a chest three rooms away is
	 * only useful if it says so.
	 */
	Component label();
}
