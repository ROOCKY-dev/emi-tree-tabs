package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.roocky.emitreetabs.ui.SidebarLayout.Row;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowKind;
import dev.roocky.emitreetabs.ui.SidebarRows.GroupRef;
import dev.roocky.emitreetabs.ui.SidebarRows.Result;
import dev.roocky.emitreetabs.ui.SidebarRows.TabRef;

class SidebarRowsTest {

	private static List<TabRef> tabs(int... groupIds) {
		List<TabRef> out = new ArrayList<>();
		for (int i = 0; i < groupIds.length; i++) {
			out.add(new TabRef(i, groupIds[i]));
		}
		return out;
	}

	@Test
	@DisplayName("a group header is followed by its own tabs, in tab order")
	void groupsOwnTheRowsBelowThem() {
		Result r = SidebarRows.build(
				List.of(new GroupRef(7, false)),
				tabs(7, -1, 7));

		assertEquals(4, r.rows().size());
		assertEquals(RowKind.GROUP, r.rows().get(0).kind());
		assertEquals(7, r.rows().get(1).groupIndex());
		assertEquals(0, r.tabAt(1), "first member should be tab 0");
		assertEquals(7, r.rows().get(2).groupIndex());
		assertEquals(2, r.tabAt(2), "second member should be tab 2, keeping tab order");
		assertEquals(-1, r.rows().get(3).groupIndex(), "the ungrouped tab comes last");
		assertEquals(1, r.tabAt(3));
	}

	@Test
	@DisplayName("ungrouped tabs come after every group")
	void looseTabsComeLast() {
		Result r = SidebarRows.build(
				List.of(new GroupRef(1, false), new GroupRef(2, false)),
				tabs(-1, 1, 2, -1));

		List<Row> rows = r.rows();
		int firstLoose = -1;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).kind() == RowKind.TAB && rows.get(i).groupIndex() == -1) {
				firstLoose = i;
				break;
			}
		}
		assertTrue(firstLoose > 0);
		for (int i = firstLoose; i < rows.size(); i++) {
			assertEquals(RowKind.TAB, rows.get(i).kind(), "a group appeared after the loose tabs");
			assertEquals(-1, rows.get(i).groupIndex());
		}
	}

	@Test
	@DisplayName("a tab whose group was deleted is shown loose, never dropped")
	void orphansSurvive() {
		Result r = SidebarRows.build(
				List.of(new GroupRef(1, false)),
				tabs(1, 99, 99));

		long tabRows = r.rows().stream().filter(x -> x.kind() == RowKind.TAB).count();
		assertEquals(3, tabRows, "every tab must appear somewhere");
		assertEquals(-1, r.rows().get(2).groupIndex(), "the orphan should be loose");
		assertEquals(-1, r.rows().get(3).groupIndex());
	}

	@Test
	@DisplayName("every tab maps back to itself, and headers map to nothing")
	void mappingIsExact() {
		Result r = SidebarRows.build(
				List.of(new GroupRef(1, false), new GroupRef(2, true)),
				tabs(1, 2, -1, 1));

		for (int i = 0; i < r.rows().size(); i++) {
			if (r.rows().get(i).kind() == RowKind.GROUP) {
				assertEquals(-1, r.tabAt(i), "a header should map to no tab");
			} else {
				int t = r.tabAt(i);
				assertTrue(t >= 0 && t < 4, "row " + i + " mapped to tab " + t);
				assertEquals(i, r.rowOf(t), "rowOf should invert tabAt");
			}
		}
	}

	@Test
	@DisplayName("collapsed is carried through to the layout, not applied here")
	void collapseIsThelayoutsJob() {
		Result r = SidebarRows.build(List.of(new GroupRef(3, true)), tabs(3, 3));
		assertTrue(r.rows().get(0).collapsed());
		assertEquals(3, r.rows().size(), "rows are still produced; hiding them is SidebarLayout's job");

		// And the layout is what actually hides them.
		SidebarLayout l = new SidebarLayout(960, 540, r.rows(), 0, true);
		assertEquals(1, l.slots().size(), "a collapsed group should draw only its header");
	}

	@Test
	@DisplayName("an empty group still shows its header, so it can be filled")
	void emptyGroupsAreVisible() {
		Result r = SidebarRows.build(List.of(new GroupRef(5, false)), tabs(-1));
		assertEquals(RowKind.GROUP, r.rows().get(0).kind());
		assertEquals(2, r.rows().size());
	}

	@Test
	@DisplayName("no groups at all is just the tabs, in order")
	void noGroups() {
		Result r = SidebarRows.build(List.of(), tabs(-1, -1, -1));
		assertEquals(3, r.rows().size());
		for (int i = 0; i < 3; i++) {
			assertEquals(RowKind.TAB, r.rows().get(i).kind());
			assertEquals(i, r.tabAt(i));
		}
	}

	@Test
	@DisplayName("nulls are treated as empty, not as a crash")
	void nullsAreSafe() {
		assertEquals(0, SidebarRows.build(null, null).rows().size());
		assertEquals(0, SidebarRows.build(List.of(), null).rows().size());
		assertEquals(1, SidebarRows.build(List.of(new GroupRef(1, false)), null).rows().size());
	}
}
