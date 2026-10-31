# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

EMI Tree Tabs: a client-side Minecraft 1.21.1 mod (NeoForge + Fabric) that lets EMI keep several recipe trees open as tabs and builds one crafting list across all of them. User-facing behaviour, controls and config keys are in `README.md`; the public API for other mods is in `API.md`; plans and decisions already taken (don't reopen them) are in `ROADMAP.md`; release steps are in `PUBLISHING.md`.

## Build and test

Needs **JDK 21** (`java_version` in `gradle.properties`; the `mc/1.20.1` maintenance branch uses JDK 17 and the `forge` module). If the default JDK is wrong, pass it on the command line: a `gradle.properties` in the Gradle home overrides the project one.

```bash
./gradlew build -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk   # both loader jars
./gradlew :common:test                                   # JUnit 5 tests (common only)
./gradlew :common:test --tests 'dev.roocky.emitreetabs.ui.TabLayoutTest'          # one class
./gradlew :common:test --tests 'dev.roocky.emitreetabs.tab.FormulaTest.someMethod' # one method
./gradlew :neoforge:runClient  # or :fabric:runClient — dev client with EMI + YACL, mixin.debug/checks on
```

Jars land in `neoforge/build/libs/` and `fabric/build/libs/`. The root project's jar is disabled on purpose: it would be empty and named like the real one. Ignore `*-dev-shadow.jar` and `*-sources.jar`. CI (`.github/workflows/build.yml`) only checks that it compiles and packages. There is no lint step.

## Layout

Architectury multiloader (`common`, `neoforge`, `fabric`; no Architectury API at runtime, `injectInjectables = false`). Nearly all code is in `common`. Each loader module has only an entrypoint that calls `EmiTreeTabs.initClient()` and registers its own events, plus a `PlatformHelper` implementation found by plain `ServiceLoader` (`META-INF/services`). `common` compiles against EMI's loader-neutral `emi-xplat-intermediary` jar and the Fabric YACL jar as compile-only references; their APIs are identical on both loaders. Mojang official mappings.

Version and metadata live in root `gradle.properties` (`mod_version`, `emi_version`, `publish_game_versions`, …). They are expanded into `mods.toml`, `fabric.mod.json` and `pack.mcmeta`.

## Architecture (the big picture)

**The core trick:** EMI keeps exactly one tree in the static field `BoM.tree`. `tab/TreeTabs` holds the list of `TreeTab`s, and switching tabs goes through `setActiveTree`, which points `BoM.tree` at another tree. EMI's own `BoMScreen` still does all the drawing and editing. Don't reimplement EMI's tree logic. Swap the field and let EMI work.

**The mod hooks EMI internals, not a public API** (mixins in `common/.../mixin`, config `emitreetabs-common.mixins.json`, `defaultRequire: 1`):
- `BoMMixin`: tree creation, rebuilding tabs on EMI reload (every `EmiRecipe` object is recreated), and rebuilding inactive trees when a default recipe changes.
- `BoMScreenMixin`: puts the tab strip/sidebar on the tree screen.
- `EmiFavoritesMixin` + `tab/CraftingFavorites`: the crafting list. It runs **EMI's own** `updateSynthetic` once per crafting-mode tree with `BoM.tree` pointed at each one, then merges the results. Trees are costed against a *shrinking* inventory pool, in tab order, and by-products carry forward. Parked phases (`TabGroup.parked`) are left out.
- `TreeCostMixin` + `tab/SubCraftCosts`: records per-sub-craft demand *during* EMI's `calculateCost` walk. The walk spends remainders as it goes, so a second walk would give wrong numbers. `require = 0`, so the feature just disappears if EMI renames something.
- `sidebar/CraftingSidebarType`: adds a `Crafting` constant to EMI's `SidebarType` enum at runtime via reflection, by appending to `$VALUES`. `EmiSidebarsMixin` must return early for that constant, because EMI's `$SwitchMap` was sized before the constant existed. `ScreenSpaceMixin` serves the sectioned list (`sidebar/CraftingGroups`). If the enum can't be extended, it falls back to taking over a Favourites panel.
- Most of EMI isn't obfuscated, so EMI members use `remap = false`.

When touching any of this, **check signatures against the real EMI jar** (`javap -p -c` on `emi-xplat-mojmap-1.1.24+1.21.1`; diffing `javap -p` output against the `+1.20.1` jar shows what changed between Minecraft versions). Don't guess. Several past bugs came from guessed APIs. An EMI update is the most likely thing to break the mod.

**Persistence:** `tab/TabCodec` stores a tab as goal recipe + chosen resolutions + batch + folded branches, not the node graph. That's why tabs survive restarts and resource reloads. `TreeTabsConfig` is a hand-rolled JSON config (`config/emitreetabs.json`), hot-reloaded.

**UI:** there are two layouts, the horizontal strip (`ui/TabBar`) and the floating sidebar (`ui/TreeSidebar`), chosen by `tabOrientation`. Geometry lives in pure classes that **import nothing from Minecraft or EMI**: `TabLayout`, `SidebarLayout`, `SidebarRows`, `SidebarDrag`, plus `tab/Formula` (batch-expression parser). These are the classes covered by tests under `common/src/test`. Keep new layout/drag arithmetic in that style so it stays testable. Everything that touches EMI must be checked in game.

**Optional YACL:** every `dev.isxander.yacl3.*` reference stays in `config/YaclConfigScreen` and `config/BarPreview`. They're reached only when `yet_another_config_lib_v3` is loaded, so a missing YACL means a class that never resolves, not a crash. Keep it that way.

**Public API:** `dev.roocky.emitreetabs.api` is the only stable surface. `TreeTabsApi.registry(int)` has a frozen signature and returns `null` instead of an incompatible registry. New methods can go on `TreeTabsRegistry` without bumping `VERSION`. `tab/ApiRegistry` wraps every call out to another mod; a source that throws is logged once and dropped. Everything outside `api` is internal.

**Icons:** `textures/gui/icons.png` is generated from `art/icons/*.svg` by `art/make_icons.py`. Edit the SVGs, not the PNG. It's a white alpha mask, tinted when drawn.

## Conventions

- Comments explain *why*, especially EMI/Minecraft quirks and anything learned by reading bytecode. Match the existing long-form javadoc style.
- Branches: `feat/…`, `fix/…`, `ui/…`, `ci/…` from `main`. PRs state the EMI version tested and say honestly whether it was run in game.
- Two lines of releases: `main` is 1.21.1 (5.x), `mc/1.20.1` is the 1.20.1 maintenance branch (4.x). Fixes that apply to both are made on `main` and cherry-picked. Workflows read `java_version` and the loader modules from the checked-out branch, so the same workflow files serve both.
- Releases: a `vX.Y.Z` tag makes a draft GitHub release. A separate `store-vX.Y.Z` tag publishes to Modrinth and CurseForge. The supported Minecraft versions come from `publish_game_versions`, not from the version range.
- In-game check: quit Minecraft fully before replacing the jar, then look for `[emitreetabs]` lines in `latest.log`. `added sidebar page type 'tree_tabs_crafting'` confirms the enum extension worked.
