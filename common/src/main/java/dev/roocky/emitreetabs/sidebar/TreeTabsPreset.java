package dev.roocky.emitreetabs.sidebar;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.config.ConfigPresets;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.EmiConfig.Comment;
import dev.emi.emi.config.EmiConfig.ConfigGroup;
import dev.emi.emi.config.EmiConfig.ConfigValue;
import dev.emi.emi.config.SidebarPages.SidebarPage;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.screen.EmiScreenManager;
import dev.roocky.emitreetabs.EmiTreeTabs;

/** A sidebar preset displayed by EMI's own preset screen, with EMI's normal revert/save behavior. */
public final class TreeTabsPreset {
	private TreeTabsPreset() {
	}

	@ConfigValue("presets.emi-tree-tabs")
	@Comment("Crafting, Favorites and Craftables on the left; the item index on the right.\nClick the left sidebar's page icon to switch pages. Replaces the current sidebar layout.")
	public static final Runnable TREE_TABS = TreeTabsPreset::apply;

	/**
	 * EMI discovers presets through annotated fields. Add ours immediately after its Sidebars
	 * heading's first preset, preserving its existing order and button/revert implementation.
	 * Rebuilding the screen starts from the original fields, so entries cannot accumulate.
	 */
	public static Field[] withPreset(Field[] original) {
		if (CraftingSidebarType.TYPE == null) {
			return original;
		}
		try {
			Field ours = TreeTabsPreset.class.getField("TREE_TABS");
			List<Field> fields = new ArrayList<>(original.length + 1);
			boolean inserted = false;
			for (Field field : original) {
				fields.add(field);
				ConfigGroup group = field.getAnnotation(ConfigGroup.class);
				if (!inserted && group != null && "presets.sidebars".equals(group.value())) {
					fields.add(ours);
					inserted = true;
				}
			}
			if (!inserted) {
				EmiTreeTabs.LOGGER.warn("[emitreetabs] EMI's Sidebars preset group was not found; preset not added");
				return original;
			}
			return fields.toArray(Field[]::new);
		} catch (NoSuchFieldException error) {
			throw new IllegalStateException("Tree Tabs preset field is missing", error);
		}
	}

	private static void apply() {
		if (CraftingSidebarType.TYPE == null) {
			EmiTreeTabs.LOGGER.warn("[emitreetabs] Crafting page type is unavailable; preset not applied");
			return;
		}
		// Reuse EMI's complete two-panel layout, then give crafting its own first page. Changing
		// only EMI settings lets its Revert button undo the entire preset, including after a resize.
		ConfigPresets.recipeBookPlus.run();
		EmiConfig.leftSidebarPages.pages.add(0, new SidebarPage(CraftingSidebarType.TYPE));
		EmiConfig.leftSidebarTheme = SidebarTheme.MODERN;
		EmiConfig.leftSidebarSubpanels.subpanels.clear();
		EmiConfig.rightSidebarSubpanels.subpanels.clear();
		EmiConfig.topSidebarSubpanels.subpanels.clear();
		EmiConfig.bottomSidebarSubpanels.subpanels.clear();
		EmiConfig.leftSidebarPages.unique();
		EmiScreenManager.focusSidebarType(CraftingSidebarType.TYPE);
		EmiTreeTabs.LOGGER.info("[emitreetabs] applied EMI Tree Tabs sidebar preset");
	}
}
