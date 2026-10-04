package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.roocky.emitreetabs.ui.SidebarLayout.Row;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowKind;
import dev.roocky.emitreetabs.ui.SidebarLayout.Slot;

/**
 * Screen sizes here are <em>logical</em> pixels, which is what a Screen is handed. 1920x1080 at GUI
 * scale 2 is a 960x540 screen; at scale 4 it is 480x270.
 */
class SidebarLayoutTest {

	private static SidebarLayout of(int w, int h, List<Row> rows) {
		return new SidebarLayout(w, h, rows, 0, true);
	}

	/** 1920x1080 at GUI scale 2. */
	private static SidebarLayout medium(List<Row> rows) {
		return of(960, 540, rows);
	}

	private static List<Row> plainTabs(int n) {
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			rows.add(Row.tab());
		}
		return rows;
	}

	@Nested
	@DisplayName("the floating panel")
	class Panel {

		@Test
		@DisplayName("takes about a fifth of the width, leaving the rest for the tree")
		void widthIsAFifth() {
			SidebarLayout l = medium(plainTabs(3));
			assertEquals(Math.round(960 * 0.2), l.panel.width());
			int leftForTree = l.screenWidth - l.panel.width() - SidebarLayout.MARGIN;
			assertTrue(leftForTree > l.screenWidth * 0.75,
					"the tree should keep about four fifths, got " + leftForTree);
		}

		@Test
		@DisplayName("floats clear of every screen edge")
		void floats() {
			SidebarLayout l = medium(plainTabs(3));
			assertEquals(SidebarLayout.MARGIN, l.panel.x(), "gap on the left");
			assertEquals(SidebarLayout.MARGIN, l.panel.y(), "gap on the top");
			assertEquals(SidebarLayout.MARGIN, l.screenHeight - (l.panel.y() + l.panel.height()),
					"gap on the bottom");
			assertTrue(l.panel.x() + l.panel.width() < l.screenWidth - SidebarLayout.MARGIN / 2,
					"gap on the right");
		}

		@Test
		@DisplayName("sits on the right when asked, with the same gaps")
		void rightHanded() {
			SidebarLayout l = new SidebarLayout(960, 540, plainTabs(3), 0, false);
			assertEquals(SidebarLayout.MARGIN,
					l.screenWidth - (l.panel.x() + l.panel.width()), "gap on the right");
			assertTrue(l.panel.x() > l.screenWidth / 2);
		}

		@Test
		@DisplayName("never eats more than a third of a small screen")
		void doesNotEatSmallScreens() {
			for (int w : new int[] { 320, 400, 480, 640, 960, 1920 }) {
				SidebarLayout l = of(w, 400, plainTabs(3));
				assertTrue(l.panel.width() <= Math.max(SidebarLayout.MIN_PANEL_WIDTH, w / 3),
						"panel took " + l.panel.width() + " of " + w);
			}
		}

		@Test
		@DisplayName("the footer never overlaps the rows")
		void footerIsClearOfRows() {
			SidebarLayout l = medium(plainTabs(40));
			Rect settings = l.settingsButton();
			Rect craftAll = l.craftAllButton();
			int viewportBottom = l.viewport.y() + l.viewport.height();
			assertTrue(settings.y() >= viewportBottom, "settings button overlaps the rows");
			assertTrue(craftAll.y() >= viewportBottom, "craft-all button overlaps the rows");
			assertTrue(settings.x() + settings.width() <= craftAll.x(), "the two buttons overlap");
			assertTrue(craftAll.x() + craftAll.width() <= l.panel.x() + l.panel.width(),
					"craft-all button escapes the panel");
		}

		@Test
		@DisplayName("knows when a screen is too small to host it")
		void fitsIsHonest() {
			assertTrue(SidebarLayout.fits(960, 540));
			assertFalse(SidebarLayout.fits(200, 540), "200px wide cannot host a fifth-width panel");
			assertFalse(SidebarLayout.fits(960, 100), "100px tall cannot host three rows");
		}
	}

	@Nested
	@DisplayName("rows")
	class Rows {

		@Test
		@DisplayName("stack in order without overlapping")
		void stackCleanly() {
			SidebarLayout l = medium(plainTabs(8));
			List<Slot> s = l.slots();
			assertEquals(8, s.size());
			for (int i = 1; i < s.size(); i++) {
				Rect prev = s.get(i - 1).bounds();
				Rect cur = s.get(i).bounds();
				assertTrue(cur.y() >= prev.y() + prev.height(),
						"row " + i + " overlaps row " + (i - 1));
			}
		}

		@Test
		@DisplayName("every row stays inside the panel horizontally")
		void stayInsideThePanel() {
			List<Row> rows = List.of(Row.group(0, false), Row.tab(0), Row.tab(0), Row.tab());
			SidebarLayout l = medium(rows);
			for (Slot s : l.slots()) {
				assertTrue(s.bounds().x() >= l.panel.x(), "row escapes left");
				assertTrue(s.bounds().x() + s.bounds().width() <= l.panel.x() + l.panel.width(),
						"row escapes right");
			}
		}

		@Test
		@DisplayName("the marker sits inside its own row")
		void markerIsInsideItsRow() {
			SidebarLayout l = medium(plainTabs(5));
			for (Slot s : l.slots()) {
				Rect b = s.bounds();
				Rect m = s.marker();
				assertTrue(m.x() >= b.x() && m.x() + m.width() <= b.x() + b.width(),
						"marker escapes its row horizontally");
				assertTrue(m.y() >= b.y() && m.y() + m.height() <= b.y() + b.height(),
						"marker escapes its row vertically");
			}
		}

		@Test
		@DisplayName("the marker keeps the row's padding, it is not flush to the edge")
		void markerRespectsPadding() {
			SidebarLayout l = medium(plainTabs(4));
			for (Slot s : l.slots()) {
				int rightGap = (s.bounds().x() + s.bounds().width()) - (s.marker().x() + s.marker().width());
				assertEquals(SidebarLayout.ROW_PAD, rightGap,
						"the marker should sit one row-padding in from the edge");
			}
		}

		@Test
		@DisplayName("a group insets its members on both sides, the way padding does")
		void membersAreInsetBothSides() {
			List<Row> rows = List.of(Row.group(0, false), Row.tab(0), Row.tab());
			SidebarLayout l = medium(rows);
			List<Slot> s = l.slots();
			Rect header = s.get(0).bounds();
			Rect member = s.get(1).bounds();
			Rect loose = s.get(2).bounds();

			assertEquals(header.x(), loose.x(), "an ungrouped tab should not be inset");
			assertEquals(header.x() + SidebarLayout.GROUP_PAD, member.x(), "inset on the left");
			assertEquals(header.x() + header.width() - SidebarLayout.GROUP_PAD,
					member.x() + member.width(),
					"inset on the right too — a left-only inset reads as a shunted row, not a contained one");
		}

		@Test
		@DisplayName("a group header spans the full content width, as its own background")
		void headerFillsTheWidth() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab(0)));
			assertEquals(l.viewport.width(), l.slots().get(0).bounds().width());
			assertTrue(l.slots().get(1).bounds().width() < l.viewport.width(),
					"a member should be narrower than its header");
		}
	}

	@Nested
	@DisplayName("groups")
	class Groups {

		@Test
		@DisplayName("collapsing hides the members but never the header")
		void collapseHidesMembers() {
			List<Row> open = List.of(Row.group(0, false), Row.tab(0), Row.tab(0), Row.tab());
			List<Row> shut = List.of(Row.group(0, true), Row.tab(0), Row.tab(0), Row.tab());
			assertEquals(4, medium(open).slots().size());
			List<Slot> collapsed = medium(shut).slots();
			assertEquals(2, collapsed.size(), "header plus the ungrouped tab should remain");
			assertEquals(RowKind.GROUP, collapsed.get(0).row().kind());
			assertEquals(-1, collapsed.get(1).row().groupIndex(), "the loose tab must survive");
		}

		@Test
		@DisplayName("collapsing one group leaves another alone")
		void collapseIsPerGroup() {
			List<Row> rows = List.of(
					Row.group(0, true), Row.tab(0), Row.tab(0),
					Row.group(1, false), Row.tab(1), Row.tab(1));
			List<Slot> s = medium(rows).slots();
			assertEquals(4, s.size(), "group 0 folded, group 1 open");
			assertEquals(1, s.get(1).row().groupIndex());
			assertEquals(1, s.get(3).row().groupIndex());
		}

		@Test
		@DisplayName("the enclosing border covers the header and every member")
		void borderEnclosesTheGroup() {
			List<Row> rows = List.of(Row.group(0, false), Row.tab(0), Row.tab(0));
			SidebarLayout l = medium(rows);
			Rect box = l.groupBounds(0);
			assertNotNull(box);
			for (Slot s : l.slots()) {
				if (s.row().groupIndex() != 0) {
					continue;
				}
				assertTrue(s.bounds().y() >= box.y(), "a row sits above the group border");
				assertTrue(s.bounds().y() + s.bounds().height() <= box.y() + box.height(),
						"a row sits below the group border");
			}
		}

		@Test
		@DisplayName("a collapsed group has no border, because there is nothing to enclose")
		void noBorderWhenCollapsed() {
			assertNull(medium(List.of(Row.group(0, true), Row.tab(0))).groupBounds(0));
		}

		@Test
		@DisplayName("only a group header offers a fold arrow")
		void onlyGroupsFold() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab(0)));
			assertNotNull(l.slots().get(0).collapse());
			assertNull(l.slots().get(1).collapse(), "a tab must not offer a fold arrow");
		}
	}

	@Nested
	@DisplayName("hit testing")
	class HitTesting {

		@Test
		@DisplayName("every row is found at its own centre")
		void rowsAreFoundWhereDrawn() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab(0), Row.tab()));
			for (Slot s : l.slots()) {
				Rect b = s.bounds();
				Slot hit = l.slotAt(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0);
				assertNotNull(hit, "nothing found at the centre of row " + s.index());
				assertEquals(s.index(), hit.index());
			}
		}

		@Test
		@DisplayName("the footer is not a row, so the buttons never select a tab")
		void footerIsNotARow() {
			SidebarLayout l = medium(plainTabs(40));
			Rect b = l.settingsButton();
			assertNull(l.slotAt(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0),
					"the settings button hit-tested as a tab row");
		}

		@Test
		@DisplayName("the marker only answers while its own row is under the pointer")
		void markerNeedsItsRow() {
			SidebarLayout l = medium(plainTabs(4));
			Slot first = l.slots().get(0);
			Rect m = first.marker();
			double mx = m.x() + m.width() / 2.0;
			double my = m.y() + m.height() / 2.0;
			assertTrue(l.overMarker(first, mx, my));
			// The same x, but down on a different row, must not toggle the first row.
			Slot second = l.slots().get(1);
			assertFalse(l.overMarker(first, mx, second.bounds().y() + 2.0),
					"the first row's marker answered for a point on the second row");
		}

		@Test
		@DisplayName("the fold arrow is a smaller target than the header it sits on")
		void foldArrowIsItsOwnTarget() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab(0)));
			Slot header = l.slots().get(0);
			Rect c = header.collapse();
			assertTrue(l.overCollapse(header, c.x() + 1.0, c.y() + 1.0));
			// Clicking the header's right half selects or toggles, it does not fold.
			double farRight = header.bounds().x() + header.bounds().width() - 3.0;
			assertFalse(l.overCollapse(header, farRight, c.y() + 1.0));
		}

		@Test
		@DisplayName("points outside the panel hit nothing")
		void outsideThePanel() {
			SidebarLayout l = medium(plainTabs(5));
			assertFalse(l.isOver(l.panel.x() - 1, l.panel.y() + 10), "left of the panel");
			assertFalse(l.isOver(l.panel.x() + l.panel.width() + 1, l.panel.y() + 10), "right of it");
			assertFalse(l.isOver(l.panel.x() + 10, l.panel.y() - 1), "above it");
			assertTrue(l.isOver(l.panel.x() + 10, l.panel.y() + 10), "inside it");
		}
	}

	@Nested
	@DisplayName("scrolling")
	class Scrolling {

		@Test
		@DisplayName("a few rows need no scrolling")
		void noScrollWhenEverythingFits() {
			SidebarLayout l = medium(plainTabs(4));
			assertEquals(0, l.maxScroll);
		}

		@Test
		@DisplayName("many rows do, and scroll is clamped to them")
		void clamped() {
			SidebarLayout l = medium(plainTabs(80));
			assertTrue(l.maxScroll > 0);
			assertEquals(0, l.clampScroll(-50));
			assertEquals(l.maxScroll, l.clampScroll(l.maxScroll + 500));
		}

		@Test
		@DisplayName("collapsing a group shortens the content, so less scrolling is needed")
		void collapsingShortensContent() {
			List<Row> open = new ArrayList<>();
			List<Row> shut = new ArrayList<>();
			open.add(Row.group(0, false));
			shut.add(Row.group(0, true));
			for (int i = 0; i < 30; i++) {
				open.add(Row.tab(0));
				shut.add(Row.tab(0));
			}
			assertTrue(medium(shut).contentHeight < medium(open).contentHeight);
			assertEquals(0, medium(shut).maxScroll, "a folded group of 30 should need no scrolling");
		}

		@Test
		@DisplayName("scrolling moves rows without changing their size or order")
		void scrollingOnlyTranslates() {
			List<Row> rows = plainTabs(60);
			SidebarLayout top = new SidebarLayout(960, 540, rows, 0, true);
			SidebarLayout down = new SidebarLayout(960, 540, rows, 100, true);
			assertEquals(top.slots().size(), down.slots().size());
			for (int i = 0; i < top.slots().size(); i++) {
				Rect a = top.slots().get(i).bounds();
				Rect b = down.slots().get(i).bounds();
				assertEquals(a.height(), b.height());
				assertEquals(a.width(), b.width());
				assertEquals(a.y() - 100, b.y(), "row " + i + " did not translate by the scroll");
			}
		}

		@Test
		@DisplayName("the visible set only contains rows actually inside the viewport")
		void visibleMeansVisible() {
			List<Row> rows = plainTabs(80);
			for (double scroll : new double[] { 0, 60, 300 }) {
				SidebarLayout l = new SidebarLayout(960, 540, rows, scroll, true);
				for (Slot s : l.visibleSlots()) {
					Rect b = s.bounds();
					assertTrue(b.y() + b.height() > l.viewport.y()
									&& b.y() < l.viewport.y() + l.viewport.height(),
							"a row outside the viewport was reported visible at scroll " + scroll);
				}
				assertTrue(l.visibleSlots().size() <= l.slots().size());
			}
		}
	}

	@Nested
	@DisplayName("labels")
	class Labels {

		@Test
		@DisplayName("a fifth of a normal screen is wide enough for names")
		void namesFitAtNormalSizes() {
			assertTrue(medium(plainTabs(3)).showNames,
					"960px wide should give a panel with room for names");
			assertTrue(medium(plainTabs(3)).labelBudget(medium(plainTabs(3)).slots().get(0)) > 0);
		}

		@Test
		@DisplayName("a narrow panel drops names rather than overlapping the marker")
		void narrowPanelDropsNames() {
			SidebarLayout l = of(320, 400, plainTabs(3));
			if (!l.showNames) {
				assertEquals(0, l.labelBudget(l.slots().get(0)));
			}
		}

		@Test
		@DisplayName("a label never runs under the marker or the fold arrow")
		void budgetLeavesRoomForControls() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab(0)));
			for (Slot s : l.slots()) {
				int budget = l.labelBudget(s);
				int controls = SidebarLayout.ROW_PAD * 2
						+ (s.collapse() != null ? SidebarLayout.COLLAPSE + 4 : 0)
						+ SidebarLayout.ICON + 5 + SidebarLayout.MARKER + 5;
				assertTrue(budget + controls <= s.bounds().width(),
						"the label budget overlaps the row's controls");
			}
		}

		@Test
		@DisplayName("a group header has less label room than a tab, because it also folds")
		void headerPaysForItsArrow() {
			SidebarLayout l = medium(List.of(Row.group(0, false), Row.tab()));
			assertTrue(l.labelBudget(l.slots().get(0)) < l.labelBudget(l.slots().get(1)),
					"the fold arrow has to cost the header some label width");
		}
	}

	@Test
	@DisplayName("an empty sidebar is harmless")
	void emptyIsSafe() {
		SidebarLayout l = medium(List.of());
		assertEquals(0, l.slots().size());
		assertEquals(0, l.contentHeight);
		assertEquals(0, l.maxScroll);
		assertNull(l.slotAt(l.viewport.x() + 5, l.viewport.y() + 5));
		assertNotNull(l.settingsButton());
	}

	@Test
	@DisplayName("null rows are treated as none, not as a crash")
	void nullRowsAreSafe() {
		SidebarLayout l = new SidebarLayout(960, 540, null, 0, true);
		assertEquals(0, l.slots().size());
	}
}
