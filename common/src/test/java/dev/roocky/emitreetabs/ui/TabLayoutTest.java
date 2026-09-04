package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.roocky.emitreetabs.ui.TabLayout.Density;

/**
 * The screen widths used here are <em>logical</em> pixels, which is what Minecraft hands a Screen —
 * not the monitor's. A 1920x1080 monitor at GUI scale 4 gives a 480x270 screen, and that is the
 * case where the strip actually struggles, so most of these tests use it.
 */
class TabLayoutTest {

	/** 1920x1080 at GUI scale 4. The configuration the tab bar was reported broken in. */
	private static TabLayout small(int count) {
		return new TabLayout(480, 270, count, false);
	}

	/** 1920x1080 at GUI scale 2. */
	private static TabLayout medium(int count) {
		return new TabLayout(960, 540, count, false);
	}

	@Nested
	@DisplayName("the sizing rule")
	class Sizing {

		@Test
		@DisplayName("a few tabs grow, but stop at the cap rather than stretching across the bar")
		void fewTabsGrowToCap() {
			TabLayout l = medium(3);
			assertEquals(TabLayout.MAX_TAB_WIDTH, l.tabWidth);
			assertEquals(Density.COMFORTABLE, l.density);
			assertFalse(l.scrolling);
		}

		@Test
		@DisplayName("tabs never fall below the floor; the strip scrolls instead")
		void neverBelowFloor() {
			// Enough tabs that an even split would be a few pixels each.
			for (int count = 1; count <= 200; count++) {
				TabLayout l = small(count);
				assertTrue(l.tabWidth >= TabLayout.MIN_TAB_WIDTH,
						"width fell below the floor at count=" + count + ": " + l.tabWidth);
			}
		}

		@Test
		@DisplayName("hitting the floor is exactly what turns scrolling on")
		void floorImpliesScrolling() {
			for (int count = 1; count <= 200; count++) {
				TabLayout l = small(count);
				assertEquals(l.tabWidth == TabLayout.MIN_TAB_WIDTH && l.maxScroll > 0, l.scrolling,
						"scrolling disagreed with the floor at count=" + count);
			}
		}

		@Test
		@DisplayName("density follows from width, so a tab never promises what it cannot draw")
		void densityFollowsWidth() {
			for (int count = 1; count <= 200; count++) {
				TabLayout l = small(count);
				Density expected = l.tabWidth >= TabLayout.COMFORTABLE_WIDTH ? Density.COMFORTABLE
						: l.tabWidth >= TabLayout.COMPACT_WIDTH ? Density.COMPACT
						: Density.ICON;
				assertEquals(expected, l.density, "at count=" + count + " width=" + l.tabWidth);
			}
		}

		@Test
		@DisplayName("width decreases monotonically as tabs are added, then flattens at the floor")
		void widthIsMonotonic() {
			int previous = Integer.MAX_VALUE;
			for (int count = 1; count <= 200; count++) {
				int width = small(count).tabWidth;
				assertTrue(width <= previous,
						"width grew from " + previous + " to " + width + " at count=" + count);
				previous = width;
			}
			assertEquals(TabLayout.MIN_TAB_WIDTH, previous);
		}

		@Test
		@DisplayName("zero tabs does not divide by zero")
		void emptyIsSafe() {
			TabLayout l = small(0);
			assertEquals(0, l.count);
			assertTrue(l.tabWidth > 0);
			assertEquals(0, l.maxScroll);
			assertEquals(-1, l.tabAt(100, 5, 0));
		}

		@Test
		@DisplayName("an absurdly narrow screen still produces a usable strip")
		void tinyScreen() {
			TabLayout l = new TabLayout(80, 270, 5, false);
			assertTrue(l.tabWidth >= TabLayout.MIN_TAB_WIDTH);
			assertTrue(l.stripWidth >= TabLayout.MIN_TAB_WIDTH);
		}
	}

	@Nested
	@DisplayName("the close button")
	class Close {

		@Test
		@DisplayName("is reachable at every tab count — the failure that started all of this")
		void alwaysReachable() {
			for (int count = 1; count <= 200; count++) {
				TabLayout l = small(count);
				// Hovering is what the player does before closing, so that is the state to check.
				TabLayout.Rect r = l.closeRect(0, 0, true, false);
				assertNotNull(r, "no close target at count=" + count);
				assertTrue(r.width() > 0 && r.height() > 0, "empty close target at count=" + count);
				// And it must sit inside the tab it belongs to.
				int x = l.tabX(0, 0);
				assertTrue(r.x() >= x, "close target left of its tab at count=" + count);
				assertTrue(r.x() + r.width() <= x + l.tabWidth,
						"close target past its tab at count=" + count);
			}
		}

