package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "these groups, these tabs" into the flat row list {@link SidebarLayout} draws.
 *
 * <p>Kept apart from {@code TreeTabs} and given only plain numbers, because ordering rules are
 * exactly the sort of thing that is easy to get subtly wrong and impossible to check by eye once
 * it is tangled up with EMI's tree objects. Everything here is testable in a second.
 *
 * <h2>The ordering rule</h2>
 *
 * Groups come first, each header immediately followed by its own tabs in tab order; ungrouped tabs
 * follow, in tab order. A tab whose group no longer exists is treated as ungrouped rather than
 * dropped — losing a tab because its group was deleted would be much worse than showing it loose.
 */
public final class SidebarRows {

	private SidebarRows() {
	}

	/** One tab, reduced to the two facts the ordering cares about. */
	public record TabRef(int index, int groupId) {
	}

	/** One group, reduced to the same. */
	public record GroupRef(int id, boolean collapsed) {
	}

	/**
	 * @param groups groups in display order
	 * @param tabs   tabs in tab order, each naming its group id or -1
	 * @return rows for {@link SidebarLayout}, and the tab index each TAB row came from
	 */
	public static Result build(List<GroupRef> groups, List<TabRef> tabs) {
		List<SidebarLayout.Row> rows = new ArrayList<>();
		List<Integer> sources = new ArrayList<>();

		List<GroupRef> safeGroups = groups == null ? List.of() : groups;
		List<TabRef> safeTabs = tabs == null ? List.of() : tabs;

		// Bucket the tabs by group, keeping tab order inside each bucket.
		Map<Integer, List<TabRef>> byGroup = new LinkedHashMap<>();
		List<TabRef> loose = new ArrayList<>();
		for (TabRef t : safeTabs) {
			boolean known = false;
			for (GroupRef g : safeGroups) {
				if (g.id() == t.groupId()) {
					known = true;
					break;
				}
			}
			if (known) {
				byGroup.computeIfAbsent(t.groupId(), k -> new ArrayList<>()).add(t);
			} else {
				// Includes groupId -1, and any tab pointing at a group that has been deleted.
				loose.add(t);
			}
		}

		for (GroupRef g : safeGroups) {
			rows.add(SidebarLayout.Row.group(g.id(), g.collapsed()));
			sources.add(-1);
			for (TabRef t : byGroup.getOrDefault(g.id(), List.of())) {
				rows.add(SidebarLayout.Row.tab(g.id()));
				sources.add(t.index());
			}
		}
		for (TabRef t : loose) {
			rows.add(SidebarLayout.Row.tab());
			sources.add(t.index());
		}
		return new Result(List.copyOf(rows), List.copyOf(sources));
	}

	/**
	 * The rows, plus a parallel list saying which tab each row is.
	 *
	 * <p>The parallel list is the whole point: {@link SidebarLayout} works in row indices and knows
	 * nothing about tabs, so something has to map a clicked row back to the tab it stands for.
	 */
	public record Result(List<SidebarLayout.Row> rows, List<Integer> tabIndices) {

		/** @return the tab index for a row, or -1 when that row is a group header. */
		public int tabAt(int rowIndex) {
			return rowIndex >= 0 && rowIndex < tabIndices.size() ? tabIndices.get(rowIndex) : -1;
		}

		/** @return the row index showing a given tab, or -1 when it is hidden or absent. */
		public int rowOf(int tabIndex) {
			return tabIndices.indexOf(tabIndex);
		}
	}
}
