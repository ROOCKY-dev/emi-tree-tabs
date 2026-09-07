package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.TabGroup;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowKind;
import dev.roocky.emitreetabs.ui.SidebarLayout.Slot;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.ProgressState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The vertical tab panel: a floating sidebar over EMI's tree screen.
 *
 * <p>All of the arithmetic lives in {@link SidebarLayout} and all of the ordering in
 * {@link SidebarRows}; this class draws what they describe and turns clicks back into tab
 * operations. Both of those are covered by tests, which is why this file can stay mostly literal.
 */
public final class TreeSidebar {

	private static final int COLOR_PANEL = 0xF012121A;
	private static final int COLOR_PANEL_HI = 0xFF34343F;
	private static final int COLOR_PANEL_LO = 0xFF05050A;
	private static final int COLOR_ROW = 0xFF1B1B20;
	private static final int COLOR_ROW_HOVER = 0xFF262630;
	private static final int COLOR_ROW_ACTIVE = 0xFF313142;
	private static final int COLOR_GROUP = 0xFF23232E;
	private static final int COLOR_GROUP_HOVER = 0xFF2C2C3A;
	private static final int COLOR_BORDER = 0xFF33333F;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_TEXT_DIM = 0xFF9A9AA2;
	private static final int COLOR_ACCENT = 0xFF5A8CFF;
	private static final int COLOR_CRAFTING = 0xFF48C8E0;
	private static final int COLOR_MARKER_BG = 0xFF15151C;
	private static final int COLOR_PARKED = 0x66000000;

	private static double scroll;

	private TreeSidebar() {
	}

	/** Whether the sidebar is the layout in use right now. */
	public static boolean active(Screen screen) {
		if (!TreeTabsConfig.enabled || TreeTabs.count() == 0) {
			return false;
		}
		String mode = TreeTabsConfig.tabOrientation;
		if ("horizontal".equalsIgnoreCase(mode)) {
			return false;
		}
		if ("vertical".equalsIgnoreCase(mode)) {
			// Even when asked for explicitly, a screen too small to draw it is still too small.
			return SidebarLayout.fits(screen.width, screen.height);
		}
		return SidebarLayout.fits(screen.width, screen.height);
	}

	private static SidebarLayout layout(Screen screen, SidebarRows.Result rows) {
		return new SidebarLayout(screen.width, screen.height, rows.rows(), scroll,
				!TreeTabsConfig.barAtBottom);
	}

	public static boolean isOver(Screen screen, double mouseX, double mouseY) {
		if (!active(screen)) {
			return false;
		}
		return layout(screen, TreeTabs.sidebarRows()).isOver(mouseX, mouseY);
	}

	// --------------------------------------------------------------- drawing

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (!active(screen)) {
			return;
		}
		TreeTabs.syncActiveCraftingMode();

		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		scroll = l.clampScroll(scroll);

		Font font = Minecraft.getInstance().font;
		Slot hovered = l.slotAt(mouseX, mouseY);

		// Only cost the trees that are actually on screen.
		int first = Integer.MAX_VALUE;
		int last = -1;
		for (Slot s : l.visibleSlots()) {
			int tab = rows.tabAt(s.index());
			if (tab >= 0) {
				first = Math.min(first, tab);
				last = Math.max(last, tab);
			}
		}
		if (last >= 0) {
			TreeTabs.pollProgress(first, last);
		}

		panel(graphics, l.panel);

		graphics.enableScissor(l.viewport.x(), l.viewport.y(),
				l.viewport.x() + l.viewport.width(), l.viewport.y() + l.viewport.height());

		// Group boxes first, so rows draw on top of their own enclosing border.
		for (TabGroup g : TreeTabs.groups()) {
			Rect box = l.groupBounds(g.id);
			if (box != null) {
				outline(graphics, box, g.colour);
			}
		}
		for (Slot s : l.visibleSlots()) {
			if (s.row().kind() == RowKind.GROUP) {
				drawGroup(graphics, font, l, s, hovered == s, mouseX, mouseY);
			} else {
				drawTab(graphics, font, l, s, rows.tabAt(s.index()), hovered == s, mouseX, mouseY);
			}
		}
		graphics.disableScissor();

