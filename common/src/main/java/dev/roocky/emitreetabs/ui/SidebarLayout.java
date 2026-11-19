package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Where every part of the vertical tab sidebar is, and nothing else.
 *
 * <p>Like {@link TabLayout}, this imports nothing from Minecraft or EMI, so the whole layout can be
 * tested without launching the game. That is not a nicety: the sidebar has nested rectangles,
 * collapsing groups and a scroll offset, which is considerably more arithmetic than the horizontal
 * strip ever had, and every tab bar defect so far has been an arithmetic one.
 *
 * <p>An instance is a snapshot. Build one per frame from the screen size, the rows and the scroll
 * offset; read from it; throw it away.
 *
 * <h2>The shape</h2>
 *
 * <pre>
 *   ┌─ screen ────────────────────────────────────────┐
 *   ├────────────┐                                    │
 *   │ ▸ Phase 1 ▪│ ← group header, fills the width    │
 *   │ ┌────────┐ │                                    │
 *   │ │ ▪ Oak 4│ │ ← member tab, inside the group     │
 *   │ │ ▪ Iron2│ │   border                           │
 *   │ └────────┘ │        the focused tree gets        │
 *   │ ┌────────┐ │        the remaining width           │
 *   │ │ ▪ Fan 1│ │ ← ungrouped tab                    │
 *   │ └────────┘ │                                    │
 *   │ [⚙]    [≡] │ ← settings / craft-all             │
 *   └────────────┴────────────────────────────────────┘
 * </pre>
 *
 * The panel is attached to the window: flush with its edge, the full height of it. It used to
 * float, inset from every edge, to read as a panel over the tree; in play that inset was only
 * space the rows could not use. Contents draw smaller than the rest of the GUI (see
 * {@link SidebarScale}), leaving room for names and actions. Changed during the 5.0 playtest.
 *
 * <p>Every measurement here is in <em>sidebar units</em>: the caller divides the screen by the
 * panel's scale before building a layout, and multiplies back on the way out.
 */
public final class SidebarLayout {

	/** What a row in the sidebar is. Groups own the rows that follow them until the next group. */
	public enum RowKind { GROUP, TAB }

	/** The caller's model of one row, flattened. Purely data, so tests can build it by hand. */
	public record Row(RowKind kind, int groupIndex, boolean collapsed, boolean hasName) {
		public static Row group(int groupIndex, boolean collapsed) {
			return new Row(RowKind.GROUP, groupIndex, collapsed, true);
		}

		public static Row tab(int groupIndex) {
			return new Row(RowKind.TAB, groupIndex, false, true);
		}

		/** A tab belonging to no group. */
		public static Row tab() {
			return new Row(RowKind.TAB, -1, false, true);
		}
	}

