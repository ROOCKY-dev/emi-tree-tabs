package dev.roocky.emitreetabs.ui;

import java.util.List;

import dev.roocky.emitreetabs.ui.SidebarLayout.Row;
import dev.roocky.emitreetabs.ui.SidebarLayout.RowKind;

/**
 * Where a tab dragged around the sidebar would land.
 *
 * <p>Imports nothing from Minecraft or EMI, for the reason the rest of the layout does not: every
 * tab bar bug so far has been a geometry bug, and geometry is the one piece testable without
 * launching the game.
 *
 * <h2>Why this is not the strip's drop arithmetic</h2>
 *
 * On the strip a drop is one number, because tabs are one flat list. The sidebar's rows are
 * <em>derived</em> — {@link SidebarRows} puts each group's tabs under its header and the ungrouped
 * ones at the end — so a drop says two things at once: which group the tab joins, and where it sits
 * among that group's tabs. Both have to come out of one gesture.
 *
 * <h2>The rule</h2>
 *
 * A tab lands where the row under the pointer is, which is the same promise the strip makes:
 *
 * <ul>
 *   <li>On another tab — join that tab's group and take its place. Dropping on an ungrouped tab is
 *       therefore how a tab leaves a group.</li>
 *   <li>On a group header — join that group at the top. A header is the only way into a group that
 *       is empty or collapsed, which is why it is a target at all.</li>
 *   <li>Below the last row — leave every group and go to the end. Without this there would be no
 *       way out of a group once every tab is in one.</li>
 * </ul>
 */
public final class SidebarDrag {

	/** The group id meaning "belongs to no group". */
	public static final int LOOSE = -1;

	/**
	 * A resolved drop.
	 *
	 * @param groupId the group the tab should join, or {@link #LOOSE}
	 * @param moveTo  the index the tab should move to in the master tab list
	 */
	public record Drop(int groupId, int moveTo) {
	}

	private SidebarDrag() {
	}

	/**
	 * @param rows      the rows currently drawn
	 * @param from      the tab being dragged, as a master-list index
	 * @param targetRow the row under the pointer, or {@code rows.size()} or more for the empty
	 *                  space below the last one
	 * @param tabCount  how many tabs there are in total
	 * @return where the tab should end up, or null when the gesture would change nothing
	 */
	public static Drop resolve(SidebarRows.Result rows, int from, int targetRow, int tabCount) {
		if (rows == null || from < 0 || from >= tabCount) {
			return null;
		}
		List<Row> list = rows.rows();
		// Past the last row: the empty panel below. Leave every group, go to the end.
		if (targetRow < 0 || targetRow >= list.size()) {
			return drop(LOOSE, tabCount - 1, rows, from);
		}
		Row row = list.get(targetRow);
		if (row.kind() == RowKind.GROUP) {
			int first = firstTabOf(rows, targetRow, row.groupIndex());
			// An empty or collapsed group has no tab to sit in front of, so the master position
			// does not matter; only the group assignment does.
			return drop(row.groupIndex(), first < 0 ? from : first, rows, from);
		}
		int target = rows.tabAt(targetRow);
		if (target < 0) {
			return null;
		}
		return drop(row.groupIndex(), target, rows, from);
	}

	/** The master index of a group's first tab, or -1 when it has none showing. */
	private static int firstTabOf(SidebarRows.Result rows, int headerRow, int groupId) {
		List<Row> list = rows.rows();
		for (int i = headerRow + 1; i < list.size(); i++) {
			Row row = list.get(i);
			if (row.kind() == RowKind.GROUP) {
				return -1;
			}
			if (row.groupIndex() == groupId) {
				return rows.tabAt(i);
			}
		}
		return -1;
	}

	/** Null rather than a no-op, so the caller can skip the sound and the save. */
	private static Drop drop(int groupId, int moveTo, SidebarRows.Result rows, int from) {
		int currentRow = rows.rowOf(from);
		int currentGroup = currentRow >= 0 && currentRow < rows.rows().size()
				? rows.rows().get(currentRow).groupIndex() : LOOSE;
		if (groupId == currentGroup && moveTo == from) {
			return null;
		}
		return new Drop(groupId, moveTo);
	}
}
