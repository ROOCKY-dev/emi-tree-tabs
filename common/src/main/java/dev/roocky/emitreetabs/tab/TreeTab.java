package dev.roocky.emitreetabs.tab;

import com.google.gson.JsonObject;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.MaterialTree;
import dev.emi.emi.bom.ProgressState;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** One tracked recipe tree, plus the bits of view state that make it feel like its own workspace. */
public class TreeTab {
	/** The live tree. Swapped into {@code BoM.tree} while this tab is the active one. */
	public MaterialTree tree;
	/** Name the player typed, or null to fall back on the goal item's name. */
	public String customName;
	/**
	 * The last json this tab serialised to. Kept so a tab can be rebuilt after a resource reload,
	 * duplicated, or restored after being closed, without re-deriving any of it from the live tree.
	 */
	public JsonObject snapshot;

	// Per tab viewport, so switching tabs does not dump you back at the origin of the other tree.
	public double offX;
	public double offY;
	public int zoom;
	public boolean viewportSet;

	/** Cached completion of this tree against the player's inventory. Recomputed on a timer. */
	public ProgressState progress = ProgressState.UNSTARTED;

	/**
	 * Whether this tree is being <em>worked on</em> rather than just looked at.
	 *
	 * <p>Mirrors EMI's own view/craft mode button, but per tab: only trees in crafting mode feed
	 * the crafting list, so you can keep half a dozen trees open for reference and still have the
	 * sidebar show only what you are actually building right now. Switching tab applies its mode.
	 */
	public boolean craftingMode;

	public TreeTab(MaterialTree tree) {
		this.tree = tree;
	}

	public EmiIngredient icon() {
		if (tree != null && tree.goal != null) {
			return tree.goal.ingredient;
		}
		return EmiStack.EMPTY;
	}

	// Label caching. Resolving the goal's name means ItemStack.getHoverName, and trimming it means
	// a width measure per character; doing that every frame for every tab is pure garbage.
	private String trimmedLabel;
	private int trimmedBudget = -1;
	private int trimmedVersion = -1;
	/** Bumped whenever anything the label is derived from changes. */
	public int labelVersion;

	/** The display name, shortened to fit and cached until the name or the space available changes. */
	public String trimmedLabel(Font font, int budget) {
		if (trimmedLabel != null && trimmedBudget == budget && trimmedVersion == labelVersion) {
			return trimmedLabel;
		}
		String full = displayName().getString();
		String trimmed = font.plainSubstrByWidth(full, budget);
		if (!trimmed.equals(full) && trimmed.length() > 1) {
			trimmed = font.plainSubstrByWidth(full, budget - font.width("..")) + "..";
		}
		trimmedLabel = trimmed;
		trimmedBudget = budget;
		trimmedVersion = labelVersion;
		return trimmed;
	}

	public Component displayName() {
		if (customName != null && !customName.isBlank()) {
			return Component.literal(customName);
		}
		return goalName();
	}

	/** The goal item's own name, shown in tooltips even when the tab has been renamed. */
	public Component goalName() {
		EmiIngredient icon = icon();
		if (!icon.getEmiStacks().isEmpty()) {
			return icon.getEmiStacks().get(0).getName();
		}
		return Component.translatable("emi.tree_tabs.unnamed");
	}

	public long batches() {
		return tree == null ? 1 : tree.batches;
	}
}
