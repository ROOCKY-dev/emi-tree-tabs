package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.ProgressState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The tab strip drawn over EMI's recipe tree screen, and all of the input that belongs to it.
 *
 * <p>State is static because exactly one tree screen can be open at a time. {@link #reset()} clears
 * everything transient whenever a screen is (re)built.
 */
public final class TabBar {
	public static final int HEIGHT = 22;

	private static final int PAD = 2;
	// Narrow enough that a dozen tabs still fit on a small screen before scrolling starts.
	private static final int MIN_TAB_WIDTH = 24;
	private static final int ARROW_WIDTH = 9;
	private static final int MAX_TAB_WIDTH = 124;
	private static final int ADD_BUTTON_WIDTH = 18;
	private static final int ALL_BUTTON_WIDTH = 18;
	private static final int ICON_SIZE = 16;
	/** Below this width there is no room for a label, so tabs become icon only. */
	private static final int LABEL_THRESHOLD = 56;
	private static final int CLOSE_THRESHOLD = 48;
	private static final int DRAG_SLOP = 4;

	private static final int COLOR_BAR = 0xE0121212;
	private static final int COLOR_BORDER = 0xFF000000;
	private static final int COLOR_TAB = 0xFF1B1B20;
	private static final int COLOR_TAB_HOVER = 0xFF262630;
	private static final int COLOR_TAB_ACTIVE = 0xFF313142;
	private static final int COLOR_ACCENT = 0xFF5A8CFF;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM = 0xFF9A9AA2;
	private static final int COLOR_CLOSE_HOVER = 0xFFD05050;
	private static final int COLOR_CRAFTING = 0xFF48C8E0;
	private static final int COLOR_DIVIDER = 0x40FFFFFF;

	private static double scroll;
	private static int dragIndex = -1;
	private static double dragOriginX;
	private static double dragX;
	private static boolean dragging;

	private static EditBox renameBox;
	private static int renameIndex = -1;

	private TabBar() {
	}

	// ---------------------------------------------------------------- layout

	public static boolean visible() {
		return TreeTabsConfig.enabled && TreeTabs.count() > 0;
	}

	public static int barY(Screen screen) {
		return TreeTabsConfig.barAtBottom ? screen.height - HEIGHT : 0;
	}

	/** True when the pointer is anywhere over the strip, including its background. */
	public static boolean isOver(Screen screen, double mouseX, double mouseY) {
		if (!visible()) {
			return false;
		}
		int y = barY(screen);
		return mouseY >= y && mouseY < y + HEIGHT && mouseX >= 0 && mouseX <= screen.width;
	}

	/** Room for tabs before any scroll arrows are taken into account. */
	private static int baseWidth(Screen screen) {
		return Math.max(MIN_TAB_WIDTH, screen.width - PAD * 2 - ADD_BUTTON_WIDTH - ALL_BUTTON_WIDTH);
	}

	private static int allButtonX(Screen screen) {
		return screen.width - PAD - ADD_BUTTON_WIDTH - ALL_BUTTON_WIDTH;
	}

	private static boolean overAllButton(Screen screen, double mouseX, double mouseY) {
		if (!isOver(screen, mouseX, mouseY) || TreeTabs.count() == 0) {
			return false;
		}
		int x = allButtonX(screen);
		return mouseX >= x && mouseX < x + ALL_BUTTON_WIDTH;
	}

	/** True once tabs cannot fit even at their narrowest, which is when scrolling kicks in. */
	private static boolean overflowing(Screen screen) {
		return TreeTabs.count() * MIN_TAB_WIDTH > baseWidth(screen);
	}

	private static int stripLeft(Screen screen) {
		return PAD + (overflowing(screen) ? ARROW_WIDTH : 0);
	}

	private static int stripWidth(Screen screen) {
		int base = baseWidth(screen);
		return Math.max(MIN_TAB_WIDTH, overflowing(screen) ? base - ARROW_WIDTH * 2 : base);
	}

	private static int tabWidth(Screen screen) {
		int count = Math.max(1, TreeTabs.count());
		int even = stripWidth(screen) / count;
		return Math.max(MIN_TAB_WIDTH, Math.min(MAX_TAB_WIDTH, even));
	}

	private static double maxScroll(Screen screen) {
		return Math.max(0, TreeTabs.count() * (double) tabWidth(screen) - stripWidth(screen));
	}

	private static int tabX(Screen screen, int index) {
		return (int) (stripLeft(screen) + index * (long) tabWidth(screen) - scroll);
	}

	/** @return the tab under the pointer, or -1. */
	private static int tabAt(Screen screen, double mouseX, double mouseY) {
		int left = stripLeft(screen);
		if (!isOver(screen, mouseX, mouseY) || mouseX < left || mouseX >= left + stripWidth(screen)) {
			return -1;
		}
		int width = tabWidth(screen);
		int index = (int) Math.floor((mouseX - left + scroll) / width);
		return index >= 0 && index < TreeTabs.count() ? index : -1;
	}

	private static boolean overAddButton(Screen screen, double mouseX, double mouseY) {
		if (!isOver(screen, mouseX, mouseY) || TreeTabs.activeTab() == null) {
			return false;
		}
		return mouseX >= screen.width - ADD_BUTTON_WIDTH - PAD && mouseX <= screen.width - PAD;
	}

	private static boolean overClose(Screen screen, int index, double mouseX) {
		int width = tabWidth(screen);
		if (width < CLOSE_THRESHOLD) {
			return false;
		}
		int closeX = tabX(screen, index) + width - 13;
		return mouseX >= closeX && mouseX < closeX + 10;
	}

	private static void clampScroll(Screen screen) {
		scroll = Math.max(0, Math.min(scroll, maxScroll(screen)));
	}

	/** Scrolls just far enough that the given tab is fully on screen. */
	public static void ensureVisible(Screen screen, int index) {
		if (index < 0) {
			return;
		}
		int width = tabWidth(screen);
		double left = index * (double) width;
		double right = left + width;
		if (left < scroll) {
			scroll = left;
		} else if (right > scroll + stripWidth(screen)) {
			scroll = right - stripWidth(screen);
		}
		clampScroll(screen);
	}

	// --------------------------------------------------------------- drawing

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (!visible()) {
			return;
		}
		TreeTabs.syncActiveCraftingMode();
		clampScroll(screen);

		Font font = Minecraft.getInstance().font;
		int y = barY(screen);
		int count = TreeTabs.count();
		int width = tabWidth(screen);
		int hovered = tabAt(screen, mouseX, mouseY);

		// Only the tabs actually on screen need their progress costed.
		int firstVisible = (int) Math.floor(scroll / width);
		int lastVisible = (int) Math.ceil((scroll + stripWidth(screen)) / width);
		TreeTabs.pollProgress(firstVisible, lastVisible);

		graphics.fill(0, y, screen.width, y + HEIGHT, COLOR_BAR);
		int borderY = TreeTabsConfig.barAtBottom ? y : y + HEIGHT - 1;
		graphics.fill(0, borderY, screen.width, borderY + 1, COLOR_BORDER);

		graphics.enableScissor(stripLeft(screen), y, stripLeft(screen) + stripWidth(screen), y + HEIGHT);
		for (int i = 0; i < count; i++) {
			if (dragging && i == dragIndex) {
				continue;
			}
			int x = tabX(screen, i);
			if (x + width < stripLeft(screen) || x > stripLeft(screen) + stripWidth(screen)) {
				continue;
			}
			drawTab(screen, graphics, font, i, x, y, width, i == hovered, mouseX);
		}
		graphics.disableScissor();

		if (dragging && dragIndex >= 0) {
			int x = (int) (dragX - width / 2.0);
			drawTab(screen, graphics, font, dragIndex, x, y, width, true, -1);
		}

		drawArrows(screen, graphics, font, mouseX, mouseY, y);
		drawAllButton(screen, graphics, font, mouseX, mouseY, y);
		drawAddButton(screen, graphics, font, mouseX, mouseY, y);

		if (renameBox != null) {
			renameBox.render(graphics, mouseX, mouseY, delta);
		} else if (hovered >= 0) {
			drawTooltip(screen, graphics, font, hovered, mouseX, mouseY);
		} else if (overAllButton(screen, mouseX, mouseY)) {
			int crafting = TreeTabs.craftingCount();
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("emi.tree_tabs.all.title"),
					Component.translatable("emi.tree_tabs.all.state", crafting, TreeTabs.count())
							.withStyle(ChatFormatting.GRAY),
					Component.translatable("emi.tree_tabs.all.hint").withStyle(ChatFormatting.DARK_GRAY)),
					mouseX, mouseY);
		} else if (overAddButton(screen, mouseX, mouseY)) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("emi.tree_tabs.fork"),
					Component.translatable("emi.tree_tabs.fork.desc").withStyle(ChatFormatting.GRAY)),
					mouseX, mouseY);
		}
	}

	private static void drawTab(Screen screen, GuiGraphics graphics, Font font, int index,
			int x, int y, int width, boolean hovered, int mouseX) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null) {
			return;
		}
		boolean isActive = index == TreeTabs.activeIndex();
		int background = isActive ? COLOR_TAB_ACTIVE : hovered ? COLOR_TAB_HOVER : COLOR_TAB;
		graphics.fill(x, y + 1, x + width - 1, y + HEIGHT - 1, background);

		int accent = accentColor(tab);
		if (isActive) {
			int accentY = TreeTabsConfig.barAtBottom ? y + HEIGHT - 3 : y + 1;
			graphics.fill(x, accentY, x + width - 1, accentY + 2, accent);
		} else {
			int accentY = TreeTabsConfig.barAtBottom ? y + 1 : y + HEIGHT - 3;
			graphics.fill(x, accentY, x + width - 1, accentY + 1, accent);
			// Hairline between inactive neighbours; the active tab reads on its own.
			graphics.fill(x + width - 1, y + 4, x + width, y + HEIGHT - 4, COLOR_DIVIDER);
		}

		EmiIngredient icon = tab.icon();
		if (!icon.isEmpty()) {
			icon.render(graphics, x + 4, y + 3, 0f, EmiIngredient.RENDER_ICON);
			if (tab.craftingMode) {
				// A pip on the icon's corner: this tree is being built, not just read.
				graphics.fill(x + 3 + ICON_SIZE - 4, y + 2 + ICON_SIZE - 4,
						x + 3 + ICON_SIZE + 1, y + 2 + ICON_SIZE + 1, 0xFF000000);
				graphics.fill(x + 3 + ICON_SIZE - 3, y + 2 + ICON_SIZE - 3,
						x + 3 + ICON_SIZE, y + 2 + ICON_SIZE, COLOR_CRAFTING);
			}
		}

		boolean closeShown = width >= CLOSE_THRESHOLD && (hovered || isActive);
		if (width >= LABEL_THRESHOLD) {
			int textX = x + 4 + ICON_SIZE + 3;
			int budget = x + width - textX - (closeShown ? 14 : 4);
			if (budget > 4) {
				graphics.drawString(font, tab.trimmedLabel(font, budget), textX, y + 7,
						isActive ? COLOR_TEXT : COLOR_TEXT_DIM, false);
			}
		}

		if (closeShown) {
			int closeX = x + width - 13;
			boolean closeHovered = mouseX >= closeX && mouseX < closeX + 10;
			graphics.drawString(font, "×", closeX + 2, y + 7,
					closeHovered ? COLOR_CLOSE_HOVER : COLOR_TEXT_DIM, false);
		}
	}

	/** Scroll affordances, so a long strip does not look like it simply ends. */
	private static void drawArrows(Screen screen, GuiGraphics graphics, Font font,
			int mouseX, int mouseY, int y) {
		if (!overflowing(screen)) {
			return;
		}
		boolean canLeft = scroll > 0.5;
		boolean canRight = scroll < maxScroll(screen) - 0.5;
		boolean overLeft = mouseX < PAD + ARROW_WIDTH && isOver(screen, mouseX, mouseY);
		int rightX = stripLeft(screen) + stripWidth(screen);
		boolean overRight = mouseX >= rightX && mouseX < rightX + ARROW_WIDTH
				&& isOver(screen, mouseX, mouseY);
		graphics.drawString(font, "\u25c0", PAD + 1, y + 7,
				canLeft ? (overLeft ? COLOR_TEXT : COLOR_TEXT_DIM) : 0xFF3A3A40, false);
		graphics.drawString(font, "\u25b6", rightX + 1, y + 7,
				canRight ? (overRight ? COLOR_TEXT : COLOR_TEXT_DIM) : 0xFF3A3A40, false);
	}

	/**
	 * Switches every tab between viewing and crafting.
	 *
	 * <p>Shows how many are being crafted, because with a dozen tabs the state is otherwise only
	 * legible by scanning every icon for its corner pip.
	 */
	private static void drawAllButton(Screen screen, GuiGraphics graphics, Font font,
			int mouseX, int mouseY, int y) {
		if (TreeTabs.count() == 0) {
			return;
		}
		int x = allButtonX(screen);
		boolean hovered = overAllButton(screen, mouseX, mouseY);
		int crafting = TreeTabs.craftingCount();
		graphics.fill(x, y + 1, x + ALL_BUTTON_WIDTH, y + HEIGHT - 1, hovered ? COLOR_TAB_HOVER : COLOR_TAB);
		int colour = crafting == 0 ? COLOR_TEXT_DIM : COLOR_CRAFTING;
		graphics.drawString(font, "\u2261", x + 3, y + 7, colour, false);
		if (crafting > 0) {
			graphics.fill(x + ALL_BUTTON_WIDTH - 5, y + 4, x + ALL_BUTTON_WIDTH - 2, y + 7, COLOR_CRAFTING);
		}
	}

	private static void drawAddButton(Screen screen, GuiGraphics graphics, Font font,
			int mouseX, int mouseY, int y) {
		if (TreeTabs.activeTab() == null) {
			return;
		}
		int x = screen.width - ADD_BUTTON_WIDTH - PAD;
		boolean hovered = overAddButton(screen, mouseX, mouseY);
		graphics.fill(x, y + 1, x + ADD_BUTTON_WIDTH, y + HEIGHT - 1, hovered ? COLOR_TAB_HOVER : COLOR_TAB);
		graphics.drawString(font, "+", x + ADD_BUTTON_WIDTH / 2 - 2, y + 7,
				hovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
	}

	private static void drawTooltip(Screen screen, GuiGraphics graphics, Font font, int index,
			int mouseX, int mouseY) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		lines.add(tab.displayName());
		if (tab.customName != null) {
			lines.add(tab.goalName().copy().withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("emi.tree_tabs.batches", tab.batches()).withStyle(ChatFormatting.GRAY));
		if (TreeTabsConfig.showProgress) {
			lines.add(progressText(tab.progress));
		}
		lines.add(tab.craftingMode
				? Component.translatable("emi.tree_tabs.mode.craft").withStyle(ChatFormatting.AQUA)
				: Component.translatable("emi.tree_tabs.mode.view").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("emi.tree_tabs.hint.select").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("emi.tree_tabs.hint.rename").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("emi.tree_tabs.hint.close").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.translatable("emi.tree_tabs.hint.crafting").withStyle(ChatFormatting.DARK_GRAY));
		graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
	}

	private static int accentColor(TreeTab tab) {
		if (!TreeTabsConfig.showProgress || tab.progress == null) {
			return COLOR_ACCENT;
		}
		// Deliberately not a switch: switching on another mod's enum makes javac emit a synthetic
		// TabBar$1 switch-map class, one more thing that has to resolve at runtime.
		if (tab.progress == ProgressState.COMPLETED) {
			return 0xFF5BD16A;
		}
		if (tab.progress == ProgressState.PARTIAL) {
			return 0xFFE0A63C;
		}
		return 0xFF6E6E76;
	}

	private static Component progressText(ProgressState state) {
		if (state == null) {
			state = ProgressState.UNSTARTED;
		}
		if (state == ProgressState.COMPLETED) {
			return Component.translatable("emi.tree_tabs.progress.complete").withStyle(ChatFormatting.GREEN);
		}
		if (state == ProgressState.PARTIAL) {
			return Component.translatable("emi.tree_tabs.progress.partial").withStyle(ChatFormatting.GOLD);
		}
		return Component.translatable("emi.tree_tabs.progress.unstarted").withStyle(ChatFormatting.GRAY);
	}

	// ----------------------------------------------------------------- input

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (!visible()) {
			return false;
		}
		if (renameBox != null) {
			if (renameBox.mouseClicked(mouseX, mouseY, button)) {
				return true;
			}
			commitRename();
		}
		if (!isOver(screen, mouseX, mouseY)) {
			return false;
		}

		if (button == 0 && overflowing(screen)) {
			int rightX = stripLeft(screen) + stripWidth(screen);
			if (mouseX < PAD + ARROW_WIDTH) {
				scroll -= tabWidth(screen);
				clampScroll(screen);
				return true;
			}
			if (mouseX >= rightX && mouseX < rightX + ARROW_WIDTH) {
				scroll += tabWidth(screen);
				clampScroll(screen);
				return true;
			}
		}
		if (button == 0 && overAllButton(screen, mouseX, mouseY)) {
			click();
			TreeTabs.toggleAllCrafting();
			return true;
		}
		if (button == 0 && overAddButton(screen, mouseX, mouseY)) {
			click();
			TreeTabs.duplicate(TreeTabs.activeIndex());
			ensureVisible(screen, TreeTabs.activeIndex());
			return true;
		}

		int index = tabAt(screen, mouseX, mouseY);
		if (index < 0) {
			// Swallow clicks on empty strip so they do not pan the tree underneath.
			return true;
		}

		if (button == 2 || (button == 0 && overClose(screen, index, mouseX))) {
			click();
			TreeTabs.close(index);
			ensureVisible(screen, TreeTabs.activeIndex());
			return true;
		}
		if (button == 1) {
			startRename(screen, index);
			return true;
		}
		if (button == 0 && Screen.hasControlDown()) {
			click();
			TreeTabs.toggleCrafting(index);
			return true;
		}
		if (button == 0) {
			click();
			TreeTabs.select(index);
			ensureVisible(screen, index);
			dragIndex = index;
			dragOriginX = mouseX;
			dragX = mouseX;
			dragging = false;
			return true;
		}
		return true;
	}

	public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
		if (!visible() || dragIndex < 0 || button != 0) {
			return false;
		}
		dragX = mouseX;
		if (!dragging && Math.abs(mouseX - dragOriginX) > DRAG_SLOP) {
			dragging = true;
		}
		return true;
	}

	/**
	 * BoMScreen does not override mouseReleased, so there is nothing to inject into. Polling the
	 * button once a frame is enough to finish a drag.
	 */
	public static void tickDrag(Screen screen) {
		if (dragIndex < 0) {
			return;
		}
		long window = Minecraft.getInstance().getWindow().getWindow();
		if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_RELEASE) {
			return;
		}
		if (dragging) {
			// stripLeft, not PAD: they differ by ARROW_WIDTH exactly when the scroll arrows are
			// showing, which is when you have enough tabs to want to reorder them. Using PAD here
			// put every drop up to a third of a tab off.
			int target = (int) Math.floor((dragX - stripLeft(screen) + scroll) / tabWidth(screen));
			target = Math.max(0, Math.min(target, TreeTabs.count() - 1));
			if (target != dragIndex) {
				TreeTabs.move(dragIndex, target);
				ensureVisible(screen, TreeTabs.activeIndex());
			}
		}
		dragIndex = -1;
		dragging = false;
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		if (!visible() || !isOver(screen, mouseX, mouseY)) {
			return false;
		}
		if (renameBox != null || maxScroll(screen) <= 0) {
			// Nothing to scroll, but still swallow it so the tree does not zoom under the bar.
			return true;
		}
		scroll -= amount * tabWidth(screen) / 2.0;
		clampScroll(screen);
		return true;
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (!TreeTabsConfig.enabled) {
			return false;
		}
		if (renameBox != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				cancelRename();
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitRename();
				return true;
			}
			renameBox.keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (!TreeTabsConfig.keyboardShortcuts) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_F2 && TreeTabs.activeTab() != null) {
			startRename(screen, TreeTabs.activeIndex());
			return true;
		}
		if (!Screen.hasControlDown()) {
			return false;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_TAB -> {
				TreeTabs.selectRelative(Screen.hasShiftDown() ? -1 : 1);
				ensureVisible(screen, TreeTabs.activeIndex());
				return true;
			}
			case GLFW.GLFW_KEY_W -> {
				if (TreeTabs.activeTab() != null) {
					click();
					TreeTabs.close(TreeTabs.activeIndex());
					ensureVisible(screen, TreeTabs.activeIndex());
				}
				return true;
			}
			case GLFW.GLFW_KEY_T -> {
				if (Screen.hasShiftDown() && TreeTabs.hasClosedTabs()) {
					click();
					TreeTabs.reopenClosed();
					ensureVisible(screen, TreeTabs.activeIndex());
					return true;
				}
				return false;
			}
			case GLFW.GLFW_KEY_A -> {
				if (TreeTabs.count() > 0) {
					click();
					TreeTabs.toggleAllCrafting();
					return true;
				}
				return false;
			}
			case GLFW.GLFW_KEY_D -> {
				if (TreeTabs.activeTab() != null) {
					click();
					TreeTabs.duplicate(TreeTabs.activeIndex());
					ensureVisible(screen, TreeTabs.activeIndex());
				}
				return true;
			}
			default -> {
				if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
					int slot = keyCode - GLFW.GLFW_KEY_1;
					int index = slot == 8 ? TreeTabs.count() - 1 : slot;
					if (index >= 0 && index < TreeTabs.count()) {
						TreeTabs.select(index);
						ensureVisible(screen, index);
					}
					return true;
				}
				return false;
			}
		}
	}

	public static boolean isRenaming() {
		return renameBox != null;
	}

	// ---------------------------------------------------------------- rename

	private static void startRename(Screen screen, int index) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null) {
			return;
		}
		TreeTabs.select(index);
		ensureVisible(screen, index);

		int width = tabWidth(screen);
		int x = tabX(screen, index);
		int y = barY(screen);
		renameBox = new EditBox(Minecraft.getInstance().font, x + 2, y + 3, Math.max(40, width - 4), 16,
				Component.translatable("emi.tree_tabs.rename"));
		renameBox.setMaxLength(48);
		renameBox.setBordered(true);
		renameBox.setValue(tab.customName != null ? tab.customName : tab.goalName().getString());
		renameBox.moveCursorToEnd();
		renameBox.setHighlightPos(0);
		renameIndex = index;
		screen.setFocused(renameBox);
		renameBox.setFocused(true);
	}

	private static void commitRename() {
		if (renameBox == null) {
			return;
		}
		TreeTabs.rename(renameIndex, renameBox.getValue());
		clearRename();
	}

	private static void cancelRename() {
		clearRename();
	}

	private static void clearRename() {
		Screen screen = Minecraft.getInstance().screen;
		if (screen != null && screen.getFocused() == renameBox) {
			screen.setFocused(null);
		}
		renameBox = null;
		renameIndex = -1;
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	/** Called whenever the tree screen is (re)initialised. */
	public static void reset() {
		clearRename();
		dragIndex = -1;
		dragging = false;
	}
}
