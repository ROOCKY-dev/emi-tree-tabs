package dev.ahmed.emitreetabs.sidebar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.ahmed.emitreetabs.TreeTabsConfig;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.network.chat.Component;

/**
 * The sectioned form of the crafting list, and the layout that turns it into slots.
 *
 * <p>Sections are built during aggregation (which already walks every tree, so working out what is
 * shared costs nothing extra) and laid out lazily against whichever sidebar is asking, because only
 * the panel knows how wide its rows are.
 */
public final class CraftingGroups {

	/** One titled section of the crafting list. */
	public static final class Group {
		public final String id;
		public final Component label;
		public final List<EmiIngredient> entries = new ArrayList<>();

		public Group(String id, Component label) {
			this.id = id;
			this.label = label;
		}
	}

	private static final List<Group> GROUPS = new ArrayList<>();
	private static final Set<String> COLLAPSED = new HashSet<>();

	private CraftingGroups() {
	}

	public static void begin() {
		GROUPS.clear();
	}

	public static Group group(String id, Component label) {
		for (Group existing : GROUPS) {
			if (existing.id.equals(id)) {
				return existing;
			}
		}
		Group created = new Group(id, label);
		GROUPS.add(created);
		return created;
	}

	public static boolean isEmpty() {
		return GROUPS.isEmpty();
	}

	public static boolean isCollapsed(String id) {
		return COLLAPSED.contains(id);
	}

	public static void toggleCollapsed(String id) {
		if (!COLLAPSED.remove(id)) {
			COLLAPSED.add(id);
		}
	}

	/**
	 * Flattens the sections into a slot list for a panel with the given row widths.
	 *
	 * <p>Each section starts on a fresh row so its title is readable, which costs some blank slots;
	 * that is the trade for being able to tell at a glance which tree a material belongs to.
	 */
	public static List<EmiIngredient> layout(int[] widths, int pitch) {
		List<EmiIngredient> out = new ArrayList<>();
		if (GROUPS.isEmpty()) {
			return out;
		}
		Emitter emitter = new Emitter(out, widths);
		boolean first = true;
		for (Group group : GROUPS) {
			if (group.entries.isEmpty()) {
				continue;
			}
			emitter.endRow();
			// The header paints the divider along its own top edge, so a section costs one row
			// rather than two.
			emitter.add(new GroupHeader(group.id, group.label, group.entries.size(),
					emitter.rowSpan(pitch), first));
			first = false;
			emitter.endRow();
			if (!TreeTabsConfig.collapsibleGroups || !isCollapsed(group.id)) {
				for (EmiIngredient entry : group.entries) {
					emitter.add(entry);
				}
			}
		}
		return out;
	}

	/** Tracks the write position so sections can be aligned to row boundaries. */
	private static final class Emitter {
		private final List<EmiIngredient> out;
		private final int[] widths;
		private int column;
		private int row;

		Emitter(List<EmiIngredient> out, int[] widths) {
			this.out = out;
			this.widths = widths;
		}

		private int rowWidth() {
			if (widths == null || widths.length == 0) {
				return 9;
			}
			return Math.max(1, widths[Math.min(row, widths.length - 1)]);
		}

		void add(EmiIngredient entry) {
			out.add(entry);
			if (++column >= rowWidth()) {
				column = 0;
				row++;
			}
		}

		/** Pads out to the end of the current row. No-op when already at a boundary. */
		void endRow() {
			while (column != 0) {
				add(SidebarEntry.Blank.INSTANCE);
			}
		}

		/** Pixel width of the row the next entry will land on. */
		int rowSpan(int pitch) {
			return rowWidth() * pitch;
		}
	}
}
