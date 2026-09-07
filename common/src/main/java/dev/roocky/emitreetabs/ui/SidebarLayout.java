package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

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
 *   ┌─ screen ─────────────────────────────────────────┐
 *   │  ┌────────────┐                                  │
 *   │  │ ▸ Phase 1 ▪│ ← group header, fills the width  │
 *   │  │ ┌────────┐ │                                  │
 *   │  │ │ ▪ Oak 4│ │ ← member tab, inside the group   │
 *   │  │ │ ▪ Iron2│ │   border                         │
 *   │  │ └────────┘ │        the focused tree gets      │
 *   │  │ ┌────────┐ │        the other four fifths      │
 *   │  │ │ ▪ Fan 1│ │ ← ungrouped tab                  │
 *   │  │ └────────┘ │                                  │
 *   │  │ [⚙]    [≡] │ ← settings / craft-all           │
 *   │  └────────────┘                                  │
 *   └──────────────────────────────────────────────────┘
 * </pre>
 *
 * The panel floats: it is inset from every screen edge by {@link #MARGIN}, which is what makes it
 * read as a panel over the tree rather than chrome bolted to the window.
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

	/** One laid-out row: the model row, where it is, and which parts of it are interactive. */
	public record Slot(Row row, int index, Rect bounds, Rect marker, Rect collapse) {
	}

	/** The panel floats clear of every screen edge by this much. */
	public static final int MARGIN = 14;
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

		int width = Math.max(MIN_PANEL_WIDTH, (int) Math.round(screenWidth * WIDTH_FRACTION));
		// Never let the panel eat the screen on a tiny window: it may not exceed a third.
		width = Math.min(width, Math.max(MIN_PANEL_WIDTH, screenWidth / 3));
		int height = Math.max(FOOTER + ROW_HEIGHT, screenHeight - MARGIN * 2);
		int x = onLeft ? MARGIN : screenWidth - MARGIN - width;
		this.panel = new Rect(x, MARGIN, width, height);

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

			out.add(new Slot(r, i, bounds, marker, collapse));
			y += h + ROW_GAP;
		}
		return out;
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

	public Rect settingsButton() {
		return new Rect(panel.x() + PAD, panel.y() + panel.height() - PAD - BUTTON, BUTTON, BUTTON);
	}

	public Rect craftAllButton() {
		return new Rect(panel.x() + panel.width() - PAD - BUTTON,
				panel.y() + panel.height() - PAD - BUTTON, BUTTON, BUTTON);
	}

	/** How wide a label may be on this row, or 0 when this panel is too narrow for labels. */
	public int labelBudget(Slot slot) {
		if (!showNames || slot == null) {
			return 0;
		}
		int used = ROW_PAD * 2 + (slot.collapse() != null ? COLLAPSE + 4 : 0) + ICON + 5 + MARKER + 5;
		return Math.max(0, slot.bounds().width() - used);
	}

	// --------------------------------------------------------------- scroll

	public double clampScroll(double scroll) {
		return Math.max(0, Math.min(scroll, maxScroll));
	}

	/** Whether this screen can host the sidebar at all. */
	public static boolean fits(int screenWidth, int screenHeight) {
		return screenWidth * WIDTH_FRACTION >= MIN_PANEL_WIDTH
				&& screenHeight - MARGIN * 2 >= FOOTER + ROW_HEIGHT * 3;
	}
}
