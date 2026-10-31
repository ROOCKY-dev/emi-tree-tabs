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
 *   │ ┌────────┐ │        the other four fifths        │
 *   │ │ ▪ Fan 1│ │ ← ungrouped tab                    │
 *   │ └────────┘ │                                    │
 *   │ [⚙]    [≡] │ ← settings / craft-all             │
 *   └────────────┴────────────────────────────────────┘
 * </pre>
 *
 * The panel is attached to the window: flush with its edge, the full height of it. It used to
 * float, inset from every edge, to read as a panel over the tree; in play that inset was only
 * space the rows could not use, and the panel is now drawn larger than the rest of the GUI (see
 * {@link SidebarScale}), which needs every unit of it. Changed at the owner's request in the 5.0
 * playtest — do not restore the float without asking.
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

	/** Padding between the panel's edge and its contents. */
	public static final int PAD = 7;
	/** Padding inside a row, before its icon and after its marker. */
	public static final int ROW_PAD = 5;
	/** Height of a tab row. */
	public static final int ROW_HEIGHT = 26;
	/** Group headers are taller, because they carry a fold arrow and a count. */
	public static final int GROUP_HEIGHT = 29;
	/**
	 * Gap between rows. Deliberately small: rows are separated by the space *inside* them, not by
	 * the space between them, so the panel reads as a stack of surfaces rather than a scattered list.
	 */
	public static final int ROW_GAP = 2;
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
	/** The settings and craft-all buttons in the bottom corners. */
	public static final int BUTTON = 16;
	/** Reserved strip at the bottom of the panel for those two buttons. */
	public static final int FOOTER = 22;
	public static final int ICON = 16;

	/** The panel is this fraction of the screen width, so the tree keeps the other four fifths. */
	public static final double WIDTH_FRACTION = 0.2;
	/** Below this the sidebar cannot show anything useful and the caller should stay horizontal. */
	public static final int MIN_PANEL_WIDTH = 72;
	/** Names are dropped below this panel width; the icon and marker still fit. */
	public static final int NAME_WIDTH = 96;

	public final int screenWidth;
	public final int screenHeight;
	public final boolean onLeft;

	/** The floating panel itself. */
	public final Rect panel;
	/** Where rows are drawn, inside the panel's padding and above the footer. */
	public final Rect viewport;
	/** True when the panel is wide enough for row labels. */
	public final boolean showNames;
	/** Total height every row would occupy, ignoring the viewport. */
	public final int contentHeight;
	public final double maxScroll;

	private final List<Slot> slots;

	public SidebarLayout(int screenWidth, int screenHeight, List<Row> rows, double scroll, boolean onLeft) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.onLeft = onLeft;

		int width = (int) Math.round(screenWidth * WIDTH_FRACTION);
		// Attached: flush with the window's edge and as tall as the window.
		int height = Math.max(FOOTER + ROW_HEIGHT, screenHeight);
		int x = onLeft ? 0 : screenWidth - width;
		this.panel = new Rect(x, 0, width, height);

		this.viewport = new Rect(panel.x() + PAD, panel.y() + PAD,
				panel.width() - PAD * 2, panel.height() - PAD * 2 - FOOTER);
		this.showNames = panel.width() >= NAME_WIDTH;

		List<Row> safe = rows == null ? List.of() : rows;
		int total = 0;
		boolean hidden = false;
		int hidingGroup = -1;
		for (Row r : safe) {
			if (r.kind() == RowKind.GROUP) {
				// A collapsed group hides its members but never itself.
				hidden = r.collapsed();
				hidingGroup = r.groupIndex();
				total += GROUP_HEIGHT + ROW_GAP;
			} else {
				if (hidden && r.groupIndex() == hidingGroup) {
					continue;
				}
				total += ROW_HEIGHT + ROW_GAP;
			}
		}
		this.contentHeight = Math.max(0, total - ROW_GAP);
		this.maxScroll = Math.max(0, contentHeight - viewport.height());

		double s = Math.max(0, Math.min(scroll, maxScroll));
		this.slots = layOut(safe, s);
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
			y += h + ROW_GAP;
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
				viewport.width(), last.y() + last.height() - first.y());
	}

	// ------------------------------------------------------------ hit tests

	public boolean isOver(double x, double y) {
		return panel.contains(x, y);
	}

	/** The row under the pointer, or null. Clipped to the viewport, so the footer never matches. */
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
		return new Rect(panel.x() + PAD, panel.y() + panel.height() - PAD - BUTTON, BUTTON, BUTTON);
	}

	public Rect craftAllButton() {
		return new Rect(panel.x() + panel.width() - PAD - BUTTON,
				panel.y() + panel.height() - PAD - BUTTON, BUTTON, BUTTON);
	}

	/**
	 * Opens the recipe-choices screen. Always drawn, and lit while the trees actually disagree:
	 * hiding it until then meant nobody knew the screen existed until the day it appeared.
	 */
	public Rect choicesButton() {
		return footerSlot(2);
	}

	/** Brings back the last closed tab. Drawn only while there is one to bring back. */
	public Rect reopenButton() {
		return footerSlot(3);
	}

	/**
	 * The n-th button from the left along the footer, settings being the 0th.
	 *
	 * <p>Craft-all is not one of these: it owns the opposite corner, and {@link #footerFits} is how
	 * the ones in between give way to it on a narrow panel.
	 */
	private Rect footerSlot(int n) {
		return new Rect(panel.x() + PAD + (BUTTON + 4) * n, panel.y() + panel.height() - PAD - BUTTON,
				BUTTON, BUTTON);
	}

	/**
	 * Whether a footer button has room before the craft-all corner.
	 *
	 * <p>At {@link #MIN_PANEL_WIDTH} the third button lands on top of it. The corners are the two
	 * that always exist, so it is the middle ones that give way — and the keybind is why losing
	 * one costs nothing.
	 */
	public boolean footerFits(Rect r) {
		return r.x() + r.width() + 4 <= craftAllButton().x();
	}

	/**
	 * Makes a new phase out of the active tree.
	 *
	 * <p>Beside settings rather than in the free corner: the two corners are "change how this
	 * behaves" and "act on every tree", and making a phase is neither — it acts on one tree.
	 */
	public Rect newGroupButton() {
		return footerSlot(1);
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
				&& screenHeight >= FOOTER + PAD * 2 + ROW_HEIGHT * 3 + ROW_GAP * 2;
	}
}