		@Test
		@DisplayName("at icon density it is a corner badge, so the rest of the tab still selects")
		void iconDensityLeavesRoomToSelect() {
			TabLayout l = small(30);
			assertEquals(Density.ICON, l.density);
			TabLayout.Rect r = l.closeRect(0, 0, true, false);
			assertNotNull(r);
			assertTrue(r.width() < l.tabWidth,
					"the badge covers the whole tab, so clicking it could never select");
			// A click on the tab's left half is a selection, not a close.
			assertFalse(l.overClose(0, l.tabX(0, 0) + 2, l.barY + 10, 0, true, false));
		}

		@Test
		@DisplayName("at icon density it appears only on hover, for the active tab too")
		void iconDensityShowsOnHoverOnly() {
			// A permanent badge on a 28px tab would sit on top of the icon, and the active tab is
			// already marked by its accent bar. Hover reveals it; middle click closes regardless.
			TabLayout l = small(30);
			assertEquals(Density.ICON, l.density);
			assertNull(l.closeRect(0, 0, false, false), "idle tab should show no close badge");
			assertNull(l.closeRect(0, 0, false, true), "active tab should not show one either");
			assertNotNull(l.closeRect(0, 0, true, false), "hover should reveal it");
		}

		@Test
		@DisplayName("the active tab keeps its close button at the roomier densities")
		void activeKeepsCloseWhenThereIsRoom() {
			TabLayout l = medium(3);
			assertEquals(Density.COMFORTABLE, l.density);
			assertNotNull(l.closeRect(0, 0, false, true));
		}
	}

	@Nested
	@DisplayName("hit testing")
	class HitTesting {

		@Test
		@DisplayName("every point inside the strip maps to the tab drawn there")
		void pointsAgreeWithDrawnPositions() {
			TabLayout l = small(6);
			for (int i = 0; i < l.count; i++) {
				int x = l.tabX(i, 0);
				assertEquals(i, l.tabAt(x, l.barY + 10, 0), "left edge of tab " + i);
				assertEquals(i, l.tabAt(x + l.tabWidth - 1, l.barY + 10, 0), "right edge of tab " + i);
			}
		}

		@Test
		@DisplayName("the strip's right edge is exclusive")
		void rightEdgeIsExclusive() {
			// A click one pixel past the strip used to select a clipped tab.
			TabLayout l = small(40);
			double justPast = l.stripLeft + l.stripWidth;
			assertEquals(-1, l.tabAt(justPast, l.barY + 10, 0));
			assertTrue(l.tabAt(justPast - 1, l.barY + 10, 0) >= 0);
		}

		@Test
		@DisplayName("clicks above and below the bar miss it")
		void verticalBounds() {
			TabLayout l = small(4);
			assertEquals(-1, l.tabAt(l.stripLeft + 1, l.barY - 1, 0));
			assertEquals(-1, l.tabAt(l.stripLeft + 1, l.barY + TabLayout.HEIGHT, 0));
			assertTrue(l.tabAt(l.stripLeft + 1, l.barY + TabLayout.HEIGHT - 1, 0) >= 0);
		}

		@Test
		@DisplayName("a bar pinned to the bottom hit-tests where it is drawn")
		void bottomBar() {
			TabLayout l = new TabLayout(480, 270, 4, true);
			assertEquals(270 - TabLayout.HEIGHT, l.barY);
			assertTrue(l.tabAt(l.stripLeft + 1, l.barY + 1, 0) >= 0);
			assertEquals(-1, l.tabAt(l.stripLeft + 1, l.barY - 1, 0));
		}

		@Test
		@DisplayName("the add and craft-all buttons do not overlap each other or the strip")
		void buttonsDoNotOverlap() {
			for (int width : new int[] { 320, 480, 640, 960, 1920 }) {
				TabLayout l = new TabLayout(width, 270, 5, false);
				assertTrue(l.allButtonX() + TabLayout.ALL_BUTTON_WIDTH <= l.addButtonX(),
						"buttons overlap at width=" + width);
				assertTrue(l.stripLeft + l.stripWidth <= l.allButtonX() + TabLayout.ARROW_WIDTH,
						"strip runs into the buttons at width=" + width);
				assertTrue(l.addButtonX() + TabLayout.ADD_BUTTON_WIDTH <= width,
						"add button off screen at width=" + width);
			}
		}
	}

	@Nested
	@DisplayName("dragging to reorder")
	class Dragging {

