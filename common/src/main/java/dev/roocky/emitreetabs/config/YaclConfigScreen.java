package dev.roocky.emitreetabs.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.roocky.emitreetabs.TreeTabsConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The in-game settings screen.
 *
 * <p><strong>Never referenced unless {@code yet_another_config_lib_v3} is loaded.</strong> Every
 * YACL type is confined to this class and {@link BarPreview}, so a missing library is a class that
 * never resolves rather than a crash — the settings button simply does not appear.
 *
 * <p>In {@code common} rather than in each loader module, because {@code dev.isxander.yacl3.*} is
 * identical on Forge and Fabric. The Cloth screen it replaces was Forge-only, so this is also the
 * first settings screen Fabric has had.
 *
 * <h2>What this screen is trying to be</h2>
 *
 * The goal is that nobody ever needs to open {@code emitreetabs.json}. So:
 *
 * <ul>
 *   <li>Tabs are named for what you came to change — <em>Tabs &amp; layout</em>, <em>Crafting
 *       list</em>, <em>Behaviour &amp; keys</em> — not after the part of the code they touch.</li>
 *   <li>No bare numbers. Every slider says what its value means, and the ends say which direction
 *       is which.</li>
 *   <li>Anything that touches the persistence format is collapsed into <em>Advanced</em>, because
 *       it is the part where a wrong answer costs you tabs rather than looks.</li>
 *   <li>The layout options carry a live tab bar, drawn by the real layout arithmetic.</li>
 * </ul>
 *
 * <p>The json stays the source of truth: this writes through {@link TreeTabsConfig#save()}, and a
 * pack author shipping a config file keeps working exactly as before.
 */
public final class YaclConfigScreen {

	private YaclConfigScreen() {
	}

	/** What the preview should draw, which is the pending values rather than the saved ones. */
	private static final class Pending {
		String orientation = TreeTabsConfig.tabOrientation;
		boolean barAtBottom = TreeTabsConfig.barAtBottom;
	}

	public static Screen create(Screen parent) {
		Pending pending = new Pending();

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("emi.tree_tabs.config.title"))
				.category(layout(pending))
				.category(craftingList())
				.category(behaviour())
				.save(TreeTabsConfig::save)
				.build()
				.generateScreen(parent);
	}

	// ------------------------------------------------------------ categories

	private static ConfigCategory layout(Pending pending) {
		Supplier<OptionDescription> preview = () -> OptionDescription.createBuilder()
				.text(Component.translatable("emi.tree_tabs.config.preview.text"))
				.customImage(new BarPreview(() -> pending.orientation, () -> pending.barAtBottom))
				.build();

		Option<String> orientation = Option.<String>createBuilder()
				.name(Component.translatable("emi.tree_tabs.config.tabOrientation"))
				.description(value -> preview.get())
				.binding("auto", () -> TreeTabsConfig.tabOrientation,
						value -> TreeTabsConfig.tabOrientation = value)
				.controller(opt -> CyclingListControllerBuilder.create(opt)
						.values("auto", "horizontal", "vertical")
						.valueFormatter(value -> Component.translatable(
								"emi.tree_tabs.config.tabOrientation." + value)))
				.listener((opt, value) -> pending.orientation = value)
				.build();

		Option<Boolean> barAtBottom = Option.<Boolean>createBuilder()
				.name(Component.translatable("emi.tree_tabs.config.barAtBottom"))
				.description(value -> preview.get())
				.binding(false, () -> TreeTabsConfig.barAtBottom,
						value -> TreeTabsConfig.barAtBottom = value)
				.controller(TickBoxControllerBuilder::create)
				.listener((opt, value) -> pending.barAtBottom = value)
				.build();

		return ConfigCategory.createBuilder()
				.name(Component.translatable("emi.tree_tabs.config.category.layout"))
				.tooltip(Component.translatable("emi.tree_tabs.config.category.layout.tooltip"))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.layout"))
						.option(toggle("enabled", true, () -> TreeTabsConfig.enabled,
								v -> TreeTabsConfig.enabled = v))
						.option(orientation)
						.option(barAtBottom)
						.option(toggle("showProgress", true, () -> TreeTabsConfig.showProgress,
								v -> TreeTabsConfig.showProgress = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.advanced"))
						.description(OptionDescription.of(Component.translatable(
								"emi.tree_tabs.config.group.advanced.desc")))
						.collapsed(true)
						.option(slider("maxTabs", 32, 4, 64, 1,
								() -> TreeTabsConfig.maxTabs, v -> TreeTabsConfig.maxTabs = v,
								value -> Component.translatable(
										"emi.tree_tabs.config.maxTabs.value", value)))
						.build())
				.build();
	}

	private static ConfigCategory craftingList() {
		return ConfigCategory.createBuilder()
				.name(Component.translatable("emi.tree_tabs.config.category.crafting"))
				.tooltip(Component.translatable("emi.tree_tabs.config.category.crafting.tooltip"))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.list"))
						.option(toggle("aggregateCraftingFavorites", true,
								() -> TreeTabsConfig.aggregateCraftingFavorites,
								v -> TreeTabsConfig.aggregateCraftingFavorites = v))
						.option(toggle("sharedCraftingInventory", true,
								() -> TreeTabsConfig.sharedCraftingInventory,
								v -> TreeTabsConfig.sharedCraftingInventory = v))
						.option(toggle("groupCraftingList", true,
								() -> TreeTabsConfig.groupCraftingList,
								v -> TreeTabsConfig.groupCraftingList = v))
						.option(toggle("showGroupSeparators", true,
								() -> TreeTabsConfig.showGroupSeparators,
								v -> TreeTabsConfig.showGroupSeparators = v))
						.option(toggle("collapsibleGroups", true,
								() -> TreeTabsConfig.collapsibleGroups,
								v -> TreeTabsConfig.collapsibleGroups = v))
						.option(toggle("markChoiceEntries", true,
								() -> TreeTabsConfig.markChoiceEntries,
								v -> TreeTabsConfig.markChoiceEntries = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.advanced"))
						.description(OptionDescription.of(Component.translatable(
								"emi.tree_tabs.config.group.advanced.desc")))
						.collapsed(true)
						.option(toggle("craftingInFavorites", true,
								() -> TreeTabsConfig.craftingInFavorites,
								v -> TreeTabsConfig.craftingInFavorites = v))
						.option(Option.<String>createBuilder()
								.name(Component.translatable("emi.tree_tabs.config.craftingPanelSide"))
								.description(OptionDescription.of(Component.translatable(
										"emi.tree_tabs.config.craftingPanelSide.tooltip")))
								.binding("NONE", () -> TreeTabsConfig.craftingPanelSide,
										value -> TreeTabsConfig.craftingPanelSide = value)
								.controller(opt -> CyclingListControllerBuilder.create(opt)
										.values("NONE", "LEFT", "RIGHT", "TOP", "BOTTOM")
										.valueFormatter(value -> Component.translatable(
												"emi.tree_tabs.config.craftingPanelSide." + value)))
								.build())
						.build())
				.build();
	}

	private static ConfigCategory behaviour() {
		return ConfigCategory.createBuilder()
				.name(Component.translatable("emi.tree_tabs.config.category.behaviour"))
				.tooltip(Component.translatable("emi.tree_tabs.config.category.behaviour.tooltip"))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.behaviour"))
						.option(toggle("openInNewTab", true, () -> TreeTabsConfig.openInNewTab,
								v -> TreeTabsConfig.openInNewTab = v))
						.option(toggle("keyboardShortcuts", true,
								() -> TreeTabsConfig.keyboardShortcuts,
								v -> TreeTabsConfig.keyboardShortcuts = v))
						.option(toggle("offerResolutionSync", true,
								() -> TreeTabsConfig.offerResolutionSync,
								v -> TreeTabsConfig.offerResolutionSync = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("emi.tree_tabs.config.group.advanced"))
						.description(OptionDescription.of(Component.translatable(
								"emi.tree_tabs.config.group.advanced.desc")))
						.collapsed(true)
						.option(toggle("persistTabs", true, () -> TreeTabsConfig.persistTabs,
								v -> TreeTabsConfig.persistTabs = v))
						.option(slider("closedTabHistory", 16, 0, 64, 1,
								() -> TreeTabsConfig.closedTabHistory,
								v -> TreeTabsConfig.closedTabHistory = v,
								value -> value == 0
										? Component.translatable(
												"emi.tree_tabs.config.closedTabHistory.off")
										: Component.translatable(
												"emi.tree_tabs.config.closedTabHistory.value", value)))
						.option(slider("progressIntervalMs", 500, 100, 2000, 100,
								() -> TreeTabsConfig.progressIntervalMs,
								v -> TreeTabsConfig.progressIntervalMs = v,
								YaclConfigScreen::interval))
						.build())
				.build();
	}

	// --------------------------------------------------------------- helpers

	private static Option<Boolean> toggle(String key, boolean fallback,
			Supplier<Boolean> getter, Consumer<Boolean> setter) {
		return Option.<Boolean>createBuilder()
				.name(Component.translatable("emi.tree_tabs.config." + key))
				.description(OptionDescription.of(
						Component.translatable("emi.tree_tabs.config." + key + ".tooltip")))
				.binding(fallback, getter, setter)
				.controller(TickBoxControllerBuilder::create)
				.build();
	}

	/**
	 * A slider that never shows a bare number.
	 *
	 * <p>"500" tells you nothing about whether it is a lot; "Every 0.5s — responsive" tells you
	 * which way the handle should go and what it costs.
	 */
	private static Option<Integer> slider(String key, int fallback, int min, int max, int step,
			Supplier<Integer> getter, Consumer<Integer> setter,
			java.util.function.Function<Integer, Component> format) {
		return Option.<Integer>createBuilder()
				.name(Component.translatable("emi.tree_tabs.config." + key))
				.description(OptionDescription.of(
						Component.translatable("emi.tree_tabs.config." + key + ".tooltip")))
				.binding(fallback, getter, setter)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt)
						.range(min, max)
						.step(step)
						.valueFormatter(format::apply))
				.build();
	}

	private static Component interval(int ms) {
		String seconds = String.format("%.1f", ms / 1000.0);
		String end = ms <= 200 ? "responsive" : ms >= 1500 ? "cheap" : "balanced";
		return Component.translatable("emi.tree_tabs.config.progressIntervalMs.value", seconds,
				Component.translatable("emi.tree_tabs.config.progressIntervalMs." + end));
	}
}
