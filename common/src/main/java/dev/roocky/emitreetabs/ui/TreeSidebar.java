package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.RecipeChoices;
import dev.roocky.emitreetabs.tab.TabGroup;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowKind;
import dev.roocky.emitreetabs.ui.SidebarLayout.Slot;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.ProgressState;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

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

	private static double scroll;

	/** How far the pointer must travel before a click on a row becomes a drag. */
	private static final int DRAG_SLOP = 4;

	private static int choiceCount;
	private static long choiceCountAt;

	private static int dragTab = -1;
	private static double dragOriginY;
	private static double dragY;
	private static boolean dragging;

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
		if (dragging) {
			drawDropTarget(graphics, l, rows);
		}
		graphics.disableScissor();

		drawFooter(graphics, l, mouseX, mouseY);

		if (hovered != null) {
			tooltip(screen, graphics, font, l, rows, hovered, mouseX, mouseY);
		} else {
			footerTooltip(screen, graphics, font, l, mouseX, mouseY);
		}
	}

	/**
	 * Marks the row a dragged tab would land on.
	 *
	 * <p>A line between rows would be the browser answer, but the sidebar's drop rule is "take that
	 * row's place" rather than "go in that gap" — a line would be promising something else. So the
	 * target row is outlined, and a group header is outlined the same way to say the tab joins it.
	 */
	private static void drawDropTarget(GuiGraphics g, SidebarLayout l, SidebarRows.Result rows) {
		int row = rowAtY(l, dragY);
		if (SidebarDrag.resolve(rows, dragTab, row, TreeTabs.count()) == null) {
			return;
		}
		for (Slot s : l.visibleSlots()) {
			if (s.index() == row) {
				outline(g, s.bounds(), TabPalette.ACCENT);
				return;
			}
		}
		// Past the last row: the drop leaves every group, so mark the space it would land in.
		Slot last = null;
		for (Slot s : l.visibleSlots()) {
			last = s;
		}
		int y = last == null ? l.viewport.y()
				: Math.min(last.bounds().y() + last.bounds().height() + SidebarLayout.ROW_GAP,
						l.viewport.y() + l.viewport.height() - 2);
		g.fill(l.viewport.x(), y, l.viewport.x() + l.viewport.width(), y + 2, TabPalette.ACCENT);
	}

	/**
	 * The row under a y coordinate, or {@code rows.size()} for the empty space past the last one.
	 *
	 * <p>By y alone, not by the slot's rectangle: a drag is allowed to wander outside the panel
	 * horizontally, and cancelling the drop because the pointer drifted sideways would be a worse
	 * answer than landing it where the pointer's height says.
	 */
	private static int rowAtY(SidebarLayout l, double y) {
		for (Slot s : l.visibleSlots()) {
			if (y >= s.bounds().y() && y < s.bounds().y() + s.bounds().height()) {
				return s.index();
			}
		}
		return Integer.MAX_VALUE;
	}

	/**
	 * What the corner buttons do.
	 *
	 * <p>Worth the lines: a button with a drawn icon and no tooltip is a button you have to click
	 * to find out about, and one of these creates something.
	 */
	private static void footerTooltip(Screen screen, GuiGraphics g, Font font, SidebarLayout l,
			int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		if (TreeTabs.count() > 0 && l.footerFits(l.newGroupButton())
				&& l.newGroupButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.group.new"));
			lines.add(Component.translatable("emi.tree_tabs.group.new.desc")
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("emi.tree_tabs.group.new.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (l.craftAllButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.all.title"));
			lines.add(Component.translatable("emi.tree_tabs.all.state",
					TreeTabs.craftingCount(), TreeTabs.count()).withStyle(ChatFormatting.GRAY));
		} else if (choiceCount() > 0 && l.footerFits(l.choicesButton())
				&& l.choicesButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.choices.button"));
			lines.add(Component.translatable("emi.tree_tabs.choices.button.desc", choiceCount())
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("emi.tree_tabs.choices.button.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (ConfigScreenHook.available() && l.settingsButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.config.title"));
		} else {
			return;
		}
		TabTooltip.render(g, font, lines, screen.width, screen.height,
				new TabTooltip.Rect(l.panel.x(), l.panel.y(), l.panel.width(), l.panel.height()),
				l.onLeft ? l.panel.x() + l.panel.width() + 60 : l.panel.x() - 60,
				mouseX, mouseY);
	}

	/**
	 * How many ingredients the open trees disagree about.
	 *
	 * <p>Cached for a second: the scan walks every tree and this is asked once a frame to decide
	 * whether to draw a button. A second is short enough that a change you just made shows up
	 * before you look for it, and long enough that the walk is not the frame's problem.
	 */
	private static int choiceCount() {
		long now = System.currentTimeMillis();
		if (now - choiceCountAt > 1000) {
			choiceCountAt = now;
			choiceCount = RecipeChoices.scan().size();
		}
		return choiceCount;
	}

	private static void panel(GuiGraphics g, Rect r) {
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), TabPalette.PANEL);
		// Vanilla's bevel: light on the top and left, dark on the bottom and right.
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, TabPalette.PANEL_HI);
		g.fill(r.x(), r.y(), r.x() + 1, r.y() + r.height(), TabPalette.PANEL_HI);
		g.fill(r.x(), r.y() + r.height() - 1, r.x() + r.width(), r.y() + r.height(), TabPalette.PANEL_LO);
		g.fill(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), TabPalette.PANEL_LO);
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
				isActive ? TabPalette.TAB_ACTIVE : hovered ? TabPalette.TAB_HOVER : TabPalette.TAB);

		// State on the border, not a corner pip: down a column a stripe reads at a glance.
		int state = isActive ? TabPalette.ACCENT : progressColour(tab);
		g.fill(b.x(), b.y(), b.x() + 3, b.y() + b.height(), state);
		if (isActive) {
			outline(g, b, TabPalette.ACCENT);
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
					isActive ? TabPalette.TEXT : TabPalette.TEXT_DIM, false);
		}

		marker(g, font, s.marker(), String.valueOf(tab.batches()), tab.craftingMode,
				hovered && l.overMarker(s, mouseX, mouseY));

		// A parked group's trees are dimmed, because they have stopped asking for materials.
		if (TreeTabs.isParked(tab)) {
			g.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), TabPalette.PARKED);
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
				hovered ? TabPalette.GROUP_HOVER : TabPalette.GROUP);
		g.fill(b.x(), b.y(), b.x() + 3, b.y() + b.height(), group.colour);
		g.fill(b.x(), b.y() + b.height() - 1, b.x() + b.width(), b.y() + b.height(), TabPalette.BORDER);

		Rect c = s.collapse();
		Icons.centred(g, group.collapsed ? Icons.CARET_RIGHT : Icons.CARET_DOWN,
				c.x(), c.y(), c.width(), c.height(),
				l.overCollapse(s, mouseX, mouseY) ? TabPalette.TEXT : TabPalette.TEXT_DIM);

		int budget = l.labelBudget(s);
		if (budget > 6 && !GroupName.editing(group.id)) {
			g.drawString(font, trim(font, group.name, budget),
					c.x() + SidebarLayout.COLLAPSE + 4, b.y() + (b.height() - 8) / 2,
					group.parked ? TabPalette.TEXT_DIM : TabPalette.TEXT, false);
		}

		int size = TreeTabs.groupSize(group.id);
		boolean allCrafting = size > 0 && TreeTabs.groupCraftingCount(group.id) == size;
		Rect m = s.marker();
		// Parking used to show only as dimmed text, which reads as "disabled" rather than "set
		// aside for now" - and not at all on a header whose name was too long to draw.
		if (group.parked) {
			Icons.draw(g, Icons.PARK, m.x() - Icons.PARK.width() - 3,
					m.y() + (m.height() - Icons.PARK.height()) / 2, TabPalette.TEXT_DIM);
		}
		marker(g, font, m, String.valueOf(size), allCrafting,
				hovered && l.overMarker(s, mouseX, mouseY));
	}

	/** The square at a row's right end: a count at rest, the crafting toggle under the cursor. */
	private static void marker(GuiGraphics g, Font font, Rect r, String count,
			boolean crafting, boolean hovered) {
		boolean lit = crafting || hovered;
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(),
				lit ? TabPalette.CRAFTING : TabPalette.MARKER_BG);
		outline(g, r, lit ? TabPalette.CRAFTING_EDGE : TabPalette.OUTLINE);
		int ink = lit ? TabPalette.INK_ON_LIT : TabPalette.TEXT_DIM;
		if (hovered) {
			// The number is only information until you reach for it; then the same square is the
			// control, and says so with the mark the craft-all button uses.
			Icons.centred(g, Icons.CRAFT_ONE, r.x(), r.y(), r.width(), r.height(), ink);
		} else {
			int w = font.width(count);
			g.drawString(font, count, r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
					ink, false);
		}
	}

	private static void drawFooter(GuiGraphics g, SidebarLayout l, int mouseX, int mouseY) {
		Rect all = l.craftAllButton();
		// No settings screen installed means no button, rather than a button that does nothing.
		if (ConfigScreenHook.available()) {
			Rect settings = l.settingsButton();
			button(g, settings, Icons.SETTINGS, settings.contains(mouseX, mouseY), false);
		}
		if (TreeTabs.count() > 0 && l.footerFits(l.newGroupButton())) {
			Rect group = l.newGroupButton();
			button(g, group, Icons.NEW_GROUP, group.contains(mouseX, mouseY), false);
		}
		// Lit, because it is a thing to attend to rather than a thing to do - and absent entirely
		// when the trees agree, which is most of the time.
		if (choiceCount() > 0 && l.footerFits(l.choicesButton())) {
			Rect choices = l.choicesButton();
			button(g, choices, Icons.CHOICES, choices.contains(mouseX, mouseY), true);
		}
		button(g, all, Icons.CRAFT_ALL, all.contains(mouseX, mouseY), TreeTabs.craftingCount() > 0);
	}

	private static void button(GuiGraphics g, Rect r, Icons.Sprite icon,
			boolean hovered, boolean lit) {
		g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(),
				lit ? TabPalette.CRAFTING : hovered ? TabPalette.TAB_HOVER : TabPalette.TAB);
		outline(g, r, TabPalette.OUTLINE);
		Icons.centred(g, icon, r.x(), r.y(), r.width(), r.height(),
				lit ? TabPalette.INK_ON_LIT : hovered ? TabPalette.TEXT : TabPalette.TEXT_DIM);
	}

	private static void tooltip(Screen screen, GuiGraphics g, Font font, SidebarLayout l,
			SidebarRows.Result rows, Slot s, int mouseX, int mouseY) {
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
			lines.add(Component.translatable("emi.tree_tabs.group.rename.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.group.delete")
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
		// Anchored beside the panel, never over it: the sidebar occupies a whole screen edge, so a
		// cursor-relative tooltip would sit on top of the rows it is describing.
		TabTooltip.render(g, font, lines, screen.width, screen.height,
				new TabTooltip.Rect(l.panel.x(), l.panel.y(), l.panel.width(), l.panel.height()),
				l.onLeft ? l.panel.x() + l.panel.width() + 60 : l.panel.x() - 60,
				mouseX, mouseY);
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
		if (button == 0 && TreeTabs.count() > 0 && l.footerFits(l.newGroupButton())
				&& l.newGroupButton().contains(mouseX, mouseY)) {
			click();
			newGroup(screen);
			return true;
		}
		if (button == 0 && choiceCount() > 0 && l.footerFits(l.choicesButton())
				&& l.choicesButton().contains(mouseX, mouseY)) {
			click();
			openChoices(screen);
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
			// Middle click drops the phase, matching middle click closing a tab. Its trees stay
			// open and simply become ungrouped - a phase is a label on trees, not a box holding
			// them, so deleting it must not take them with it.
			if (button == 2) {
				click();
				GroupName.close(screen);
				TreeTabs.removeGroup(id);
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
			dragTab = tabIndex;
			dragOriginY = mouseY;
			dragY = mouseY;
			dragging = false;
			return true;
		}
		return true;
	}

	public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
		if (!active(screen) || dragTab < 0 || button != 0) {
			return false;
		}
		dragY = mouseY;
		if (!dragging && Math.abs(mouseY - dragOriginY) > DRAG_SLOP) {
			dragging = true;
		}
		return true;
	}

	/**
	 * Finishes a drag once the button comes back up.
	 *
	 * <p>Polled rather than handled, for the reason the strip polls: {@code BoMScreen} does not
	 * override {@code mouseReleased}, so there is nothing to inject into.
	 */
	public static void tickDrag(Screen screen) {
		if (dragTab < 0) {
			return;
		}
		if (!active(screen)) {
			dragTab = -1;
			dragging = false;
			return;
		}
		long window = Minecraft.getInstance().getWindow().getWindow();
		if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_RELEASE) {
			return;
		}
		if (dragging) {
			SidebarRows.Result rows = TreeTabs.sidebarRows();
			SidebarLayout l = layout(screen, rows);
			SidebarDrag.Drop drop =
					SidebarDrag.resolve(rows, dragTab, rowAtY(l, dragY), TreeTabs.count());
			if (drop != null) {
				// Group first: the move's meaning depends on which bucket the tab is in, since
				// SidebarRows derives display order from group membership and then tab order.
				TreeTabs.assignToGroup(dragTab, drop.groupId());
				TreeTabs.move(dragTab, drop.moveTo());
				ensureVisible(screen, TreeTabs.activeIndex());
			}
		}
		dragTab = -1;
		dragging = false;
	}

	/**
	 * Makes a phase out of the active tree and opens its name for typing.
	 *
	 * <p>Named at the moment it is created, because a phase called "Phase 2" is a phase nobody
	 * renames, and the whole point of grouping is that the name says which part of the build it is.
	 */
	public static void newGroup(Screen screen) {
		TabGroup group = TreeTabs.groupTab(TreeTabs.activeIndex());
		if (group == null || !active(screen)) {
			return;
		}
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		for (Slot s : l.slots()) {
			if (s.row().kind() == RowKind.GROUP && s.row().groupIndex() == group.id) {
				GroupName.open(screen, group.id, s.bounds());
				return;
			}
		}
	}

	/** Opens the recipe-choices screen, from the footer button or from the keybind. */
	public static void openChoices(Screen screen) {
		Minecraft.getInstance().setScreen(new RecipeChoicesScreen(screen));
	}

	/** F2 renames whichever phase the pointer is over, matching F2 on a tab. */
	public static boolean renameHovered(Screen screen, double mouseX, double mouseY) {
		if (!active(screen)) {
			return false;
		}
		SidebarLayout l = layout(screen, TreeTabs.sidebarRows());
		Slot s = l.slotAt(mouseX, mouseY);
		if (s == null || s.row().kind() != RowKind.GROUP) {
			return false;
		}
		GroupName.open(screen, s.row().groupIndex(), s.bounds());
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
		GroupName.reset();
		choiceCountAt = 0;
		scroll = 0;
		dragTab = -1;
		dragging = false;
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
			return TabPalette.PROGRESS_NONE;
		}
		if (tab.progress == ProgressState.COMPLETED) {
			return TabPalette.PROGRESS_COMPLETE;
		}
		if (tab.progress == ProgressState.PARTIAL) {
			return TabPalette.PROGRESS_PARTIAL;
		}
		return TabPalette.PROGRESS_NONE;
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
