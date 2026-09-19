package dev.roocky.emitreetabs.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.CraftingFavorites;
import dev.roocky.emitreetabs.tab.ApiRegistry;
import dev.roocky.emitreetabs.tab.SubCraftCosts;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.runtime.EmiFavorite;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
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
 * <p>Two splits, because there are two questions and they have different answers. <em>Which tree</em>
 * is the default. <em>Which sub-craft</em> is behind Shift, because a single tree can want copper in
 * several places — a mixer needs a fan needs plates — and showing both at once is how a tooltip
 * turns into the problem rather than the answer.
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
	/** Tighter, because a locate provider's lines sit above everything else this adds. */
	private static final int MAX_LOCATE_LINES = 3;

	/**
	 * {@code RETURN}, not {@code TAIL}. {@code Synthetic.getTooltip} returns early for
	 * {@code state == -1}, and {@code state} is -1 for exactly the entries this is about — the raw
	 * material costs, built by the {@code (ingredient, needed, total)} constructor. TAIL hooks only
	 * the last return in the method, so it fired for recipes and never once for a material, which
	 * is why this feature appeared to do nothing at all until it was run.
	 */
	@Inject(method = "getTooltip", at = @At("RETURN"), cancellable = true, require = 0)
	private void emitreetabs$attribution(CallbackInfoReturnable<List<ClientTooltipComponent>> cir) {
		if (!TreeTabsConfig.enabled || !TreeTabsConfig.aggregateCraftingFavorites) {
			return;
		}
		EmiFavorite self = (EmiFavorite) (Object) this;
		if (self.getRecipe() != null) {
			// A recipe entry is already attributed by being a recipe; only raw materials are shared.
			return;
		}
		List<ClientTooltipComponent> lines = cir.getReturnValue();
		if (lines == null) {
			return;
		}
		EmiIngredient stack = self.getStack();

		// Where it is, and where it is coming from. Both are answers other mods supply, and both
		// belong before the splits: "it is in the chest behind you" ends the question that the
		// arithmetic below only helps you think about.
		stockSource(lines, stack);
		locate(lines, stack);

		List<CraftingFavorites.Attribution> byTree = CraftingFavorites.attribution(stack);

		if (Screen.hasShiftDown()) {
			subCraftSplit(lines, stack, byTree);
			return;
		}
		if (byTree.size() < 2) {
			// One tree wanting all of it is not a split. The sub-craft view may still have
			// something to say about it, so offer that rather than nothing.
			if (!splitBody(stack, byTree).isEmpty()) {
				hint(lines);
			}
			return;
		}
		add(lines, Component.translatable("emi.tree_tabs.attribution.title")
				.withStyle(ChatFormatting.GRAY));
		int shown = 0;
		for (CraftingFavorites.Attribution a : byTree) {
			if (shown >= MAX_LINES) {
				add(lines, Component.translatable("emi.tree_tabs.attribution.more",
						byTree.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			add(lines, Component.translatable("emi.tree_tabs.attribution.line",
					a.needed(), a.tab().displayName()).withStyle(ChatFormatting.AQUA));
			shown++;
		}
		// Offered only when Shift would actually show something.
		if (!splitBody(stack, byTree).isEmpty()) {
			hint(lines);
		}
	}

	/**
	 * The sub-craft split.
	 *
	 * <p>Built before anything is drawn, because a heading must not appear without a body. It did:
	 * the per-tree lists were filtered to "two or more consumers" further down, so a material that
	 * each tree spent in one place produced "Goes into" and then nothing at all.
	 *
	 * <p>With one tree it is shown flat, since naming the tree would repeat the obvious. With
	 * several, each tree's line is followed by its own sub-crafts, because "60 for plates" summed
	 * across two machines does not tell you which machine to stop building.
	 */
	private static void subCraftSplit(List<ClientTooltipComponent> lines, EmiIngredient stack,
			List<CraftingFavorites.Attribution> byTree) {
		List<Component> body = splitBody(stack, byTree);
		if (body.isEmpty()) {
			return;
		}
		add(lines, Component.translatable("emi.tree_tabs.attribution.subcraft.title")
				.withStyle(ChatFormatting.GRAY));
		for (Component line : body) {
			add(lines, line);
		}
	}

	/**
	 * The lines the split would draw, or empty when it has nothing to say.
	 *
	 * <p>"Nothing to say" is one destination: a material that goes to a single place is only
	 * restating the total the tooltip has already given.
	 */
	private static List<Component> splitBody(EmiIngredient stack,
			List<CraftingFavorites.Attribution> byTree) {
		List<Component> out = new ArrayList<>();
		if (byTree.size() < 2) {
			List<SubCraftCosts.Share> flat = byTree.isEmpty()
					? SubCraftCosts.shares(stack)
					: SubCraftCosts.shares(stack, byTree.get(0).tab());
			if (flat.size() < 2) {
				return List.of();
			}
			shareLines(out, flat, MAX_LINES);
			return out;
		}
		int splits = 0;
		for (CraftingFavorites.Attribution a : byTree) {
			List<SubCraftCosts.Share> shares = SubCraftCosts.shares(stack, a.tab());
			if (shares.isEmpty() || out.size() >= MAX_LINES) {
				continue;
			}
			out.add(Component.translatable("emi.tree_tabs.attribution.line",
					a.needed(), a.tab().displayName()).withStyle(ChatFormatting.AQUA));
			// A tree that spends it in one place needs no breakdown: its own line already carries
			// that number, and the sub-line under it read "20 for Piston / 20 for Piston".
			if (shares.size() < 2) {
				continue;
			}
			splits++;
			shareLines(out, shares, MAX_LINES - out.size());
		}
		// If no tree splits it, this is the tree view again under a different heading.
		return splits == 0 ? List.of() : out;
	}

	/** Appends up to {@code budget} share lines, then says how many were left out. */
	private static void shareLines(List<Component> out, List<SubCraftCosts.Share> shares,
			int budget) {
		int cap = Math.max(1, budget);
		int shown = 0;
		for (SubCraftCosts.Share share : shares) {
			if (shown >= cap) {
				out.add(Component.translatable("emi.tree_tabs.attribution.more",
						shares.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
				return;
			}
			out.add(Component.translatable("emi.tree_tabs.attribution.subcraft.line",
					share.needed(), name(share.consumer())).withStyle(ChatFormatting.AQUA));
			shown++;
		}
	}


	/** Says when an amount is being counted from somewhere that is not the player's inventory. */
	private static void stockSource(List<ClientTooltipComponent> lines, EmiIngredient stack) {
		Component label = CraftingFavorites.stockLabel(stack);
		if (label != null) {
			add(lines, Component.translatable("emi.tree_tabs.api.stock", label)
					.withStyle(ChatFormatting.GREEN));
		}
	}

	/**
	 * Asks registered providers where a shortfall can be found.
	 *
	 * <p>Only for a cost entry, which is by definition something the player is short of - asking
	 * "where do I find this" about something already in hand is noise.
	 */
	private static void locate(List<ClientTooltipComponent> lines, EmiIngredient stack) {
		ApiRegistry api = ApiRegistry.get();
		if (!api.hasLocate()) {
			return;
		}
		List<dev.emi.emi.api.stack.EmiStack> stacks = stack.getEmiStacks();
		if (stacks.isEmpty()) {
			return;
		}
		List<Component> where = api.locate(stacks.get(0).getItemStack());
		if (where.isEmpty()) {
			return;
		}
		add(lines, Component.translatable("emi.tree_tabs.api.locate")
				.withStyle(ChatFormatting.GRAY));
		int shown = 0;
		for (Component line : where) {
			if (shown >= MAX_LOCATE_LINES) {
				add(lines, Component.translatable("emi.tree_tabs.attribution.more",
						where.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			add(lines, line.copy().withStyle(ChatFormatting.YELLOW));
			shown++;
		}
	}

	private static void hint(List<ClientTooltipComponent> lines) {
		add(lines, Component.translatable("emi.tree_tabs.attribution.hint")
				.withStyle(ChatFormatting.DARK_GRAY));
	}

	private static void add(List<ClientTooltipComponent> lines, Component text) {
		lines.add(ClientTooltipComponent.create(text.getVisualOrderText()));
	}

	/** A sub-craft is named by what it makes, which is the thing the question is about. */
	private static Component name(EmiIngredient consumer) {
		List<dev.emi.emi.api.stack.EmiStack> stacks = consumer.getEmiStacks();
		if (stacks.isEmpty()) {
			return Component.literal("?");
		}
		return stacks.get(0).getName().copy();
	}
}
