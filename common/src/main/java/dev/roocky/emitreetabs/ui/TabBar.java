package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.TabLayout.Density;
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
 * <p>Where things <em>are</em> is {@link TabLayout}'s job; this class draws them and reacts to
 * clicks. Both sides read the same geometry, which is what stops a button from being drawn
 * somewhere other than where it can be clicked.
 *
 * <p>State is static because exactly one tree screen can be open at a time. {@link #reset()} clears
 * everything transient whenever a screen is (re)built.
 */
public final class TabBar {
	public static final int HEIGHT = TabLayout.HEIGHT;

	private static final int ICON_SIZE = TabLayout.ICON_SIZE;
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

	/** Geometry for right now. Cheap to build, and deliberately never cached or held. */
	private static TabLayout layout(Screen screen) {
		return new TabLayout(screen.width, screen.height, TreeTabs.count(), TreeTabsConfig.barAtBottom);
	}

	public static int barY(Screen screen) {
		return layout(screen).barY;
	}

	/** True when the pointer is anywhere over the strip, including its background. */
	public static boolean isOver(Screen screen, double mouseX, double mouseY) {
		return visible() && layout(screen).isOver(mouseX, mouseY);
	}

	/** Scrolls just far enough that the given tab is fully on screen. */
	public static void ensureVisible(Screen screen, int index) {
		if (index < 0) {
			return;
		}
		scroll = layout(screen).ensureVisible(index, scroll);
	}

	// --------------------------------------------------------------- drawing

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (!visible()) {
			return;
		}
		TreeTabs.syncActiveCraftingMode();

		TabLayout l = layout(screen);
		scroll = l.clampScroll(scroll);

		Font font = Minecraft.getInstance().font;
		int y = l.barY;
		int hovered = l.tabAt(mouseX, mouseY, scroll);

		// Only the tabs actually on screen need their progress costed.
		TreeTabs.pollProgress(l.firstVisible(scroll), l.lastVisible(scroll));

		graphics.fill(0, y, screen.width, y + HEIGHT, COLOR_BAR);
		int borderY = l.barAtBottom ? y : y + HEIGHT - 1;
		graphics.fill(0, borderY, screen.width, borderY + 1, COLOR_BORDER);

		graphics.enableScissor(l.stripLeft, y, l.stripLeft + l.stripWidth, y + HEIGHT);
		for (int i = 0; i < l.count; i++) {
			if (dragging && i == dragIndex) {
				continue;
			}
			int x = l.tabX(i, scroll);
			if (x + l.tabWidth < l.stripLeft || x > l.stripLeft + l.stripWidth) {
				continue;
			}
			drawTab(l, graphics, font, i, x, i == hovered, mouseX, mouseY);
		}
		graphics.disableScissor();

		if (dragging && dragIndex >= 0) {
			drawTab(l, graphics, font, dragIndex, (int) (dragX - l.tabWidth / 2.0), true, -1, -1);
		}

		drawArrows(l, graphics, font, mouseX, mouseY);
		drawAllButton(l, graphics, font, mouseX, mouseY);
		drawAddButton(l, graphics, font, mouseX, mouseY);

		if (renameBox != null) {
			renameBox.render(graphics, mouseX, mouseY, delta);
		} else if (hovered >= 0) {
			drawTooltip(graphics, font, hovered, mouseX, mouseY);
		} else if (l.overAllButton(mouseX, mouseY)) {
			int crafting = TreeTabs.craftingCount();
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("emi.tree_tabs.all.title"),
					Component.translatable("emi.tree_tabs.all.state", crafting, TreeTabs.count())
							.withStyle(ChatFormatting.GRAY),
					Component.translatable("emi.tree_tabs.all.hint").withStyle(ChatFormatting.DARK_GRAY)),
					mouseX, mouseY);
		} else if (l.overAddButton(mouseX, mouseY) && TreeTabs.activeTab() != null) {
			graphics.renderComponentTooltip(font, List.of(
					Component.translatable("emi.tree_tabs.fork"),
					Component.translatable("emi.tree_tabs.fork.desc").withStyle(ChatFormatting.GRAY)),
					mouseX, mouseY);
		}
	}

	private static void drawTab(TabLayout l, GuiGraphics graphics, Font font, int index,
			int x, boolean hovered, int mouseX, int mouseY) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null) {
			return;
		}
		int y = l.barY;
		int width = l.tabWidth;
		boolean isActive = index == TreeTabs.activeIndex();
		int background = isActive ? COLOR_TAB_ACTIVE : hovered ? COLOR_TAB_HOVER : COLOR_TAB;
		graphics.fill(x, y + 1, x + width - 1, y + HEIGHT - 1, background);

		int accent = accentColor(tab);
		if (isActive) {
			int accentY = l.barAtBottom ? y + HEIGHT - 3 : y + 1;
			graphics.fill(x, accentY, x + width - 1, accentY + 2, accent);
		} else {
			int accentY = l.barAtBottom ? y + 1 : y + HEIGHT - 3;
			graphics.fill(x, accentY, x + width - 1, accentY + 1, accent);
			// Hairline between inactive neighbours; the active tab reads on its own.
			graphics.fill(x + width - 1, y + 4, x + width, y + HEIGHT - 4, COLOR_DIVIDER);
		}

		// At icon density the tab is barely wider than the icon, so centre it rather than letting
		// it sit against the left edge with dead space to its right.
		int iconX = l.density == Density.ICON ? x + (width - ICON_SIZE) / 2 : x + 4;
		EmiIngredient icon = tab.icon();
		if (!icon.isEmpty()) {
			icon.render(graphics, iconX, y + 3, 0f, EmiIngredient.RENDER_ICON);
			if (tab.craftingMode) {
				// A pip on the icon's corner: this tree is being built, not just read.
				graphics.fill(iconX - 1 + ICON_SIZE - 4, y + 2 + ICON_SIZE - 4,
						iconX - 1 + ICON_SIZE + 1, y + 2 + ICON_SIZE + 1, 0xFF000000);
				graphics.fill(iconX - 1 + ICON_SIZE - 3, y + 2 + ICON_SIZE - 3,
						iconX - 1 + ICON_SIZE, y + 2 + ICON_SIZE, COLOR_CRAFTING);
			}
		}

		boolean closeShown = l.closeVisible(hovered, isActive);
		int budget = l.labelBudget(closeShown);
		if (budget > 4) {
			graphics.drawString(font, tab.trimmedLabel(font, budget), x + 4 + ICON_SIZE + 3, y + 7,
					isActive ? COLOR_TEXT : COLOR_TEXT_DIM, false);
		}

		if (closeShown) {
			TabLayout.Rect r = l.closeRect(index, scroll, hovered, isActive);
			// The drag ghost is drawn away from its real position, so shift the target with it.
			int rx = r.x() + (x - l.tabX(index, scroll));
			boolean closeHovered = mouseX >= rx && mouseX < rx + r.width()
					&& mouseY >= r.y() && mouseY < r.y() + r.height();
			int colour = closeHovered ? COLOR_CLOSE_HOVER : COLOR_TEXT_DIM;
			if (l.density == Density.ICON) {
				// A badge, not an inline button: the rest of the tab has to stay selectable.
				graphics.fill(rx, r.y(), rx + r.width(), r.y() + r.height(), 0xC0101014);
				graphics.drawString(font, "×", rx + 2, r.y() + 1, colour, false);
			} else {
				graphics.drawString(font, "×", rx + 2, r.y() + 2, colour, false);
			}
		}
	}

	/** Scroll affordances, so a long strip does not look like it simply ends. */
	private static void drawArrows(TabLayout l, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		if (!l.scrolling) {
			return;
		}
		int y = l.barY;
		boolean canLeft = scroll > 0.5;
		boolean canRight = scroll < l.maxScroll - 0.5;
		graphics.drawString(font, "◀", l.leftArrowX() + 1, y + 7,
				canLeft ? (l.overLeftArrow(mouseX, mouseY) ? COLOR_TEXT : COLOR_TEXT_DIM) : 0xFF3A3A40, false);
		graphics.drawString(font, "▶", l.rightArrowX() + 1, y + 7,
				canRight ? (l.overRightArrow(mouseX, mouseY) ? COLOR_TEXT : COLOR_TEXT_DIM) : 0xFF3A3A40, false);
	}

	/**
	 * Switches every tab between viewing and crafting.
	 *
	 * <p>Shows how many are being crafted, because with a dozen tabs the state is otherwise only
	 * legible by scanning every icon for its corner pip.
	 */
	private static void drawAllButton(TabLayout l, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		if (l.count == 0) {
			return;
		}
		int x = l.allButtonX();
		int y = l.barY;
		boolean hovered = l.overAllButton(mouseX, mouseY);
		int crafting = TreeTabs.craftingCount();
		graphics.fill(x, y + 1, x + TabLayout.ALL_BUTTON_WIDTH, y + HEIGHT - 1,
				hovered ? COLOR_TAB_HOVER : COLOR_TAB);
		int colour = crafting == 0 ? COLOR_TEXT_DIM : COLOR_CRAFTING;
		graphics.drawString(font, "≡", x + 3, y + 7, colour, false);
		if (crafting > 0) {
			graphics.fill(x + TabLayout.ALL_BUTTON_WIDTH - 5, y + 4,
					x + TabLayout.ALL_BUTTON_WIDTH - 2, y + 7, COLOR_CRAFTING);
		}
	}

	private static void drawAddButton(TabLayout l, GuiGraphics graphics, Font font, int mouseX, int mouseY) {
		if (TreeTabs.activeTab() == null) {
			return;
		}
		int x = l.addButtonX();
		int y = l.barY;
		boolean hovered = l.overAddButton(mouseX, mouseY);
		graphics.fill(x, y + 1, x + TabLayout.ADD_BUTTON_WIDTH, y + HEIGHT - 1,
				hovered ? COLOR_TAB_HOVER : COLOR_TAB);
		graphics.drawString(font, "+", x + TabLayout.ADD_BUTTON_WIDTH / 2 - 2, y + 7,
				hovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
	}

	private static void drawTooltip(GuiGraphics graphics, Font font, int index, int mouseX, int mouseY) {
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
		TabLayout l = layout(screen);
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}

		if (button == 0 && l.overLeftArrow(mouseX, mouseY)) {
			scroll = l.clampScroll(scroll - l.tabWidth);
			return true;
		}
		if (button == 0 && l.overRightArrow(mouseX, mouseY)) {
			scroll = l.clampScroll(scroll + l.tabWidth);
			return true;
		}
		if (button == 0 && l.overAllButton(mouseX, mouseY)) {
			click();
			TreeTabs.toggleAllCrafting();
			return true;
		}
		if (button == 0 && l.overAddButton(mouseX, mouseY) && TreeTabs.activeTab() != null) {
			click();
			TreeTabs.duplicate(TreeTabs.activeIndex());
			ensureVisible(screen, TreeTabs.activeIndex());
			return true;
		}

		int index = l.tabAt(mouseX, mouseY, scroll);
		if (index < 0) {
			// Swallow clicks on empty strip so they do not pan the tree underneath.
			return true;
		}

		boolean isActive = index == TreeTabs.activeIndex();
		if (button == 2 || (button == 0 && l.overClose(index, mouseX, mouseY, scroll, true, isActive))) {
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
			int target = layout(screen).dropTarget(dragX, scroll);
			if (target != dragIndex) {
				TreeTabs.move(dragIndex, target);
				ensureVisible(screen, TreeTabs.activeIndex());
			}
		}
		dragIndex = -1;
		dragging = false;
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		if (!visible()) {
			return false;
		}
		TabLayout l = layout(screen);
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}
		if (renameBox != null || l.maxScroll <= 0) {
			// Nothing to scroll, but still swallow it so the tree does not zoom under the bar.
			return true;
		}
		scroll = l.clampScroll(scroll - amount * l.tabWidth / 2.0);
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

		TabLayout l = layout(screen);
		int x = l.tabX(index, scroll);
		// At icon density a tab is far too narrow to type in, so the box borrows room from its
		// neighbours rather than being unusably small.
		int boxWidth = Math.max(80, l.tabWidth - 4);
		x = Math.min(x + 2, l.stripLeft + l.stripWidth - boxWidth);
		x = Math.max(x, l.stripLeft);
		renameBox = new EditBox(Minecraft.getInstance().font, x, l.barY + 3, boxWidth, 16,
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
