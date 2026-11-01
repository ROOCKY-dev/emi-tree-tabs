# Hierarchical Task Analysis

See [scope and evidence limits](README.md). The goal is a player who can maintain several build
plans and trust the outstanding-material list. Plans below describe execution order and branches;
numbering identifies subtasks, not clicks or elapsed time.

## 0. Plan and carry out a multi-stage build

**Plan 0:** perform 1 if the crafting panel is not configured; repeat 2 for each goal; use 3 to
organize stages; perform 4 before relying on quantities; repeat 5 while gathering/crafting; use 6
when recipe routes disagree; use 7 after errors or interruption. Use 8–10 for navigation and
plan/participation management, 11–13 for layout and preferences, 14 for external stock, 15–16
for lifecycle and compatibility, and 17 for optional mod-author integration. Stop when the intended goals are
completed, rather than when the panel merely looks empty.

## 1. Prepare the workspace

1.1 Open EMI settings and Config Presets.
1.2 Locate the Sidebars group and choose **EMI Tree Tabs**.
1.3 Accept the preset screen, then save EMI settings with Done.
1.4 Open the relevant inventory screen and verify the Crafting page is selected.
1.5 Switch page *type* with its header icon; use arrows only for pages within that type.

**Plan 1:** use the preset only when replacing the sidebar layout is acceptable. Otherwise add a
Crafting page/subpanel manually, preserving the existing layout. Before saving a mistaken preset,
use EMI's Revert. Other presets may legitimately replace the Crafting page.

**Completion:** Crafting is available in the chosen sidebar and contains the intended list.
**Risk:** configuring the Tree Tabs attached panel and configuring EMI's Crafting page are different
tasks. One can succeed while the other is absent. The owner's earlier confusion supports testing
this distinction; it does not establish that the current preset is broken.
**Proposed check U1:** a first-time player can explain and configure both surfaces without a README
walkthrough, and can undo a preset before saving.

