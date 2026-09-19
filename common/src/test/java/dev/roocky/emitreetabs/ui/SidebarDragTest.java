package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.roocky.emitreetabs.ui.SidebarDrag.Drop;
import dev.roocky.emitreetabs.ui.SidebarRows.GroupRef;
import dev.roocky.emitreetabs.ui.SidebarRows.Result;
import dev.roocky.emitreetabs.ui.SidebarRows.TabRef;

class SidebarDragTest {

	private static List<TabRef> tabs(int... groupIds) {
		List<TabRef> out = new ArrayList<>();
		for (int i = 0; i < groupIds.length; i++) {
			out.add(new TabRef(i, groupIds[i]));
		}
		return out;
	}

	private static List<GroupRef> groups(int... ids) {
		List<GroupRef> out = new ArrayList<>();
		for (int id : ids) {
			out.add(new GroupRef(id, false));
		}
		return out;
	}

	@Test
	@DisplayName("dropping on a tab in the same group takes that tab's place")
	void reordersWithinAGroup() {
		// rows: [g7][t0][t1][t2]
		Result rows = SidebarRows.build(groups(7), tabs(7, 7, 7));
		Drop drop = SidebarDrag.resolve(rows, 2, 1, 3);
		assertNotNull(drop);
		assertEquals(7, drop.groupId());
		assertEquals(0, drop.moveTo());
	}

	@Test
	@DisplayName("dropping on a tab in another group joins that group")
	void movesBetweenGroups() {
		// rows: [g1][t0][g2][t1]
		Result rows = SidebarRows.build(groups(1, 2), tabs(1, 2));
		Drop drop = SidebarDrag.resolve(rows, 0, 3, 2);
		assertNotNull(drop);
		assertEquals(2, drop.groupId());
		assertEquals(1, drop.moveTo());
	}

	@Test
	@DisplayName("dropping on an ungrouped tab is how a tab leaves its group")
	void leavesAGroupViaALooseTab() {
		// rows: [g1][t0][t2(loose)]
		Result rows = SidebarRows.build(groups(1), tabs(1, 1, -1));
		Drop drop = SidebarDrag.resolve(rows, 0, 3, 3);
		assertNotNull(drop);
		assertEquals(SidebarDrag.LOOSE, drop.groupId());
		assertEquals(2, drop.moveTo());
	}

	@Test
	@DisplayName("dropping on a header joins that group at the top")
	void headerDropsAtTheTop() {
		// rows: [g1][t0][g2][t1][t2]
		Result rows = SidebarRows.build(groups(1, 2), tabs(1, 2, 2));
		Drop drop = SidebarDrag.resolve(rows, 0, 2, 3);
		assertNotNull(drop);
		assertEquals(2, drop.groupId());
		// Group 2's first tab is master index 1, so the dragged tab takes that place.
		assertEquals(1, drop.moveTo());
	}

	@Test
	@DisplayName("a header is the only way into an empty group, and asks for no move")
	void emptyGroupTakesTheAssignmentOnly() {
		// rows: [g1][t0][g2]  - group 2 has nothing in it
		Result rows = SidebarRows.build(groups(1, 2), tabs(1));
		Drop drop = SidebarDrag.resolve(rows, 0, 2, 1);
		assertNotNull(drop);
		assertEquals(2, drop.groupId());
		assertEquals(0, drop.moveTo(), "no tab to sit in front of, so stay put in the master list");
	}

	@Test
	@DisplayName("a collapsed group hides its tabs, and a header drop still joins it")
	void collapsedGroupIsStillATarget() {
		Result rows = SidebarRows.build(List.of(new GroupRef(1, true), new GroupRef(2, false)),
				tabs(1, 2));
		// rows: [g1(collapsed)][g2][t1] - group 1's tab is not drawn
		Drop drop = SidebarDrag.resolve(rows, 1, 0, 2);
		assertNotNull(drop);
		assertEquals(1, drop.groupId());
	}

	@Test
	@DisplayName("the empty space below the rows is the way out when every tab is grouped")
	void emptySpaceUngroups() {
		Result rows = SidebarRows.build(groups(1), tabs(1, 1));
		Drop drop = SidebarDrag.resolve(rows, 0, 99, 2);
		assertNotNull(drop);
		assertEquals(SidebarDrag.LOOSE, drop.groupId());
		assertEquals(1, drop.moveTo(), "goes to the end of the master list");
	}

	@Test
	@DisplayName("dropping a tab on itself changes nothing")
	void selfDropIsNull() {
		Result rows = SidebarRows.build(groups(1), tabs(1, 1, 1));
		assertNull(SidebarDrag.resolve(rows, 1, 2, 3));
	}

	@Test
	@DisplayName("a header drop that would not move the tab out of its own group changes nothing")
	void redundantHeaderDropIsNull() {
		Result rows = SidebarRows.build(groups(1), tabs(1, 1));
		// Row 0 is group 1's header; tab 0 is already group 1's first tab.
		assertNull(SidebarDrag.resolve(rows, 0, 0, 2));
	}

	@Test
	@DisplayName("out-of-range drags are refused rather than clamped into a wrong move")
	void refusesNonsense() {
		Result rows = SidebarRows.build(groups(1), tabs(1, 1));
		assertNull(SidebarDrag.resolve(rows, -1, 1, 2));
		assertNull(SidebarDrag.resolve(rows, 5, 1, 2));
		assertNull(SidebarDrag.resolve(null, 0, 1, 2));
	}

	@Test
	@DisplayName("with no groups at all it behaves exactly like the strip")
	void flatListIsAPlainMove() {
		Result rows = SidebarRows.build(List.of(), tabs(-1, -1, -1, -1));
		Drop drop = SidebarDrag.resolve(rows, 3, 0, 4);
		assertNotNull(drop);
		assertEquals(SidebarDrag.LOOSE, drop.groupId());
		assertEquals(0, drop.moveTo());
	}
}