	public record Rect(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}
	}

	/**
	 * A button on a row. Shown while the row is hovered, so the panel stays quiet at rest and every
	 * gesture the tooltip used to describe in words has something to click.
	 */
	public enum RowAction {
		CLOSE, RENAME, BATCH, PARK, FOCUS
	}

	/** Where one of a row's buttons is. */
	public record Action(RowAction kind, Rect bounds) {
	}

	/**
	 * Which buttons each kind of row offers, in the order they claim space from the right.
	 *
	 * <p>Close comes first so it is the last to give way on a narrow panel: it is the one every
	 * reader looks for, and on a tab it sits where the strip's × always has. Rename gives way first,
	 * because F2 and right click both do the same thing.
	 */
	private static final List<RowAction> TAB_ACTIONS =
			List.of(RowAction.CLOSE, RowAction.BATCH, RowAction.RENAME);
	private static final List<RowAction> GROUP_ACTIONS =
			List.of(RowAction.CLOSE, RowAction.PARK, RowAction.FOCUS, RowAction.RENAME);

	/** One laid-out row: the model row, where it is, and which parts of it are interactive. */
	public record Slot(Row row, int index, Rect bounds, Rect marker, Rect collapse,
			List<Action> actions) {

		/** Where this row's button of the given kind is, or null when the row has no room for it. */
		public Rect action(RowAction kind) {
			for (Action a : actions) {
				if (a.kind() == kind) {
					return a.bounds();
				}
			}
			return null;
		}

		/** The × — kept as its own accessor because it is the button every row is sure to have. */
		public Rect close() {
			return action(RowAction.CLOSE);
		}
	}

	/**
	 * The border of EMI's sidebar frame: the corner size of the nine-patch it is drawn from. A
	 * resource pack can paint anything up to this wide, and packs do paint the sides differently,
	 * so contents start past it on every side rather than wherever the default art's border ends.
	 */
	public static final int FRAME = 8;
	/** Padding between the panel's edge and its contents: the frame, then clear space. */
	public static final int PAD = FRAME + 4;
	/** Padding inside a row, before its icon and after its marker. */
	public static final int ROW_PAD = 4;
	/** Height of a tab row: the 16px icon and marker with 3px either side, and no more. */
	public static final int ROW_HEIGHT = 22;
	/** Group headers are a little taller, because they carry a fold arrow and a count. */
	public static final int GROUP_HEIGHT = 24;
	/**
	 * Gap between rows. At 2 the rows ran together into one block once a pack gave them a strong
	 * border; 4 keeps each tree its own surface.
	 */
	public static final int ROW_GAP = 4;
	/**
	 * How far a group insets its members — on <em>both</em> sides, because this is the group box's
	 * padding rather than a margin on each member. Insetting only the left would leave every member's
	 * right edge flush with its header, which reads as a shunted row instead of a contained one.
	 */
	public static final int GROUP_PAD = 5;
	/** The square at a row's right end: batch count normally, crafting toggle on hover. */
	public static final int MARKER = 16;
	/** The fold arrow at a group header's left end. */
	public static final int COLLAPSE = 10;
	/** A row button: the × that closes, and the others that line up to its left. */
	public static final int CLOSE = 10;
	public static final int ACTION = CLOSE;
	/** Between two row buttons. Tighter than the gap to the marker, so they read as one cluster. */
	public static final int ACTION_GAP = 2;
	/** Between the leftmost row button and the marker it would otherwise touch. */
	public static final int ACTION_MARKER_GAP = 3;
	/** A toolbar button. */
	public static final int BUTTON = 18;
	/** Between the toolbar's edge and its buttons, and between two buttons. */
	public static final int TOOLBAR_PAD = 3;
	/**
	 * The toolbar across the top of the panel.
	 *
	 * <p>It was a footer: two 16px buttons pinned into the bottom corners, with the rows at the top
	 * and the empty middle of a full-height panel between them. On top, the controls and the list
	 * they act on are one block, and the space left over is all below it.
	 */
	public static final int TOOLBAR = BUTTON + TOOLBAR_PAD * 2;
	/** Between the toolbar and the first row. */
	public static final int TOOLBAR_GAP = 6;
	public static final int ICON = 16;

	/** The panel takes 25% of the full window width by default, independently of content scaling. */
	public static final double WIDTH_FRACTION = 0.25;
	public static final int DEFAULT_WIDTH_PERCENT = 25;
	/**
	 * The range dragging the edge may set. Below 15% a name no longer fits at common window sizes;
	 * above half the window the tree is the thing squeezed out of view.
	 */
	public static final int MIN_WIDTH_PERCENT = 15;
	public static final int MAX_WIDTH_PERCENT = 50;
	/**
	 * How much of an unpinned panel stays on screen while it is slid away: the inner edge of the
	 * frame and its grip. Enough to see and to point at, which is what brings the panel back.
	 */
	public static final int PEEK = 10;
	/** The grab strip along the panel's inner edge, inside the frame border, that resizes it. */
	public static final int GRIP = 6;
	/** Below this the sidebar cannot show anything useful and the caller should stay horizontal. */
	public static final int MIN_PANEL_WIDTH = PAD * 2 + GROUP_PAD * 2 + ROW_PAD * 2 + 2 + ICON + ACTION_MARKER_GAP + MARKER;
	/** Names are dropped below this panel width; the icon and marker still fit. */
	public static final int NAME_WIDTH = 96;

	public final int screenWidth;
	public final int screenHeight;
	public final boolean onLeft;

	/** The floating panel itself. */
	public final Rect panel;
	/** The strip of buttons across the top, inside the panel's padding. */
	public final Rect toolbar;
	/** Where rows are drawn, inside the panel's padding and below the toolbar. */
	public final Rect viewport;
	/** True when the panel is all the way in view. Rows only take clicks then. */
	public final boolean revealed;
	/** True when the panel is wide enough for row labels. */
	public final boolean showNames;
	/** Total height every row would occupy, ignoring the viewport. */
	public final int contentHeight;
	public final double maxScroll;

	private final List<Slot> slots;

	public SidebarLayout(int screenWidth, int screenHeight, List<Row> rows, double scroll, boolean onLeft) {
		this(screenWidth, screenHeight, rows, scroll, onLeft, DEFAULT_WIDTH_PERCENT, 1.0);
	}

	/**
	 * @param widthPercent how much of the window the panel takes; clamped to the drag range
	 * @param reveal       how far the panel is slid into view: 1 is open, 0 leaves only
	 *                     {@link #PEEK} showing at the window's edge. Unpinned panels move
	 *                     between the two.
	 */
	public SidebarLayout(int screenWidth, int screenHeight, List<Row> rows, double scroll, boolean onLeft,
			int widthPercent, double reveal) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.onLeft = onLeft;

		int percent = Math.max(MIN_WIDTH_PERCENT, Math.min(MAX_WIDTH_PERCENT, widthPercent));
		int width = Math.max(MIN_PANEL_WIDTH, (int) Math.round(screenWidth * percent / 100.0));
		width = Math.min(width, Math.max(MIN_PANEL_WIDTH, screenWidth));
		// Attached: flush with the window's edge and as tall as the window.
		int height = Math.max(TOOLBAR + ROW_HEIGHT, screenHeight);
		// Slid out past the window's edge rather than shrunk, so nothing inside has to re-lay out
		// while it moves; everything below is placed relative to the panel and follows it.
		double shown = Math.max(0, Math.min(1, reveal));
		// Smoothstep: starts and lands gently instead of moving at one speed and stopping dead.
		double eased = shown * shown * (3 - 2 * shown);
		int offscreen = (int) Math.round((width - PEEK) * (1 - eased));
		int x = onLeft ? -offscreen : screenWidth - width + offscreen;
		this.panel = new Rect(x, 0, width, height);
		this.revealed = shown >= 1;

		this.toolbar = new Rect(panel.x() + PAD, panel.y() + PAD, panel.width() - PAD * 2, TOOLBAR);
		int rowsTop = toolbar.y() + TOOLBAR + TOOLBAR_GAP;
		this.viewport = new Rect(panel.x() + PAD, rowsTop,
				panel.width() - PAD * 2, panel.y() + panel.height() - PAD - rowsTop);
		this.showNames = panel.width() >= NAME_WIDTH;

		List<Row> safe = rows == null ? List.of() : rows;
		int total = 0;
		boolean hidden = false;
		int hidingGroup = -1;
		for (int i = 0; i < safe.size(); i++) {
			Row r = safe.get(i);
			if (r.kind() == RowKind.GROUP) {
				// A collapsed group hides its members but never itself.
				hidden = r.collapsed();
				hidingGroup = r.groupIndex();
				total += GROUP_HEIGHT + ROW_GAP;
			} else {
				if (hidden && r.groupIndex() == hidingGroup) {
					continue;
				}
				total += ROW_HEIGHT + ROW_GAP + groupBottomPad(safe, i);
			}
		}
		this.contentHeight = Math.max(0, total - ROW_GAP);
		this.maxScroll = Math.max(0, contentHeight - viewport.height());

		double s = Math.max(0, Math.min(scroll, maxScroll));
		this.slots = layOut(safe, s);
	}

	private static int groupBottomPad(List<Row> rows, int i) {
		Row row = rows.get(i);
		return row.kind() != RowKind.GROUP && row.groupIndex() >= 0
				&& (i + 1 == rows.size() || rows.get(i + 1).groupIndex() != row.groupIndex()) ? GROUP_PAD : 0;
	}

	private List<Slot> layOut(List<Row> rows, double scroll) {
		List<Slot> out = new ArrayList<>();
		int y = viewport.y() - (int) Math.round(scroll);
		boolean hidden = false;
		int hidingGroup = -1;

		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			boolean isGroup = r.kind() == RowKind.GROUP;

			if (isGroup) {
				hidden = r.collapsed();
				hidingGroup = r.groupIndex();
			} else if (hidden && r.groupIndex() == hidingGroup) {
				continue;
			}

			int h = isGroup ? GROUP_HEIGHT : ROW_HEIGHT;
			// A group header spans the full content width, as its own background. Member tabs are
			// indented so the group's enclosing border has somewhere to be drawn.
			int inset = (!isGroup && r.groupIndex() >= 0) ? GROUP_PAD : 0;
			Rect bounds = new Rect(viewport.x() + inset, y, viewport.width() - inset * 2, h);

			// The marker sits at the row's right end: batch count, or the tree count on a group,
			// and it becomes the crafting toggle while the pointer is over the row.
			Rect marker = new Rect(bounds.x() + bounds.width() - MARKER - ROW_PAD,
					y + (h - MARKER) / 2, MARKER, MARKER);
			// Only a group has a fold arrow, and only clicks there fold it.
			Rect collapse = isGroup
					? new Rect(bounds.x() + ROW_PAD, y + (h - COLLAPSE) / 2, COLLAPSE, COLLAPSE)
					: null;
			List<Action> actions = layOutActions(isGroup ? GROUP_ACTIONS : TAB_ACTIONS,
					labelStart(bounds, isGroup), marker.x() - ACTION_MARKER_GAP, y, h);

			out.add(new Slot(r, i, bounds, marker, collapse, actions));
			y += h + ROW_GAP + groupBottomPad(rows, i);
		}
		return out;
	}

	/**
	 * Lines a row's buttons up leftwards from its marker, dropping the ones that would run into the
	 * start of the label.
	 *
	 * <p>A dropped button is not a lost feature: each has a key or a click that does the same, and
	 * the tooltip still names it. What must never happen is a button drawn over the row's icon or
	 * fold arrow, where a click meant for one would land on the other.
	 */
	private static List<Action> layOutActions(List<RowAction> wanted, int labelStart, int right,
			int rowY, int rowHeight) {
		List<Action> out = new ArrayList<>();
		int x = right;
		for (RowAction kind : wanted) {
			int left = x - ACTION;
			if (left < labelStart) {
				break;
			}
			out.add(new Action(kind, new Rect(left, rowY + (rowHeight - ACTION) / 2, ACTION, ACTION)));
			x = left - ACTION_GAP;
		}
		return out;
	}

	/** Where a tab row's icon is drawn, a little in from the progress stripe on its left edge. */
	public static int iconX(Rect bounds) {
		return bounds.x() + ROW_PAD + 2;
	}

	/** Where a row's label begins: past the fold arrow on a header, past the icon on a tab. */
	public static int labelStart(Rect bounds, boolean isGroup) {
		return isGroup
				? bounds.x() + ROW_PAD + COLLAPSE + 4
				: iconX(bounds) + ICON + 5;
	}

	/** Every laid-out row, in order. Rows hidden by a collapsed group are absent. */
	public List<Slot> slots() {
		return List.copyOf(slots);
	}

	/** The rows actually on screen, for drawing and for costing progress. */
	public List<Slot> visibleSlots() {
		List<Slot> out = new ArrayList<>();
		for (Slot s : slots) {
			if (s.bounds().y() + s.bounds().height() > viewport.y()
					&& s.bounds().y() < viewport.y() + viewport.height()) {
				out.add(s);
			}
		}
		return out;
	}

	/**
	 * The border drawn around a group and all of its members.
	 *
	 * <p>Returns null for a group with no visible members — a collapsed group is just its header,
	 * and drawing a box around nothing looks like a bug.
	 */
	public Rect groupBounds(int groupIndex) {
		Rect first = null;
		Rect last = null;
		for (Slot s : slots) {
			if (s.row().groupIndex() != groupIndex) {
				continue;
			}
			if (first == null) {
				first = s.bounds();
			}
			last = s.bounds();
		}
		if (first == null || first == last) {
			return null;
		}
		return new Rect(viewport.x(), first.y(),
				viewport.width(), last.y() + last.height() + GROUP_PAD - first.y());
	}

	// ------------------------------------------------------------ hit tests

	public boolean isOver(double x, double y) {
		return panel.contains(x, y);
	}

	/** The row under the pointer, or null. Clipped to the viewport, so the toolbar never matches. */
	public Slot slotAt(double x, double y) {
		if (!viewport.contains(x, y)) {
			return null;
		}
		for (Slot s : slots) {
			if (s.bounds().contains(x, y)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * True when the pointer is over the row's marker <em>and</em> the row itself.
	 *
	 * <p>The marker only becomes the crafting toggle while the row is hovered, which is what lets
	 * one square be a number at rest and a control under the cursor.
	 */
	public boolean overMarker(Slot slot, double x, double y) {
		return slot != null && viewport.contains(x, y) && slot.marker().contains(x, y);
	}

	/** True when the pointer is over a group header's fold arrow. Groups fold on click only. */
	public boolean overCollapse(Slot slot, double x, double y) {
		return slot != null && slot.collapse() != null
				&& viewport.contains(x, y) && slot.collapse().contains(x, y);
	}

	/** True when the pointer is over a row's ×. */
	public boolean overClose(Slot slot, double x, double y) {
		return actionAt(slot, x, y) == RowAction.CLOSE;
	}

	/**
	 * The row button under the pointer, or null.
	 *
	 * <p>Asked of a row the pointer is already over, like {@link #overMarker}: the buttons only
	 * exist while their row is hovered, so a point on another row must never answer for this one.
	 */
	public RowAction actionAt(Slot slot, double x, double y) {
		if (slot == null || !viewport.contains(x, y) || !slot.bounds().contains(x, y)) {
			return null;
		}
		for (Action a : slot.actions()) {
			if (a.bounds().contains(x, y)) {
				return a.kind();
			}
		}
		return null;
	}

	public Rect settingsButton() {
		return toolbarSlot(0);
	}

	/** The act-on-every-tree button, at the toolbar's far end. */
	public Rect craftAllButton() {
		return new Rect(toolbar.x() + toolbar.width() - TOOLBAR_PAD - BUTTON, toolbar.y() + TOOLBAR_PAD,
				BUTTON, BUTTON);
	}

	/**
	 * Opens the recipe-choices screen. Always drawn, and lit while the trees actually disagree:
	 * hiding it until then meant nobody knew the screen existed until the day it appeared.
	 */
	public Rect choicesButton() {
		return toolbarSlot(2);
	}

	/** Brings back the last closed tab. Drawn only while there is one to bring back. */
	public Rect reopenButton() {
		return toolbarSlot(3);
	}

	/**
	 * The n-th button from the left along the toolbar, settings being the 0th.
	 *
	 * <p>Craft-all is not one of these: it owns the far end, and {@link #toolbarFits} is how the
	 * ones in between give way to it on a narrow panel.
	 */
	private Rect toolbarSlot(int n) {
		return new Rect(toolbar.x() + TOOLBAR_PAD + (BUTTON + TOOLBAR_PAD) * n,
				toolbar.y() + TOOLBAR_PAD, BUTTON, BUTTON);
	}

	/**
	 * Pins the panel open, or lets it slide away. Next to craft-all, so the two buttons that are
	 * always there sit together at the far end and the middle ones give way to both.
	 */
	public Rect pinButton() {
		Rect all = craftAllButton();
		return new Rect(all.x() - TOOLBAR_PAD - BUTTON, all.y(), BUTTON, BUTTON);
	}

	/** Whether the pin button has room beside settings. Below that the setting is the way in. */
	public boolean pinFits() {
		Rect settings = settingsButton();
		return pinButton().x() >= settings.x() + settings.width() + TOOLBAR_PAD;
	}

	/**
	 * Whether a toolbar button has room before craft-all.
	 *
	 * <p>On a narrow panel the later buttons land on top of it. The two ends are the buttons that
	 * always exist, so it is the middle ones that give way — and the keybind is why losing one
	 * costs nothing.
	 */
	public boolean toolbarFits(Rect r) {
		int end = pinFits() ? pinButton().x() : craftAllButton().x();
		return r.x() + r.width() + TOOLBAR_PAD <= end;
	}

	/**
	 * The strip along the panel's inner edge that resizes it.
	 *
	 * <p>Inside the frame border, which nothing else uses, so grabbing it can never be mistaken
	 * for a click on a row or on the tree beside the panel.
	 */
	public Rect resizeHandle() {
		int x = onLeft ? panel.x() + panel.width() - GRIP : panel.x();
		return new Rect(x, panel.y(), GRIP, panel.height());
	}

	/**
	 * The width, in percent of the window, that puts the panel's inner edge at {@code edgeX}.
	 *
	 * <p>Clamped to the drag range and to {@link #MIN_PANEL_WIDTH}, so no drag can make a panel
	 * the layout then refuses to draw.
	 */
	public static int widthPercentFor(double edgeX, int screenWidth, boolean onLeft) {
		if (screenWidth <= 0) {
			return DEFAULT_WIDTH_PERCENT;
		}
		double width = onLeft ? edgeX : screenWidth - edgeX;
		int percent = (int) Math.round(width * 100.0 / screenWidth);
		int floor = (int) Math.ceil(MIN_PANEL_WIDTH * 100.0 / screenWidth);
		return Math.min(MAX_WIDTH_PERCENT, Math.max(Math.max(MIN_WIDTH_PERCENT, floor), percent));
	}

	/** How long an unpinned panel takes to slide all the way in or out. */
	public static final double SLIDE_MS = 160;

	/**
	 * Moves {@code reveal} towards {@code target} at a constant rate, so the slide takes
	 * {@link #SLIDE_MS} at any frame rate. The easing is applied where the value becomes a position,
	 * in the constructor, which keeps this a plain clock.
	 */
	public static double stepReveal(double reveal, double target, double elapsedMs) {
		if (elapsedMs <= 0) {
			return reveal;
		}
		double step = elapsedMs / SLIDE_MS;
		return target > reveal ? Math.min(target, reveal + step) : Math.max(target, reveal - step);
	}

	/**
	 * Makes a new phase out of the active tree.
	 *
	 * <p>Beside settings rather than at the far end: the two ends are "change how this behaves" and
	 * "act on every tree", and making a phase is neither — it acts on one tree.
	 */
	public Rect newGroupButton() {
		return toolbarSlot(1);
	}

	/** How wide a label may be on this row at rest, with none of its buttons showing. */
	public int labelBudget(Slot slot) {
		return labelBudget(slot, Set.of());
	}

	/**
	 * How wide a label may be on this row while the given buttons are drawn, or 0 when this panel is
	 * too narrow for labels.
	 *
	 * <p>The label gives way to the buttons rather than the other way round: they appear under the
	 * pointer, which is exactly when the name is already known.
	 */
	public int labelBudget(Slot slot, Set<RowAction> shown) {
		if (!showNames || slot == null) {
			return 0;
		}
		int used = ROW_PAD * 2 + (slot.collapse() != null ? COLLAPSE + 4 : 0)
				+ ICON + 5 + MARKER + 5;
		int budget = slot.bounds().width() - used;

		int leftmostShown = Integer.MAX_VALUE;
		for (Action a : slot.actions()) {
			if (shown.contains(a.kind())) {
				leftmostShown = Math.min(leftmostShown, a.bounds().x());
			}
		}
		if (leftmostShown != Integer.MAX_VALUE) {
			int labelLeft = labelStart(slot.bounds(), slot.collapse() != null);
			budget = Math.min(budget, leftmostShown - ACTION_GAP - labelLeft);
		}
		return Math.max(0, budget);
	}

	// --------------------------------------------------------------- scroll

	public double clampScroll(double scroll) {
		return Math.max(0, Math.min(scroll, maxScroll));
	}

	/** Whether this screen can host the sidebar at all. */
	public static boolean fits(int screenWidth, int screenHeight) {
		return screenWidth * WIDTH_FRACTION >= MIN_PANEL_WIDTH
				&& screenHeight >= TOOLBAR + TOOLBAR_GAP + PAD * 2 + ROW_HEIGHT * 3 + ROW_GAP * 2;
	}
}
