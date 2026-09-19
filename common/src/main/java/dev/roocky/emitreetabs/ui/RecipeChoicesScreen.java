package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import dev.roocky.emitreetabs.tab.RecipeChoices;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Shows where the open trees disagree about how to make something, and fixes it.
 *
 * <p>This is the whole of the feature's interface, and it is reached deliberately: a button that
 * appears in the sidebar only when there is a disagreement to look at. Nothing about it happens on
 * its own, which is the point — the version this replaced announced itself whenever you picked a
 * recipe and then offered a keybind you had to already know.
 *
 * <p>Each row is one ingredient the trees make in more than one way. Under it, one line per way,
 * showing the recipe's inputs and which trees use it, and a button that makes every other tree
 * agree. Nothing is hidden behind a count: you can see which trees would change before changing
 * them.
 */
public final class RecipeChoicesScreen extends Screen {

	private static final int ROW = 26;
	private static final int OPTION = 24;
	private static final int MARGIN = 16;
	private static final int SLOT = 18;

	private final Screen parent;
	private List<RecipeChoices.Conflict> conflicts;
	private int scroll;

	public RecipeChoicesScreen(Screen parent) {
		super(Component.translatable("emi.tree_tabs.choices.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		conflicts = RecipeChoices.scan();
		rebuild();
	}

	private void rebuild() {
		clearWidgets();
		int y = MARGIN + 34 - scroll;
		for (RecipeChoices.Conflict conflict : conflicts) {
			y += ROW;
			for (RecipeChoices.Option option : conflict.options()) {
				int changes = conflict.wouldChange(option);
				int by = y + 2;
				if (by > MARGIN + 20 && by < height - MARGIN - 30) {
					Button button = Button.builder(
							Component.translatable("emi.tree_tabs.choices.use", changes),
							b -> apply(conflict, option))
							.bounds(width - MARGIN - 96, by, 96, 18)
							.tooltip(Tooltip.create(
									Component.translatable("emi.tree_tabs.choices.use.desc")))
							.build();
					button.active = changes > 0;
					addRenderableWidget(button);
				}
				y += OPTION;
			}
			y += 6;
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 50, height - MARGIN - 20, 100, 20).build());
	}

	private void apply(RecipeChoices.Conflict conflict, RecipeChoices.Option option) {
		RecipeChoices.applyToAll(conflict.ingredient(), option.recipe());
		// Re-scanned rather than patched: applying one choice can settle or reshape others, and a
		// list that disagrees with the trees is worse than no list.
		conflicts = RecipeChoices.scan();
		scroll = 0;
		rebuild();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		renderBackground(g);
		g.drawCenteredString(font, title, width / 2, MARGIN, TabPalette.TEXT);

		if (conflicts.isEmpty()) {
			g.drawCenteredString(font, Component.translatable("emi.tree_tabs.choices.none"),
					width / 2, height / 2 - 10, TabPalette.TEXT_DIM);
			g.drawCenteredString(font, Component.translatable("emi.tree_tabs.choices.none.desc"),
					width / 2, height / 2 + 4, TabPalette.PROGRESS_NONE);
			super.render(g, mouseX, mouseY, delta);
			return;
		}

		g.drawCenteredString(font,
				Component.translatable("emi.tree_tabs.choices.summary", conflicts.size()),
				width / 2, MARGIN + 14, TabPalette.TEXT_DIM);

		int top = MARGIN + 30;
		int bottom = height - MARGIN - 28;
		g.enableScissor(0, top, width, bottom);
		int y = MARGIN + 34 - scroll;
		for (RecipeChoices.Conflict conflict : conflicts) {
			y = drawConflict(g, conflict, y, top, bottom);
		}
		g.disableScissor();
		super.render(g, mouseX, mouseY, delta);
	}

	private int drawConflict(GuiGraphics g, RecipeChoices.Conflict conflict, int y,
			int top, int bottom) {
		if (y + ROW > top && y < bottom) {
			g.fill(MARGIN, y, width - MARGIN, y + ROW - 2, TabPalette.GROUP);
			conflict.ingredient().render(g, MARGIN + 4, y + 3, 0f, EmiIngredient.RENDER_ICON);
			g.drawString(font, name(conflict.ingredient()), MARGIN + 4 + SLOT + 6, y + 8,
					TabPalette.TEXT, false);
			g.drawString(font, Component.translatable("emi.tree_tabs.choices.ways",
							conflict.options().size()).withStyle(ChatFormatting.GRAY),
					MARGIN + 4 + SLOT + 6 + font.width(name(conflict.ingredient())) + 8, y + 8,
					TabPalette.TEXT_DIM, false);
		}
		y += ROW;

		for (RecipeChoices.Option option : conflict.options()) {
			if (y + OPTION > top && y < bottom) {
				drawOption(g, option, y);
			}
			y += OPTION;
		}
		return y + 6;
	}

	private void drawOption(GuiGraphics g, RecipeChoices.Option option, int y) {
		int x = MARGIN + 20;
		// The recipe's inputs, because that is what tells two ways of making the same thing apart.
		// Its output is the ingredient named on the row above and would say nothing.
		List<EmiIngredient> inputs = new ArrayList<>();
		if (option.recipe() != null) {
			for (EmiIngredient in : option.recipe().getInputs()) {
				if (in != null && !in.isEmpty() && inputs.size() < 5) {
					inputs.add(in);
				}
			}
		}
		if (inputs.isEmpty()) {
			// No inputs means no recipe: this way is "go and get it". Advanced by the label's own
			// width, not a guessed constant - 60px was narrower than the words and the tree names
			// were drawn straight through them.
			Component raw = Component.translatable("emi.tree_tabs.choices.raw");
			g.drawString(font, raw, x, y + 5, TabPalette.PROGRESS_NONE, false);
			x += font.width(raw);
		} else {
			for (EmiIngredient in : inputs) {
				in.render(g, x, y, 0f, EmiIngredient.RENDER_ICON | EmiIngredient.RENDER_AMOUNT);
				x += SLOT;
			}
		}
		g.drawString(font, trees(option), x + 10, y + 5, TabPalette.TEXT_DIM, false);
	}

	/** Which trees use this way, by name, because "3 trees" does not tell you which three. */
	private String trees(RecipeChoices.Option option) {
		StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (TreeTab tab : option.trees()) {
			if (shown == 3) {
				sb.append(Component.translatable("emi.tree_tabs.choices.more",
						option.trees().size() - shown).getString());
				break;
			}
			if (shown > 0) {
				sb.append(", ");
			}
			sb.append(tab.displayName().getString());
			shown++;
		}
		return sb.toString();
	}

	private String name(EmiIngredient ingredient) {
		List<EmiStack> stacks = ingredient.getEmiStacks();
		return stacks.isEmpty() ? "?" : stacks.get(0).getName().getString();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll = Math.max(0, scroll - (int) (amount * 20));
		rebuild();
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
