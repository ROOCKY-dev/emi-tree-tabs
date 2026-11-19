package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.roocky.emitreetabs.tab.RecipeChoices;
import dev.roocky.emitreetabs.tab.TabGroup;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.SidebarLayout.Action;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowAction;
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
 * The vertical tab panel, attached to the edge of EMI's tree screen.
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

	/** True while the panel's inner edge is being dragged. */
	private static boolean resizing;
	/** Where the edge was relative to the pointer when grabbed, so the edge does not jump to it. */
	private static double resizeGrab;
	/** The width being dragged to; written to the config when the button comes up. */
	private static int resizePercent;

	/**
	 * How far an unpinned panel is slid into view, 0 to 1. Negative until the first frame, which
	 * starts it where it belongs instead of animating in from nowhere every time the screen opens.
	 */
	private static double reveal = -1;
	private static long revealClock;
	/** When the pointer was last over the panel. An unpinned panel waits a moment before leaving. */
	private static long lastOverAt;
	/** Long enough that brushing past the edge, or a jittery hand on the list, does not shut it. */
	private static final long HIDE_DELAY_MS = 350;

	private TreeSidebar() {
	}

	/** Whether the sidebar is the layout in use right now. */
	public static boolean active(Screen screen) {
		if (!TreeTabsConfig.enabled || TreeTabs.count() == 0) {
			return false;
		}
		return scale().useSidebar(TreeTabsConfig.tabOrientation, screen.width, screen.height);
	}

	private static SidebarLayout layout(Screen screen, SidebarRows.Result rows) {
		SidebarScale scale = scale();
		return new SidebarLayout(scale.toUnits(screen.width), scale.toUnits(screen.height), rows.rows(), scroll,
				!TreeTabsConfig.barAtBottom, resizing ? resizePercent : TreeTabsConfig.sidebarWidthPercent,
				currentReveal());
	}

	private static double currentReveal() {
		return TreeTabsConfig.sidebarPinned ? 1 : Math.max(0, reveal);
	}

	/**
	 * Slides an unpinned panel towards where it should be this frame.
	 *
	 * <p>Open while the pointer is over it, and while anything on it is mid-gesture — a drag, a
	 * resize, a name or a batch count being typed — because sliding away under a half-finished edit
	 * would throw it at the tree.
	 */
	private static void updateReveal(Screen screen, int mouseX, int mouseY) {
		long now = System.currentTimeMillis();
		if (TreeTabsConfig.sidebarPinned) {
			reveal = 1;
			revealClock = now;
			return;
		}
		boolean busy = resizing || dragTab >= 0 || RowName.isOpen() || BatchInput.isOpen();
		if (busy || layout(screen, TreeTabs.sidebarRows()).isOver(mouseX, mouseY)) {
			lastOverAt = now;
		}
		double target = now - lastOverAt < HIDE_DELAY_MS ? 1 : 0;
		if (reveal < 0) {
			reveal = target;
		} else {
			reveal = SidebarLayout.stepReveal(reveal, target, Math.min(100, now - revealClock));
		}
		revealClock = now;
	}

	private static SidebarScale scale() {
		return SidebarScale.standard();
	}

	/** Screen coordinates for text fields and tooltips, which render outside the panel pose. */
	private static Rect screenRect(Screen screen, Rect rect) {
		return scale().toScreen(rect);
	}

	public static boolean isOver(Screen screen, double mouseX, double mouseY) {
		mouseX = scale().toUnits(mouseX);
		mouseY = scale().toUnits(mouseY);
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
		SidebarScale scale = scale();
		mouseX = (int) scale.toUnits((double) mouseX);
		mouseY = (int) scale.toUnits((double) mouseY);
		TreeTabs.syncActiveCraftingMode();

		updateReveal(screen, mouseX, mouseY);
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		scroll = l.clampScroll(scroll);

		Font font = Minecraft.getInstance().font;
		// Nothing on a panel that is still moving answers the pointer, so a row does not light up
		// as it slides past and then turn out not to be clickable.
		Slot hovered = l.revealed ? l.slotAt(mouseX, mouseY) : null;

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

		graphics.pose().pushPose();
		graphics.pose().scale((float) scale.factor, (float) scale.factor, 1f);
		panel(graphics, l.panel);
		drawGrip(graphics, l, mouseX, mouseY);

		// GuiGraphics scissor coordinates ignore the pose on 1.21.1.
		Rect clip = scale.toScreen(l.viewport);
		graphics.enableScissor(clip.x(), clip.y(), clip.x() + clip.width(), clip.y() + clip.height());

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

		drawToolbar(graphics, l, mouseX, mouseY);
		graphics.pose().popPose();

		if (hovered != null) {
			tooltip(screen, graphics, font, l, rows, hovered, mouseX, mouseY);
		} else if (l.revealed) {
			toolbarTooltip(screen, graphics, font, l, mouseX, mouseY);
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
	 * The row under a y coordinate, or {@link Integer#MAX_VALUE} past the last visible row.
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
	private static void toolbarTooltip(Screen screen, GuiGraphics g, Font font, SidebarLayout l,
			int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		if (TreeTabs.count() > 0 && l.toolbarFits(l.newGroupButton())
				&& l.newGroupButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.group.new"));
			lines.add(Component.translatable("emi.tree_tabs.group.new.desc")
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("emi.tree_tabs.group.new.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (l.pinFits() && l.pinButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable(TreeTabsConfig.sidebarPinned
					? "emi.tree_tabs.sidebar.unpin" : "emi.tree_tabs.sidebar.pin"));
			lines.add(Component.translatable("emi.tree_tabs.sidebar.pin.desc")
					.withStyle(ChatFormatting.GRAY));
		} else if (l.craftAllButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.all.title"));
			lines.add(Component.translatable("emi.tree_tabs.all.state",
					TreeTabs.craftingCount(), TreeTabs.count()).withStyle(ChatFormatting.GRAY));
		} else if (l.toolbarFits(l.choicesButton()) && l.choicesButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.choices.button"));
			lines.add((choiceCount() > 0
					? Component.translatable("emi.tree_tabs.choices.button.desc", choiceCount())
					: Component.translatable("emi.tree_tabs.choices.button.none"))
					.withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("emi.tree_tabs.choices.button.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (TreeTabs.hasClosedTabs() && l.toolbarFits(l.reopenButton())
				&& l.reopenButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.reopen.button"));
			lines.add(Component.translatable("emi.tree_tabs.reopen.button.hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		} else if (ConfigScreenHook.available() && l.settingsButton().contains(mouseX, mouseY)) {
			lines.add(Component.translatable("emi.tree_tabs.config.title"));
		} else {
			return;
		}
		Rect panel = screenRect(screen, l.panel);
		TabTooltip.render(g, font, lines, screen.width, screen.height,
				new TabTooltip.Rect(panel.x(), panel.y(), panel.width(), panel.height()),
				l.onLeft ? panel.x() + panel.width() + 60 : panel.x() - 60,
				scale().toScreen(mouseX), scale().toScreen(mouseY));
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

	/**
	 * Three short bars on the inner edge: the handle that resizes the panel, and on an unpinned
	 * panel the part that stays on screen to say where it went.
	 */
	private static void drawGrip(GuiGraphics g, SidebarLayout l, int mouseX, int mouseY) {
		Rect h = l.resizeHandle();
		boolean lit = resizing || l.revealed && h.contains(mouseX, mouseY);
		int colour = lit ? EmiSkin.TEXT : EmiSkin.TEXT_DIM;
		int x = h.x() + (h.width() - 2) / 2;
		int top = h.y() + h.height() / 2 - 14;
		for (int i = 0; i < 3; i++) {
			int y = top + i * 10;
			g.fill(x, y, x + 2, y + 8, colour);
		}
	}

	/** EMI's own sidebar frame, so the panel matches the item list beside it under any pack. */
	private static void panel(GuiGraphics g, Rect r) {
		EmiSkin.sidebarPanel(g, r.x(), r.y(), r.width(), r.height());
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
		// A tab is a slot well, and the open one is raised out of the list as an EMI panel: the
		// same sunken-or-raised language EMI's own screens use, and both come from its textures.
		if (isActive) {
			EmiSkin.panel(g, b.x(), b.y(), b.width(), b.height());
		} else {
			EmiSkin.slot(g, b.x(), b.y(), b.width(), b.height());
			if (hovered) {
				EmiSkin.highlight(g, b.x() + 1, b.y() + 1, b.width() - 2, b.height() - 2);
			}
		}

		// State on the leading edge, not a corner pip: down a column a stripe reads at a glance.
		// Inside the border, so the pack's edge stays whole.
		int inset = isActive ? 3 : 1;
		g.fill(b.x() + inset, b.y() + inset, b.x() + inset + 2, b.y() + b.height() - inset,
				progressColour(tab));

		EmiIngredient icon = tab.icon();
		if (!icon.isEmpty()) {
			icon.render(g, SidebarLayout.iconX(b), b.y() + (b.height() - SidebarLayout.ICON) / 2, 0f,
					EmiIngredient.RENDER_ICON);
		}

		Set<RowAction> shown = hovered ? EnumSet.allOf(RowAction.class) : EnumSet.noneOf(RowAction.class);
		int budget = l.labelBudget(s, shown);
		if (budget > 6 && !RowName.editingTab(tabIndex)) {
			g.drawString(font, tab.trimmedLabel(font, budget),
					SidebarLayout.labelStart(b, false), b.y() + (b.height() - 8) / 2,
					isActive ? EmiSkin.TEXT : EmiSkin.TEXT_DIM, true);
		}
		drawActions(g, l, s, shown, Set.of(), mouseX, mouseY);

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
		// EMI's category-title bar, the thing it puts a heading on.
		EmiSkin.inset(g, b.x(), b.y(), b.width(), b.height());
		if (hovered) {
			EmiSkin.highlight(g, b.x(), b.y(), b.width(), b.height());
		}
		g.fill(b.x(), b.y(), b.x() + 3, b.y() + b.height(), group.colour);

		Rect c = s.collapse();
		Icons.centred(g, group.collapsed ? Icons.CARET_RIGHT : Icons.CARET_DOWN,
				c.x(), c.y(), c.width(), c.height(),
				l.overCollapse(s, mouseX, mouseY) ? EmiSkin.TEXT : EmiSkin.TEXT_DIM);

		// Parking used to show only as dimmed text, which reads as "disabled" rather than "set
		// aside for now". Now the park button stays up on a parked header: the state and the control
		// that changes it are one icon, the way the marker is both a count and the crafting toggle.
		Set<RowAction> shown = groupActionsShown(group, hovered);
		Set<RowAction> lit = group.parked ? Set.of(RowAction.PARK) : Set.of();

		int budget = l.labelBudget(s, shown);
		if (budget > 6 && !RowName.editingGroup(group.id)) {
			g.drawString(font, trim(font, group.name, budget),
					SidebarLayout.labelStart(b, true), b.y() + (b.height() - 8) / 2,
					group.parked ? EmiSkin.TEXT_DIM : EmiSkin.TEXT, true);
		}
		drawActions(g, l, s, shown, lit, mouseX, mouseY);

		int size = TreeTabs.groupSize(group.id);
		boolean allCrafting = size > 0 && TreeTabs.groupCraftingCount(group.id) == size;
		marker(g, font, s.marker(), String.valueOf(size), allCrafting,
				hovered && l.overMarker(s, mouseX, mouseY));
	}

	/** Every button while the header is hovered; at rest, only the park state if it is parked. */
	private static Set<RowAction> groupActionsShown(TabGroup group, boolean hovered) {
		if (hovered) {
			return EnumSet.allOf(RowAction.class);
		}
		return group.parked ? EnumSet.of(RowAction.PARK) : EnumSet.noneOf(RowAction.class);
	}

	/**
	 * Draws a row's buttons: plain icons rather than boxed buttons, so a row full of them still reads
	 * as one row and not as a toolbar. The one under the pointer brightens; × turns red, as on the
	 * strip.
	 */
	private static void drawActions(GuiGraphics g, SidebarLayout l, Slot s, Set<RowAction> shown,
			Set<RowAction> lit, int mouseX, int mouseY) {
		RowAction pointed = l.actionAt(s, mouseX, mouseY);
		for (Action a : s.actions()) {
			if (!shown.contains(a.kind())) {
				continue;
			}
			int ink;
			if (a.kind() == pointed) {
				ink = a.kind() == RowAction.CLOSE ? TabPalette.CLOSE_HOVER : EmiSkin.TEXT;
			} else {
				ink = lit.contains(a.kind()) ? EmiSkin.TEXT : EmiSkin.TEXT_DIM;
			}
			Rect r = a.bounds();
			Icons.centred(g, actionIcon(a.kind()), r.x(), r.y(), r.width(), r.height(), ink);
		}
	}

	private static Icons.Sprite actionIcon(RowAction kind) {
		return switch (kind) {
			case CLOSE -> Icons.CLOSE;
			case RENAME -> Icons.RENAME;
			case BATCH -> Icons.BATCH;
			case PARK -> Icons.PARK;
			case FOCUS -> Icons.FOCUS;
		};
	}

	/** The square at a row's right end: a count at rest, the crafting toggle under the cursor. */
	private static void marker(GuiGraphics g, Font font, Rect r, String count,
			boolean crafting, boolean hovered) {
		boolean lit = crafting || hovered;
		// An EMI slot, filled with the crafting colour while lit. The colour is status, so it stays
		// ours; the well around it is the pack's.
		EmiSkin.slot(g, r.x(), r.y(), r.width(), r.height());
		if (lit) {
			g.fill(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, TabPalette.CRAFTING);
		}
		int ink = lit ? TabPalette.INK_ON_LIT : EmiSkin.TEXT;
		if (hovered) {
			// The number is only information until you reach for it; then the same square is the
			// control, and says so with the mark the craft-all button uses.
			Icons.centred(g, Icons.CRAFT_ONE, r.x(), r.y(), r.width(), r.height(), ink);
		} else {
			int w = font.width(count);
			g.drawString(font, count, r.x() + (r.width() - w) / 2, r.y() + (r.height() - 8) / 2,
					ink, !lit);
		}
	}

	private static void drawToolbar(GuiGraphics g, SidebarLayout l, int mouseX, int mouseY) {
		Rect bar = l.toolbar;
		EmiSkin.inset(g, bar.x(), bar.y(), bar.width(), bar.height());
		Rect all = l.craftAllButton();
		// No settings screen installed means no button, rather than a button that does nothing.
		if (ConfigScreenHook.available()) {
			Rect settings = l.settingsButton();
			button(g, settings, Icons.SETTINGS, settings.contains(mouseX, mouseY), false);
		}
		if (TreeTabs.count() > 0 && l.toolbarFits(l.newGroupButton())) {
			Rect group = l.newGroupButton();
			button(g, group, Icons.NEW_GROUP, group.contains(mouseX, mouseY), false);
		}
		// Always there, so the screen can be found before it is needed. Lit while the trees
		// disagree, because then it is a thing to attend to rather than a thing to do.
		if (l.toolbarFits(l.choicesButton())) {
			Rect choices = l.choicesButton();
			button(g, choices, Icons.CHOICES, choices.contains(mouseX, mouseY), choiceCount() > 0);
		}
		if (TreeTabs.hasClosedTabs() && l.toolbarFits(l.reopenButton())) {
			Rect reopen = l.reopenButton();
			button(g, reopen, Icons.REOPEN, reopen.contains(mouseX, mouseY), false);
		}
		if (l.pinFits()) {
			// Bright while pinned, dim while it slides away: the state and its switch are one icon.
			Rect pin = l.pinButton();
			boolean over = pin.contains(mouseX, mouseY);
			EmiSkin.button(g, pin.x(), pin.y(), pin.width(), pin.height(), EmiSkin.State.of(true, over));
			Icons.centred(g, Icons.PIN, pin.x(), pin.y(), pin.width(), pin.height(),
					TreeTabsConfig.sidebarPinned || over ? EmiSkin.TEXT : EmiSkin.TEXT_DIM);
		}
		button(g, all, Icons.CRAFT_ALL, all.contains(mouseX, mouseY), TreeTabs.craftingCount() > 0);
	}

	private static void button(GuiGraphics g, Rect r, Icons.Sprite icon,
			boolean hovered, boolean lit) {
		// EMI's button, in its own hover state. Lit says "attend to this", which is status and so a
		// colour of ours on the icon rather than a different button.
		EmiSkin.button(g, r.x(), r.y(), r.width(), r.height(), EmiSkin.State.of(true, hovered));
		Icons.centred(g, icon, r.x(), r.y(), r.width(), r.height(),
				lit ? TabPalette.CRAFTING : hovered ? EmiSkin.TEXT : EmiSkin.TEXT_DIM);
	}

	private static void tooltip(Screen screen, GuiGraphics g, Font font, SidebarLayout l,
			SidebarRows.Result rows, Slot s, int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		RowAction pointed = l.actionAt(s, mouseX, mouseY);
		if (pointed != null) {
			actionTooltip(lines, s.row().kind() == RowKind.GROUP, pointed, s.row().groupIndex());
		} else if (s.row().kind() == RowKind.GROUP) {
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
			// The strip's tooltip has listed its gestures since 2.0; this one listed none, so
			// every one of them - the marker being a control, drag moving a tree between phases,
			// F2, middle click - had to be discovered by accident.
			lines.add(Component.translatable("emi.tree_tabs.hint.sidebar.select")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.hint.sidebar.marker")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.hint.sidebar.batch")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.hint.rename")
					.withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("emi.tree_tabs.hint.close")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
		// Anchored beside the panel, never over it: the sidebar occupies a whole screen edge, so a
		// cursor-relative tooltip would sit on top of the rows it is describing.
		Rect panel = screenRect(screen, l.panel);
		TabTooltip.render(g, font, lines, screen.width, screen.height,
				new TabTooltip.Rect(panel.x(), panel.y(), panel.width(), panel.height()),
				l.onLeft ? panel.x() + panel.width() + 60 : panel.x() - 60,
				scale().toScreen(mouseX), scale().toScreen(mouseY));
	}

	/**
	 * What a row button does, and the key or click that does the same.
	 *
	 * <p>The shortcut line is what makes the button a way to <em>learn</em> the gesture rather than
	 * only a replacement for it.
	 */
	private static void actionTooltip(List<Component> lines, boolean isGroup, RowAction action,
			int groupId) {
		String key = switch (action) {
			case CLOSE -> isGroup ? "emi.tree_tabs.action.drop_phase" : "emi.tree_tabs.action.close";
			case RENAME -> "emi.tree_tabs.action.rename";
			case BATCH -> "emi.tree_tabs.action.batch";
			case PARK -> {
				TabGroup group = TreeTabs.group(groupId);
				yield group != null && group.parked ? "emi.tree_tabs.group.unpark" : "emi.tree_tabs.group.park";
			}
			case FOCUS -> "emi.tree_tabs.group.only";
		};
		lines.add(Component.translatable(key));
		String hint = switch (action) {
			case CLOSE -> isGroup ? "emi.tree_tabs.action.drop_phase.hint" : "emi.tree_tabs.action.close.hint";
			case RENAME -> isGroup ? "emi.tree_tabs.group.rename.hint" : "emi.tree_tabs.action.rename.hint";
			case BATCH -> "emi.tree_tabs.action.batch.hint";
			case PARK -> "emi.tree_tabs.action.park.hint";
			case FOCUS -> "emi.tree_tabs.action.focus.hint";
		};
		lines.add(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ----------------------------------------------------------------- input

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		mouseX = scale().toUnits(mouseX);
		mouseY = scale().toUnits(mouseY);
		if (!active(screen)) {
			return false;
		}
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}
		if (!l.revealed) {
			// The panel is on its way in. Swallow the click rather than hand it to whatever row
			// happens to be sliding under the pointer.
			return true;
		}

		if (button == 0 && l.resizeHandle().contains(mouseX, mouseY)) {
			resizing = true;
			resizePercent = TreeTabsConfig.sidebarWidthPercent;
			resizeGrab = edgeX(l) - mouseX;
			return true;
		}
		if (button == 0 && l.pinFits() && l.pinButton().contains(mouseX, mouseY)) {
			click();
			togglePinned();
			return true;
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
		if (button == 0 && TreeTabs.count() > 0 && l.toolbarFits(l.newGroupButton())
				&& l.newGroupButton().contains(mouseX, mouseY)) {
			click();
			newGroup(screen);
			return true;
		}
		if (button == 0 && l.toolbarFits(l.choicesButton()) && l.choicesButton().contains(mouseX, mouseY)) {
			click();
			openChoices(screen);
			return true;
		}
		if (button == 0 && TreeTabs.hasClosedTabs() && l.toolbarFits(l.reopenButton())
				&& l.reopenButton().contains(mouseX, mouseY)) {
			click();
			TreeTabs.reopenClosed();
			ensureVisible(screen, TreeTabs.activeIndex());
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
			RowAction action = button == 0 ? l.actionAt(s, mouseX, mouseY) : null;
			if (action != null) {
				click();
				runGroupAction(screen, action, group, s);
				return true;
			}
			// A middle click drops the phase, matching how a tab closes.
			if (button == 2) {
				click();
				runGroupAction(screen, RowAction.CLOSE, group, s);
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
				openBatchInput(screen, tabIndex, s);
			} else {
				TreeTabs.toggleCrafting(tabIndex);
			}
			return true;
		}
		RowAction action = button == 0 ? l.actionAt(s, mouseX, mouseY) : null;
		if (action != null) {
			click();
			runTabAction(screen, action, tabIndex, s);
			return true;
		}
		// Right click renames, as it does on the strip. Its tooltip line ("Right click or F2 to
		// rename") is shared with the strip, and here it used to only select the row.
		if (button == 1) {
			click();
			RowName.openTab(screen, tabIndex, screenRect(screen, s.bounds()));
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

	/** What a tab row's buttons do. Each is the same operation its gesture already performed. */
	private static void runTabAction(Screen screen, RowAction action, int tabIndex, Slot s) {
		switch (action) {
			case CLOSE -> TreeTabs.close(tabIndex);
			case RENAME -> RowName.openTab(screen, tabIndex, screenRect(screen, s.bounds()));
			case BATCH -> openBatchInput(screen, tabIndex, s);
			// A tab row never lays these out; nothing to do if one ever arrives.
			case PARK, FOCUS -> {
			}
		}
	}

	/**
	 * What a phase header's buttons do.
	 *
	 * <p>Dropping a phase leaves its trees open and simply ungrouped: a phase is a label on trees,
	 * not a box holding them, so deleting it must not take them with it.
	 */
	private static void runGroupAction(Screen screen, RowAction action, TabGroup group, Slot s) {
		switch (action) {
			case CLOSE -> {
				RowName.close(screen);
				TreeTabs.removeGroup(group.id);
			}
			case RENAME -> RowName.openGroup(screen, group.id, screenRect(screen, s.bounds()));
			case PARK -> TreeTabs.setParked(group.id, !group.parked);
			case FOCUS -> TreeTabs.parkAllExcept(group.id);
			// A header never lays this out; its trees each have their own batch count.
			case BATCH -> {
			}
		}
	}

	/** Opens the batch box beside the row's marker, which is the number it edits. */
	private static void openBatchInput(Screen screen, int tabIndex, Slot s) {
		Rect m = s.marker();
		Rect marker = screenRect(screen, m);
		BatchInput.open(screen, tabIndex, marker.x() + marker.width() + 4, marker.y());
	}

	public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
		mouseX = scale().toUnits(mouseX);
		mouseY = scale().toUnits(mouseY);
		if (resizing && button == 0 && active(screen)) {
			SidebarLayout l = layout(screen, TreeTabs.sidebarRows());
			resizePercent = SidebarLayout.widthPercentFor(mouseX + resizeGrab, l.screenWidth, l.onLeft);
			return true;
		}
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
		if (resizing) {
			long window = Minecraft.getInstance().getWindow().getWindow();
			if (!active(screen)
					|| GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
				// Saved once, on release: writing the file on every frame of a drag would be a
				// disk write per frame for a value only the last frame decides.
				resizing = false;
				if (resizePercent != TreeTabsConfig.sidebarWidthPercent) {
					TreeTabsConfig.sidebarWidthPercent = resizePercent;
					TreeTabsConfig.save();
				}
			}
		}
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
				RowName.openGroup(screen, group.id, screenRect(screen, s.bounds()));
				return;
			}
		}
	}

	/** Opens the recipe-choices screen, from the toolbar button or from the keybind. */
	public static void openChoices(Screen screen) {
		Minecraft.getInstance().setScreen(new RecipeChoicesScreen(screen));
	}

	/**
	 * F2 renames whichever row the pointer is over — a phase or a tree — and falls back to the
	 * active tree's row when the pointer is elsewhere.
	 *
	 * <p>Always handled here while the sidebar is the layout. {@code TabBar}'s own rename box is
	 * drawn by {@code TabBar.render}, which does not run in this layout, so letting it take F2
	 * opened a text box that was never painted.
	 */
	public static boolean renameHovered(Screen screen, double mouseX, double mouseY) {
		mouseX = scale().toUnits(mouseX);
		mouseY = scale().toUnits(mouseY);
		if (!active(screen)) {
			return false;
		}
		SidebarRows.Result rows = TreeTabs.sidebarRows();
		SidebarLayout l = layout(screen, rows);
		Slot s = l.slotAt(mouseX, mouseY);
		if (s == null) {
			int row = rows.rowOf(TreeTabs.activeIndex());
			for (Slot candidate : l.slots()) {
				if (candidate.index() == row) {
					s = candidate;
					break;
				}
			}
		}
		if (s == null) {
			return false;
		}
		if (s.row().kind() == RowKind.GROUP) {
			RowName.openGroup(screen, s.row().groupIndex(), screenRect(screen, s.bounds()));
			return true;
		}
		int tab = rows.tabAt(s.index());
		if (tab < 0) {
			return false;
		}
		RowName.openTab(screen, tab, screenRect(screen, s.bounds()));
		return true;
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		mouseX = scale().toUnits(mouseX);
		mouseY = scale().toUnits(mouseY);
		if (!active(screen)) {
			return false;
		}
		SidebarLayout l = layout(screen, TreeTabs.sidebarRows());
		if (!l.isOver(mouseX, mouseY)) {
			return false;
		}
		if (!l.revealed || l.maxScroll <= 0) {
			// The grip and sliding panel capture the wheel without moving hidden rows.
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
		RowName.reset();
		choiceCountAt = 0;
		scroll = 0;
		dragTab = -1;
		dragging = false;
		resizing = false;
		// Reopening the screen starts an unpinned panel tucked away, not sliding in.
		reveal = -1;
		lastOverAt = 0;
	}

	/** Pins the panel open, or lets it slide away. Also reachable from the settings screen. */
	public static void togglePinned() {
		TreeTabsConfig.sidebarPinned = !TreeTabsConfig.sidebarPinned;
		// Just unpinned with the pointer on the button: stay open until it leaves, like a hover.
		lastOverAt = System.currentTimeMillis();
		reveal = 1;
		TreeTabsConfig.save();
	}

	/** The panel's inner edge, the one the resize handle sits on. */
	private static double edgeX(SidebarLayout l) {
		return l.onLeft ? l.panel.x() + l.panel.width() : l.panel.x();
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
