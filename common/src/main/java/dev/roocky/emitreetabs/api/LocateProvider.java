package dev.roocky.emitreetabs.api;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Where the player can find something they are short of. */
public interface LocateProvider {

	/**
	 * @param stack what the crafting list says is missing
	 * @return one line per place, or an empty list when this provider knows nothing about it.
	 *         Keep it short — these go on a tooltip that already has a lot to say, and Tree Tabs
	 *         will truncate rather than let it fill the screen.
	 *
	 *         <p>Called while a tooltip is being built, every frame the cursor is on the item, so
	 *         it must be cheap and must not block. Cache on your side if the answer is expensive.
	 */
	List<Component> locate(ItemStack stack);
}
