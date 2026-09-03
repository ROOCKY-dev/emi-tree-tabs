package dev.ahmed.emitreetabs.forge;

import dev.ahmed.emitreetabs.TreeTabsConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game settings screen, built with Cloth Config.
 *
 * <p><strong>Never referenced unless {@code cloth_config} is loaded.</strong> Every Cloth type
 * lives in this class alone, so when the library is absent the class is simply never loaded and
 * nothing tries to resolve it. See {@link EmiTreeTabs} for the guard.
 */
public final class ClothConfigScreen {

	private ClothConfigScreen() {
	}

	public static Screen create(Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(key("title"))
				.setSavingRunnable(TreeTabsConfig::save);
		ConfigEntryBuilder entries = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(key("category.general"));
		general.addEntry(entries.startBooleanToggle(key("enabled"), TreeTabsConfig.enabled)
				.setDefaultValue(true)
				.setTooltip(key("enabled.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.enabled = value)
				.build());
		general.addEntry(entries.startBooleanToggle(key("openInNewTab"), TreeTabsConfig.openInNewTab)
				.setDefaultValue(true)
				.setTooltip(key("openInNewTab.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.openInNewTab = value)
				.build());
		general.addEntry(entries.startBooleanToggle(key("persistTabs"), TreeTabsConfig.persistTabs)
				.setDefaultValue(true)
				.setTooltip(key("persistTabs.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.persistTabs = value)
				.build());
		general.addEntry(entries.startBooleanToggle(key("keyboardShortcuts"), TreeTabsConfig.keyboardShortcuts)
				.setDefaultValue(true)
				.setTooltip(key("keyboardShortcuts.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.keyboardShortcuts = value)
				.build());
		general.addEntry(entries.startIntSlider(key("maxTabs"), TreeTabsConfig.maxTabs, 1, 64)
				.setDefaultValue(32)
				.setTooltip(key("maxTabs.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.maxTabs = value)
				.build());
		general.addEntry(entries.startIntSlider(key("closedTabHistory"), TreeTabsConfig.closedTabHistory, 0, 64)
				.setDefaultValue(16)
				.setTooltip(key("closedTabHistory.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.closedTabHistory = value)
				.build());

		ConfigCategory appearance = builder.getOrCreateCategory(key("category.appearance"));
		appearance.addEntry(entries.startBooleanToggle(key("barAtBottom"), TreeTabsConfig.barAtBottom)
				.setDefaultValue(false)
				.setTooltip(key("barAtBottom.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.barAtBottom = value)
				.build());
		appearance.addEntry(entries.startBooleanToggle(key("showProgress"), TreeTabsConfig.showProgress)
				.setDefaultValue(true)
				.setTooltip(key("showProgress.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.showProgress = value)
				.build());
		appearance.addEntry(entries.startIntSlider(key("progressIntervalMs"), TreeTabsConfig.progressIntervalMs, 100, 5000)
				.setDefaultValue(500)
				.setTooltip(key("progressIntervalMs.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.progressIntervalMs = value)
				.build());

		ConfigCategory crafting = builder.getOrCreateCategory(key("category.crafting"));
		crafting.addEntry(entries.startBooleanToggle(key("aggregateCraftingFavorites"), TreeTabsConfig.aggregateCraftingFavorites)
				.setDefaultValue(true)
				.setTooltip(key("aggregateCraftingFavorites.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.aggregateCraftingFavorites = value)
				.build());
		crafting.addEntry(entries.startBooleanToggle(key("sharedCraftingInventory"), TreeTabsConfig.sharedCraftingInventory)
				.setDefaultValue(true)
				.setTooltip(key("sharedCraftingInventory.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.sharedCraftingInventory = value)
				.build());

		ConfigCategory sidebar = builder.getOrCreateCategory(key("category.sidebar"));
		sidebar.addEntry(entries.startStringDropdownMenu(key("craftingPanelSide"), TreeTabsConfig.craftingPanelSide)
				.setDefaultValue("NONE")
				.setSelections(List.of("NONE", "LEFT", "RIGHT", "TOP", "BOTTOM"))
				.setTooltip(key("craftingPanelSide.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.craftingPanelSide = value)
				.build());
		sidebar.addEntry(entries.startBooleanToggle(key("groupCraftingList"), TreeTabsConfig.groupCraftingList)
				.setDefaultValue(true)
				.setTooltip(key("groupCraftingList.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.groupCraftingList = value)
				.build());
		sidebar.addEntry(entries.startBooleanToggle(key("showGroupSeparators"), TreeTabsConfig.showGroupSeparators)
				.setDefaultValue(true)
				.setTooltip(key("showGroupSeparators.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.showGroupSeparators = value)
				.build());
		sidebar.addEntry(entries.startBooleanToggle(key("craftingInFavorites"), TreeTabsConfig.craftingInFavorites)
				.setDefaultValue(true)
				.setTooltip(key("craftingInFavorites.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.craftingInFavorites = value)
				.build());
		sidebar.addEntry(entries.startBooleanToggle(key("collapsibleGroups"), TreeTabsConfig.collapsibleGroups)
				.setDefaultValue(true)
				.setTooltip(key("collapsibleGroups.tooltip"))
				.setSaveConsumer(value -> TreeTabsConfig.collapsibleGroups = value)
				.build());

		return builder.build();
	}

	private static Component key(String suffix) {
		return Component.translatable("emi.tree_tabs.config." + suffix);
	}
}