		drawFooter(graphics, font, l, mouseX, mouseY);

		if (hovered != null) {
			tooltip(graphics, font, l, rows, hovered, mouseX, mouseY);
		}
	}

	private static void panel(GuiGraphics g, Rect r) {
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), COLOR_PANEL);
		// Vanilla's bevel: light on the top and left, dark on the bottom and right.
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, COLOR_PANEL_HI);
		g.fill(r.x(), r.y(), r.x() + 1, r.y() + r.height(), COLOR_PANEL_HI);
		g.fill(r.x(), r.y() + r.height() - 1, r.x() + r.width(), r.y() + r.height(), COLOR_PANEL_LO);
		g.fill(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), COLOR_PANEL_LO);
	}

	private static void outline(GuiGraphics g, Rect r, int colour) {
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, colour);
		g.fill(r.x(), r.y() + r.height() - 1, r.x() + r.width(), r.y() + r.height(), colour);
		g.fill(r.x(), r.y(), r.x() + 1, r.y() + r.height(), colour);
		g.fill(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), colour);
	}

	private static void drawTab(GuiGraphics g, Font font, SidebarLayout l, Slot s, int tabIndex,
			boolean hovered, int mouseX, int mouseY) {
		TreeTab tab = TreeTabs.tab(tabIndex);
		if (tab == null) {
			return;
		}
		Rect b = s.bounds();
		boolean isActive = tabIndex == TreeTabs.activeIndex();
		g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(),
				isActive ? COLOR_ROW_ACTIVE : hovered ? COLOR_ROW_HOVER : COLOR_ROW);

		// State on the border, not a corner pip: down a column a stripe reads at a glance.
		int state = isActive ? COLOR_ACCENT : progressColour(tab);
		g.fill(b.x(), b.y(), b.x() + 3, b.y() + b.height(), state);
		if (isActive) {
			outline(g, b, COLOR_ACCENT);
		}

		int iconX = b.x() + SidebarLayout.ROW_PAD + 2;
		EmiIngredient icon = tab.icon();
		if (!icon.isEmpty()) {
			icon.render(g, iconX, b.y() + (b.height() - SidebarLayout.ICON) / 2, 0f,
					EmiIngredient.RENDER_ICON);
		}

		int budget = l.labelBudget(s);
		if (budget > 6) {
			g.drawString(font, tab.trimmedLabel(font, budget),
					iconX + SidebarLayout.ICON + 5, b.y() + (b.height() - 8) / 2,
					isActive ? COLOR_TEXT : COLOR_TEXT_DIM, false);
		}

		marker(g, font, s.marker(), String.valueOf(tab.batches()), tab.craftingMode,
				hovered && l.overMarker(s, mouseX, mouseY));

		// A parked group's trees are dimmed, because they have stopped asking for materials.
		if (TreeTabs.isParked(tab)) {
			g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), COLOR_PARKED);
		}
	}

	private static void drawGroup(GuiGraphics g, Font font, SidebarLayout l, Slot s,
			boolean hovered, int mouseX, int mouseY) {
		TabGroup group = TreeTabs.group(s.row().groupIndex());
		if (group == null) {
			return;
		}
		Rect b = s.bounds();
		// The header fills the panel width as its own background — that is what separates it from a
		// tab, which only has a border.
		g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(),
				hovered ? COLOR_GROUP_HOVER : COLOR_GROUP);
		g.fill(b.x(), b.y(), b.x() + 3, b.y() + b.height(), group.colour);
		g.fill(b.x(), b.y() + b.height() - 1, b.x() + b.width(), b.y() + b.height(), COLOR_BORDER);

		Rect c = s.collapse();
		g.drawString(font, group.collapsed ? "▶" : "▼", c.x(), c.y() + 1,
				l.overCollapse(s, mouseX, mouseY) ? COLOR_TEXT : COLOR_TEXT_DIM, false);

		int budget = l.labelBudget(s);
		if (budget > 6) {
			g.drawString(font, trim(font, group.name, budget),
					c.x() + SidebarLayout.COLLAPSE + 4, b.y() + (b.height() - 8) / 2,
					group.parked ? COLOR_TEXT_DIM : COLOR_TEXT, false);
		}

		int size = TreeTabs.groupSize(group.id);
		boolean allCrafting = size > 0 && TreeTabs.groupCraftingCount(group.id) == size;
		marker(g, font, s.marker(), String.valueOf(size), allCrafting,
				hovered && l.overMarker(s, mouseX, mouseY));
	}

	/** The square at a row's right end: a count at rest, the crafting toggle under the cursor. */
	private static void marker(GuiGraphics g, Font font, Rect r, String count,
			boolean crafting, boolean hovered) {
		boolean lit = crafting || hovered;
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(),
				lit ? COLOR_CRAFTING : COLOR_MARKER_BG);
		outline(g, r, lit ? 0xFF7FE0F0 : 0xFF2E2E3A);
		String label = hovered ? "✦" : count;
		int w = font.width(label);
		g.drawString(font, label, r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
				lit ? 0xFF0C0C10 : COLOR_TEXT_DIM, false);
	}

	private static void drawFooter(GuiGraphics g, Font font, SidebarLayout l, int mouseX, int mouseY) {
		Rect all = l.craftAllButton();
		// No settings screen installed means no button, rather than a button that does nothing.
		if (ConfigScreenHook.available()) {
			Rect settings = l.settingsButton();
			button(g, font, settings, "⚙", settings.contains(mouseX, mouseY), false);
		}
		button(g, font, all, "≡", all.contains(mouseX, mouseY), TreeTabs.craftingCount() > 0);
	}

	private static void button(GuiGraphics g, Font font, Rect r, String glyph,
			boolean hovered, boolean lit) {
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(),
				lit ? COLOR_CRAFTING : hovered ? COLOR_ROW_HOVER : COLOR_ROW);
		outline(g, r, 0xFF2E2E3A);
		int w = font.width(glyph);
		g.drawString(font, glyph, r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
				lit ? 0xFF0C0C10 : hovered ? COLOR_TEXT : COLOR_TEXT_DIM, false);
	}

	private static void tooltip(GuiGraphics g, Font font, SidebarLayout l, SidebarRows.Result rows,
			Slot s, int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		if (s.row().kind() == RowKind.GROUP) {
			TabGroup group = TreeTabs.group(s.row().groupIndex());
			if (group == null) {
				return;
			}
			lines.add(Component.literal(group.name));
			lines.add(Component.translatable("emi.tree_tabs.group.size",
					TreeTabs.groupSize(group.id)).withStyle(ChatFormatting.GRAY));
			if (group.parked) {
				lines.add(Component.translatable("emi.tree_tabs.group.parked")
						.withStyle(ChatFormatting.GOLD));
			}
			lines.add(Component.translatable(group.parked
					? "emi.tree_tabs.group.unpark" : "emi.tree_tabs.group.park")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.group.only")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else {
			TreeTab tab = TreeTabs.tab(rows.tabAt(s.index()));
			if (tab == null) {
				return;
			}
			lines.add(tab.displayName());
			lines.add(Component.translatable("emi.tree_tabs.batches", tab.batches())
					.withStyle(ChatFormatting.GRAY));
			if (TreeTabsConfig.showProgress) {
				lines.add(progressText(tab.progress));
			}
			if (TreeTabs.isParked(tab)) {
				lines.add(Component.translatable("emi.tree_tabs.group.parked")
						.withStyle(ChatFormatting.GOLD));
			}
		}
		g.renderComponentTooltip(font, lines, mouseX, mouseY);
	}

	// ----------------------------------------------------------------- input

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (!active(screen)) {
			return false;
		}
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}

		if (button == 0 && l.craftAllButton().contains(mouseX, mouseY)) {
			click();
			TreeTabs.toggleAllCrafting();
			return true;
		}
		if (button == 0 && ConfigScreenHook.available() && l.settingsButton().contains(mouseX, mouseY)) {
			click();
			ConfigScreenHook.open(screen);
			return true;
		}

		Slot s = l.slotAt(mouseX, mouseY);
		if (s == null) {
			// Swallow clicks on the panel's own background so the tree does not pan underneath.
			return true;
		}

		if (s.row().kind() == RowKind.GROUP) {
			int id = s.row().groupIndex();
			TabGroup group = TreeTabs.group(id);
			if (group == null) {
				return true;
			}
			if (button == 0 && l.overMarker(s, mouseX, mouseY)) {
				click();
				TreeTabs.setGroupCrafting(id, TreeTabs.groupCraftingCount(id) < TreeTabs.groupSize(id));
				return true;
			}
			if (button == 1) {
				click();
				// Right click parks. Shift right click works on this phase alone, setting the rest
				// aside, which is the whole workflow in one gesture.
				if (Screen.hasShiftDown()) {
					TreeTabs.parkAllExcept(id);
				} else {
					TreeTabs.setParked(id, !group.parked);
				}
				return true;
			}
			if (button == 0) {
				click();
				TreeTabs.setCollapsed(id, !group.collapsed);
				return true;
			}
			return true;
		}

		int tabIndex = rows.tabAt(s.index());
		if (tabIndex < 0) {
			return true;
		}
		if (button == 0 && l.overMarker(s, mouseX, mouseY)) {
			click();
			// The marker shows the batch count, so control-clicking it edits that number, while a
			// plain click toggles crafting. One square, and the modifier acts on what it displays.
			if (Screen.hasControlDown()) {
				Rect m = s.marker();
				BatchInput.open(screen, tabIndex, m.x() + m.width() + 4, m.y());
			} else {
				TreeTabs.toggleCrafting(tabIndex);
			}
			return true;
		}
		if (button == 1) {
			click();
			TreeTabs.select(tabIndex);
			return true;
		}
		if (button == 2) {
			click();
			TreeTabs.close(tabIndex);
			return true;
		}
		if (button == 0) {
			click();
			TreeTabs.select(tabIndex);
			return true;
		}
		return true;
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		if (!active(screen)) {
			return false;
		}
		SidebarLayout l = layout(screen, TreeTabs.sidebarRows());
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}
		if (l.maxScroll <= 0) {
			// Nothing to scroll, but swallow it so the tree does not zoom under the panel.
			return true;
		}
		scroll = l.clampScroll(scroll - amount * SidebarLayout.ROW_HEIGHT);
		return true;
	}

	/** Scrolls just far enough that the given tab is on screen. */
	public static void ensureVisible(Screen screen, int tabIndex) {
		if (!active(screen) || tabIndex < 0) {
			return;
		}
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		int row = rows.rowOf(tabIndex);
		if (row < 0) {
			return;
		}
		for (Slot s : l.slots()) {
			if (s.index() != row) {
				continue;
			}
			Rect b = s.bounds();
			if (b.y() < l.viewport.y()) {
				scroll = l.clampScroll(scroll - (l.viewport.y() - b.y()));
			} else if (b.y() + b.height() > l.viewport.y() + l.viewport.height()) {
				scroll = l.clampScroll(scroll
						+ (b.y() + b.height() - (l.viewport.y() + l.viewport.height())));
			}
			return;
		}
	}

	public static void reset() {
		scroll = 0;
	}

	// ---------------------------------------------------------------- shared

	private static String trim(Font font, String text, int budget) {
		if (font.width(text) <= budget) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, budget - font.width(".."))) + "..";
	}

	private static int progressColour(TreeTab tab) {
		if (!TreeTabsConfig.showProgress || tab.progress == null) {
			return 0xFF6E6E76;
		}
		if (tab.progress == ProgressState.COMPLETED) {
			return 0xFF5BD16A;
		}
		if (tab.progress == ProgressState.PARTIAL) {
			return 0xFFE0A63C;
		}
		return 0xFF6E6E76;
	}

	private static Component progressText(ProgressState state) {
		if (state == ProgressState.COMPLETED) {
			return Component.translatable("emi.tree_tabs.progress.complete")
					.withStyle(ChatFormatting.GREEN);
		}
		if (state == ProgressState.PARTIAL) {
			return Component.translatable("emi.tree_tabs.progress.partial")
					.withStyle(ChatFormatting.GOLD);
		}
		return Component.translatable("emi.tree_tabs.progress.unstarted")
				.withStyle(ChatFormatting.GRAY);
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