Evidence: [README setup](../../README.md#setting-up-the-crafting-panel),
[TreeTabsPreset](../../common/src/main/java/dev/roocky/emitreetabs/sidebar/TreeTabsPreset.java),
[SidebarScale.useSidebar](../../common/src/main/java/dev/roocky/emitreetabs/ui/SidebarScale.java).

## 2. Add and identify build goals

2.1 Find the recipe in EMI and open its recipe tree.
2.2 Decide whether to add a tab or replace the current one; Shift inverts the configured default.
2.3 Decide whether this is reference material or work to perform; Ctrl while opening starts crafting.
2.4 Verify the new goal, selected tab and mode.
2.5 Rename ambiguous goals; fork only when comparing independent recipe choices.

**Plan 2:** normally add; deliberately replace only when the previous goal is expendable. At the
configured tab limit, follow the recovery task rather than assume another tab was created.
**Completion:** the intended goal exists with an identifiable name and intended mode.
**Risks:** at capacity, the source replaces the current tree after a toast; the displaced tree is not
put in closed history (R4). New tabs default to viewing unless Ctrl is held. An empty crafting list
can therefore mean “no participating trees,” not “everything obtained.”
**Proposed U2:** explain reference/craft/parked states and their list effect where the player acts.

Evidence: [TreeTabs.onGoalSet/duplicate](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java).

## 3. Organize stages without losing goals

3.1 Select the tree that will seed a phase.
3.2 Use New phase or Ctrl+G.
3.3 Type a phase name and commit it.
3.4 Drag additional trees onto the phase header or member rows as appropriate.
3.5 Fold the phase to reduce clutter, or park it to exclude its trees from the crafting list.
3.6 Use Focus to park other phases when working on one stage.
3.7 Unpark phases when ready; remove a phase only to ungroup its trees.

**Plan 3:** repeat 3.4 for additional members. Folding is presentation only; parking changes list
participation while preserving individual craft flags. Focus affects other *phases*: ungrouped
crafting tabs are not parked by `parkAllExcept` and can still contribute.
**Completion:** phase membership and participation match the player's intent, including ungrouped tabs.
**Risks:** folded, parked, viewing and complete states have different meanings. Focus is not a
literal “only this phase contributes” operation when ungrouped tabs exist. Drag is the existing
method for assigning a tree to an existing phase; tab cycling is not an alternative to assignment.
**Proposed U2/U3:** state the scope of Focus and offer a non-drag assignment method. Do not silently
change Focus semantics. Horizontal mode carries phase color but lacks grouped headers; Ctrl+G
creates a phase there without the sidebar's immediate rename field. Treat strip parity as a
separate, explicitly selected improvement, not a promised redesign.

Evidence: [TreeSidebar.newGroup/input](../../common/src/main/java/dev/roocky/emitreetabs/ui/TreeSidebar.java),
[TreeTabs.parkAllExcept/removeGroup](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java),
[SidebarDrag](../../common/src/main/java/dev/roocky/emitreetabs/ui/SidebarDrag.java).

## 4. Set and verify quantity

4.1 Hover the tree and choose #, or Ctrl-click its batch marker.
4.2 Enter a positive count or expression.
4.3 Inspect the result/error; correct invalid input without losing the expression.
4.4 Press Enter to apply, or Escape to cancel.
4.5 Check the goal output and outstanding materials against the intended build quantity.

**Plan 4:** repeat 4.2–4.3 until valid, then apply once. Clicking outside currently cancels batch
input, whereas clicking outside a rename commits it. Explain those rules before changing either.
**Completion:** batches and resulting outputs match the intended quantity.
**Risks:** a number of recipe executions can differ from a number of output items. Test a recipe
producing multiple items; do not assume the input is a target-output calculator. Errors are shown
in a fixed-width box and require long-message and narrow-window inspection.
**Proposed U4:** offer explicit Apply/Cancel without removing Enter/Escape, and explain batches versus
output count. A target-output mode would be a separately specified feature.

Evidence: [BatchInput](../../common/src/main/java/dev/roocky/emitreetabs/ui/BatchInput.java),
[Formula](../../common/src/main/java/dev/roocky/emitreetabs/tab/Formula.java),
[TreeTabs.setBatches](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java).

## 5. Gather and allocate outstanding materials

5.1 Confirm the intended tabs are crafting and their phases are not parked.
5.2 Select the Crafting page; expand the needed sections.
5.3 Read outstanding quantities and distinguish tags/alternatives from fixed items.
5.4 Hover a shared material for per-tree demand; hold Shift for its sub-craft breakdown.
5.5 Gather or craft; inspect the updated list and progress.
5.6 Reorder underlying tabs deliberately when changing which tree gets first claim on inventory.

**Plan 5:** repeat 5.3–5.5. If quantities are surprising, inspect participation, batch counts,
recipe choices and external-stock provenance before gathering more. Sidebar grouping can display
rows in an order different from the underlying tab order used for inventory allocation.
**Completion:** the player can explain why each shortage remains; no unit of stock is promised twice.
**Risks:** cached progress can stay stale after scrolling (R6); green per-tree inventory progress is
not by itself evidence that all trees can consume the same inventory simultaneously. Test displayed
progress against pooled totals. Grouping versus allocation order needs an explicit explanation.
**Proposed U5:** show participation and priority information near the list, then test whether it
reduces incorrect interpretations before adding more visual indicators.

Evidence: [CraftingFavorites.aggregate](../../common/src/main/java/dev/roocky/emitreetabs/tab/CraftingFavorites.java),
[TreeTabs.pollProgress/sidebarRows](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java),
[SidebarRows](../../common/src/main/java/dev/roocky/emitreetabs/ui/SidebarRows.java).

## 6. Reconcile recipe routes

6.1 Open Recipe choices via its footer button or Ctrl+Shift+R.
6.2 Inspect the contested ingredient, alternative recipes and affected trees.
6.3 Decide whether differences are intentional; leave them alone if so.
6.4 Choose one route and read how many trees will change before applying it.
6.5 Inspect the rescanned conflicts and resulting material totals, then return.

**Plan 6:** repeat only for conflicts the player wants to resolve. Include gathered/raw versus
crafted alternatives and parked/reference trees: the current operation scans all open trees.
**Completion:** only intended routes are synchronized, or intentional differences remain unchanged.
**Strength:** affected-tree counts are provided before committing; the operation is player-initiated.
**Proposed evaluation:** ask a player to identify the scope before clicking. Consider an explicit
scope label before introducing filtering or bulk actions. No automatic synchronization is proposed.

Evidence: [RecipeChoices](../../common/src/main/java/dev/roocky/emitreetabs/tab/RecipeChoices.java),
[RecipeChoicesScreen](../../common/src/main/java/dev/roocky/emitreetabs/ui/RecipeChoicesScreen.java).

## 7. Recover and resume

7.1 Reopen an accidentally closed tab using the footer or Ctrl+Shift+T.
7.2 Confirm name, mode, phase, batches and recipe choices.
7.3 Close/reopen the tree screen; leave/rejoin the world; fully restart the client when testing persistence.
7.4 After recipe reload, inspect missing goals and fallback recipe choices before trusting totals.
7.5 If a save fails or a tab cannot restore, retain recoverable data and communicate the failure.

**Plan 7:** use 7.1 immediately for closure; use 7.3–7.4 for session continuation. Closed-tab history
is in-memory and should not be assumed to survive a restart. Current behavior does not satisfy
all of 7.5: it is an acceptance goal for the reliability work, not an existing guarantee.
**Completion:** valid work survives supported transitions; failures are visible and recoverable.
**Priority:** R1–R5 precede new workflow features.

Evidence: [TreeTabs persistence/history](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java),
[TabCodec](../../common/src/main/java/dev/roocky/emitreetabs/tab/TabCodec.java).

## 8. Navigate trees and retain the view

8.1 Select a visible tab with a click, cycle forward/backward with Ctrl+Tab/Ctrl+Shift+Tab, or
jump using Ctrl+1–8/Ctrl+9 (last tab).
8.2 Scroll the panel/strip; use strip arrows when available; reveal a tab inside a collapsed phase.
8.3 Pan/zoom the EMI tree, switch away and back, and check that tab's viewport.
8.4 Reorder within a phase, between phases, or into the ungrouped area; verify both membership
and inventory-priority consequences.

**Plan 8:** select by recognition when visible, by position when known, or scroll/unfold first.
A keyboard cycle follows underlying tab order, not necessarily grouped row order. Verify the
selected tab is exposed after a jump, including a collapsed parent.
**Completion:** the intended tree is active and its viewport survives switching.
**Evaluation:** last/first wrap, nonexistent numeric positions, reordered groups, right placement,
scroll boundaries and click/scroll suppression over panel background. Treat pan/zoom gestures as
EMI-owned; test their interaction with our viewport capture without reimplementing them.

## 9. Rename, fork and close individual plans

9.1 Rename through pencil/F2/right-click; in the sidebar F2 prefers the hovered row, with an
active-tab fallback. In the strip F2 targets the active tab.
9.2 Commit with Enter or click-away; cancel with Escape; clear a custom name to return to its default.
9.3 Fork with Ctrl+D, or the strip's +, then verify independent recipe choices and inherited metadata.
9.4 Close using ×, middle-click or Ctrl+W; use task 7 for recovery.

**Plan 9:** rename to identify, fork to compare, close only to discard an open plan. The + is a fork,
not a blank-tree creation tool. Do not assume phase removal closes members or restores a deleted
phase through tab history.
**Completion:** the right object was changed, and another tree was not unintentionally edited.
**Evaluation:** empty/long/Unicode names, duplicate names, no active tree, final-tab close, non-active
close, fork at capacity, and a recipe without a persistable ID. A no-op needs understandable feedback.

## 10. Control participation at every scope

10.1 Toggle one tab using its sidebar marker or Ctrl-click on the strip; EMI's own mode control
is synchronized back to the active tab.
10.2 Toggle a phase's member flags using its header marker.
10.3 Toggle all tabs using the footer/strip control or Ctrl+A.
10.4 Separately park/unpark a phase or focus one phase (task 3).
10.5 Recheck list participation after a goal completes or flags change.

**Plan 10:** choose the smallest intended scope; bulk toggle means “enable all if any are viewing,
otherwise disable all,” not invert each member. Parking still excludes a phase whose members
retain crafting flags. Completion can cause EMI aggregation to turn a tab back to viewing.
**Completion:** affected flags and actual list contributors match the intended scope.
**Evaluation:** mixed flags, all/no participants, parked members, ungrouped members, completion,
and toggles while viewing a different tab. Label raw crafting counts separately from effective
unparked participants where needed; do not infer one from the other.

## 11. Choose layout and maintain access to controls

11.1 Open Tree Tabs settings from its sidebar button or the loader's registered configuration entry.
11.2 Select Auto, horizontal or attached sidebar; inspect the preview, then save.
11.3 Choose the alternate edge with `barAtBottom` (bottom strip or right sidebar).
11.4 Resize/change GUI scale and verify Auto fallback versus an explicit sidebar selection.
11.5 Return to the tree and find essential actions whose buttons no longer fit.

**Plan 11:** Auto adapts; explicit vertical must remain vertical. Missing YACL removes the native
settings route; use task 13. The horizontal strip does not promise the sidebar's phase controls,
so a change of layout can change the available pointer methods.
**Completion:** saved selection is honored, input agrees with rendering and the player can reach
required operations. Keep approved width/content scale defaults.
**Evaluation:** save/cancel, repeated open, previews, all edges, very small windows, keyboard shortcuts
disabled and absent YACL. A shortcut fallback alone does not solve missing controls for everyone.

## 12. Configure crafting presentation and accounting

12.1 Choose aggregate versus single-tree behavior.
12.2 Choose shared-inventory accounting, understanding that disabling it lets trees count the same stock.
12.3 Choose Crafting-page layout or Favorites fallback/side takeover.
12.4 Toggle section grouping, separators, foldability and choice/tag frames independently.
12.5 Fold/unfold a material section without parking its phase or changing recipe demand.
12.6 Toggle progress visibility/poll interval; verify what updates and what is merely hidden.

**Plan 12:** change one option, verify its effect, then continue. Dedicated Crafting-page placement
suppresses duplicate Favorites presentation. A folded list section is different from a folded phase
and neither is parking. Disabling group collapse should reveal entries hidden only by that mechanism.
**Completion:** desired presentation changes without unintended loss of goals or accounting surprises.
**Evaluation:** combined settings, no Crafting page, page plus subpanel, flat versus grouped lists,
list pagination, full-width header hits and restoring the previous configuration.

## 13. Maintain preferences with or without a settings library

13.1 With YACL, open the relevant category, inspect descriptions/previews, edit and save or cancel.
13.2 Without YACL, back up and edit `config/emitreetabs.json`; wait for hot reload and inspect the effect.
13.3 Set new-tree behavior, persistence, max tabs, history length and shortcut availability deliberately.
13.4 Disable/re-enable Tree Tabs and check the transition to/from stock EMI behavior.
13.5 Recover from invalid JSON or invalid values without overwriting the user's only good copy.

**Plan 13:** prefer the GUI where available; use JSON for pack configuration or absent YACL. Never
promise that disabling persistence deletes an existing tabs file; specify and test off/on behavior.
Do not assume the GUI slider range and JSON clamp range are identical (`maxTabs` is one example).
**Completion:** the saved setting, live behavior and visible explanation agree.
**Evaluation:** all keys in [coverage](coverage.md), malformed late fields after valid early fields,
rapid file edits, old configs gaining new keys, toggling enabled/persistence with an open tree,
shortcut-disabled pointer alternatives and storage failures. Transactional-read risk is in reliability.

## 14. Use external stock and locate a shortage

14.1 Install/use a compatible source integration; explicitly enable external stock if desired.
14.2 Compare the list with the option off/on and inspect the source label.
14.3 Hover a shortage to read locate-provider suggestions; distinguish a location hint from a stock claim.
14.4 Inspect truncated extra results and Shift attribution without losing source context.
14.5 Disable the option/integration or recover after a provider fails; verify ordinary inventory still works.

**Plan 14:** enable only when the source's data is appropriate to this build. A locate provider is a
separate capability from stock accounting and is not gated by `useExternalStock` in `hasLocate`.
**Completion:** the player knows what is counted, where it was reported and what remains uncertain.
**Evaluation:** absent provider, stale/empty source, multiple providers, thrown callbacks, mixed tags,
more than three locate lines and stock disabled. Do not imply Tree Tabs transfers items or refreshes
remote container contents itself. See R8 for provider failure isolation.

## 15. Continue across reloads, sessions and Minecraft versions

15.1 Close the tree screen and verify deferred saving.
15.2 Reload EMI/resources; verify trees rebuild after recipes finish loading.
15.3 Leave/rejoin a world, switch worlds/servers and fully restart; inspect restored goals and phases.
15.4 Back up both JSON files before copying between supported Minecraft versions.
15.5 Review missing recipe goals, fallback resolutions and legacy-NBT/data-component differences.
15.6 Recover valid entries from a damaged file using a preserved backup.

**Plan 15:** test reload, disconnect and process restart separately. Cross-version copying is best-effort migration, not guaranteed recipe compatibility. Current storage is config-directory based;
verify/explain scope before assuming separate per-world plans. World-specific workspaces are a
possible future feature, not current behavior.
**Completion:** valid plans resume with expected names, grouping, flags, folds, batches and viewport;
missing recipes are reported rather than mistaken for completed work.
**Evaluation:** old-format files, partial restore, pending/live snapshot ordering, all recipes absent,
recipe return, active selection after a skipped entry, synthetic recipes without IDs, file failure
and repeated transitions. R1–R3 are prerequisites for stronger recovery guarantees.

## 16. Change recipe defaults and diagnose compatibility

16.1 Change/remove an EMI default route; inspect active and inactive trees, preserving explicit choices.
16.2 Use task 6 to resolve deliberate per-tree disagreement without silently changing unrelated branches.
16.3 If Crafting is missing, distinguish page configuration from registration failure, then inspect
`latest.log` for `emitreetabs` warnings and use the documented Favorites fallback.
16.4 If startup fails after an EMI/modpack change, record Minecraft/loader/EMI versions, logs and
minimal reproduction using copied configs; check the pinned supported combination.
16.5 Check absent YACL and optional mixin degradation separately from mandatory startup failures.

**Plan 16:** reproduce on the same Minecraft/loader line first; do not label another version
compatible because it compiles. Preserve data while diagnosing.
**Completion:** identify a configuration, integration or compatibility failure with enough evidence
to act; fallback must not silently imply all optional information remains available.
**Evaluation:** changed defaults with explicit resolutions, addon-created goals, missing optional
breakdown/hover hooks, enum-extension failure and third-party callbacks. Keep solver behavior in EMI.

## 17. Integrate another mod through the public API (author workflow)

17.1 Obtain the versioned registry, tolerating absent/older Tree Tabs without bundling its API.
17.2 Register/re-register a stock source or locate provider under a stable ID.
17.3 Register a cheap list-change listener and read the participant count with its documented scope.
17.4 Offer stock/labels and locations without blocking, opening screens or registering inside callbacks.
17.5 Unregister keyed providers; handle session lifetime for listeners and failures.
17.6 Build/test against both supported game lines and preserve the frozen entrypoint signature.

**Plan 17:** implement only the needed capability; stock remains player opt-in. Listener addition is
not keyed like providers; do not promise that repeated addition is deduplicated or individually
removable. `craftingTreeCount` currently reports raw crafting flags, including parked members;
verify that against the public wording before consumers treat it as the effective participant count.
**Completion:** healthy integrations survive a neighbor failing, notifications observe complete data,
and optional absence is safe. R7–R8 identify current contract gaps.
**Evaluation:** same-ID replacement, missing/null registry, duplicate listener registration,
throwing first/middle/last provider and listener, unregister both provider kinds, and no callbacks
holding stale world graphs. Author tasks are included for completeness, separately from player UX.

Sources for tasks 8–17: [UI dispatch](../../common/src/main/java/dev/roocky/emitreetabs/ui/TabUi.java),
[strip input](../../common/src/main/java/dev/roocky/emitreetabs/ui/TabBar.java),
[sidebar input](../../common/src/main/java/dev/roocky/emitreetabs/ui/TreeSidebar.java),
[config](../../common/src/main/java/dev/roocky/emitreetabs/TreeTabsConfig.java),
[YACL screen](../../common/src/main/java/dev/roocky/emitreetabs/config/YaclConfigScreen.java),
[section layout](../../common/src/main/java/dev/roocky/emitreetabs/sidebar/CraftingGroups.java),
[tooltip integration](../../common/src/main/java/dev/roocky/emitreetabs/mixin/SyntheticTooltipMixin.java),
[BoM hooks](../../common/src/main/java/dev/roocky/emitreetabs/mixin/BoMMixin.java),
[API guide](../../API.md) and [API implementation](../../common/src/main/java/dev/roocky/emitreetabs/tab/ApiRegistry.java).