		@Test
		@DisplayName("a drop lands on the tab it was dragged over, with the arrows showing")
		void dropMatchesPositionWhileScrolling() {
			// This is the regression. The drop used to be measured from PAD while everything else
			// measured from stripLeft; those differ by the arrow width exactly when the arrows are
			// visible, which is exactly when there are enough tabs to want to reorder.
			TabLayout l = small(40);
			assertTrue(l.scrolling, "this test is meaningless unless the arrows are showing");
			for (double scroll : new double[] { 0, 30, 137.5, l.maxScroll }) {
				for (int i = 0; i < 8; i++) {
					double centre = l.tabX(i, scroll) + l.tabWidth / 2.0;
					if (centre < l.stripLeft || centre >= l.stripLeft + l.stripWidth) {
						continue;
					}
					assertEquals(l.tabAt(centre, l.barY + 10, scroll), l.dropTarget(centre, scroll),
							"drop disagreed with hover at index " + i + " scroll " + scroll);
				}
			}
		}

		@Test
		@DisplayName("a drop is clamped into range rather than throwing")
		void dropIsClamped() {
			TabLayout l = small(6);
			assertEquals(0, l.dropTarget(-9999, 0));
			assertEquals(5, l.dropTarget(9999, 0));
		}

		@Test
		@DisplayName("dropping with no tabs is harmless")
		void dropWithNoTabs() {
			assertEquals(0, small(0).dropTarget(100, 0));
		}
	}

	@Nested
	@DisplayName("scrolling")
	class Scrolling {

		@Test
		@DisplayName("there is nothing to scroll when everything fits")
		void noScrollWhenEverythingFits() {
			TabLayout l = medium(3);
			assertEquals(0, l.maxScroll);
			assertEquals(0, l.clampScroll(500));
		}

		@Test
		@DisplayName("scroll is clamped to the range that keeps tabs on screen")
		void clamped() {
			TabLayout l = small(40);
			assertEquals(0, l.clampScroll(-100));
			assertEquals(l.maxScroll, l.clampScroll(l.maxScroll + 100));
		}

		@Test
		@DisplayName("ensureVisible brings a tab fully into the strip from either side")
		void ensureVisibleWorksBothWays() {
			TabLayout l = small(40);
			double atEnd = l.ensureVisible(39, 0);
			assertTrue(atEnd > 0);
			assertTrue(l.tabX(39, atEnd) + l.tabWidth <= l.stripLeft + l.stripWidth + 1,
					"last tab still clipped after scrolling to it");

			double atStart = l.ensureVisible(0, atEnd);
			assertEquals(0, atStart);
			assertEquals(l.stripLeft, l.tabX(0, atStart));
		}

		@Test
		@DisplayName("a tab that is already visible does not move the strip")
		void alreadyVisibleIsANoOp() {
			TabLayout l = small(40);
			assertEquals(0, l.ensureVisible(0, 0));
			assertEquals(0, l.ensureVisible(1, 0));
		}

		@Test
		@DisplayName("the visible range covers every tab that is actually on screen")
		void visibleRangeCoversWhatIsDrawn() {
			TabLayout l = small(40);
			for (double scroll : new double[] { 0, 50, 200, l.maxScroll }) {
				int first = l.firstVisible(scroll);
				int last = l.lastVisible(scroll);
				for (int i = 0; i < l.count; i++) {
					int x = l.tabX(i, scroll);
					boolean onScreen = x + l.tabWidth > l.stripLeft && x < l.stripLeft + l.stripWidth;
					if (onScreen) {
						assertTrue(i >= first && i <= last,
								"tab " + i + " is drawn but outside [" + first + "," + last
										+ "] at scroll " + scroll);
					}
				}
			}
		}
	}

	@Nested
	@DisplayName("labels")
	class Labels {

		@Test
		@DisplayName("icon density asks for no label at all")
		void noLabelAtIconDensity() {
			TabLayout l = small(30);
			assertEquals(Density.ICON, l.density);
			assertEquals(0, l.labelBudget(true));
		}

		@Test
		@DisplayName("a label budget is never negative and never exceeds the tab")
		void budgetIsSane() {
			for (int count = 1; count <= 200; count++) {
				TabLayout l = small(count);
				for (boolean close : new boolean[] { true, false }) {
					int budget = l.labelBudget(close);
					assertTrue(budget >= 0, "negative budget at count=" + count);
					assertTrue(budget <= l.tabWidth, "budget wider than the tab at count=" + count);
				}
			}
		}

		@Test
		@DisplayName("making room for a close button shrinks the label, never the other way round")
		void closeButtonCostsLabelWidth() {
			TabLayout l = medium(3);
			assertTrue(l.labelBudget(true) < l.labelBudget(false));
		}
	}
}
