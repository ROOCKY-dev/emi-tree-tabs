# Complete workflow inventory and coverage

“All” means the controls, configuration keys, lifecycle transitions and public integration operations
exposed by Tree Tabs at the reviewed revisions on both supported lines. EMI's own recipe browser,
solver and pan/zoom implementation are dependencies: their interaction with Tree Tabs is covered,
not every independent EMI or third-party mod feature. Coverage means **analyzed and assigned a test**,
not successfully playtested. Both layouts and all four loader/game combinations remain in scope.

HTA numbers refer to [hta.md](hta.md); GOMS covers the corresponding goal/method either in its
main table or its complete-task supplement. Acceptance detail is in those tasks and
[reliability.md](reliability.md).

## Player actions and methods

| ID | Action / alternative methods | HTA | Required exception or boundary |
|---|---|---|---|
| C01 | Apply the named EMI preset, repeat it, accept both Done steps | 1 | Existing custom layout, duplicate pages, wrong preset |
| C02 | Revert a preset before saving / preserve layout by adding Crafting manually | 1, 12 | Revert after resize; page versus subpanel |
| C03 | Switch Crafting/Favorites/Craftables by page icon | 1, 5 | Type switch versus pagination; Crafting absent |
| C04 | Open a tree as a tab; Shift-invert new/replace behavior | 2 | No active tab, capacity, synthetic recipe without ID |
| C05 | Ctrl-open already crafting | 2, 10 | Modified opening with Shift and capacity |
| C06 | Select by click / forward-back cycle / numeric jump / last-tab jump | 8 | Empty list, invalid slot, wrap, collapsed parent |
| C07 | Scroll sidebar/strip; use strip arrows | 8 | Limits, active editor, hidden rows, tree must not zoom underneath |
| C08 | Pan/zoom a tree; switch away/back | 8, 15 | Per-tab view, screen reopen and resize |
| C09 | Reorder by dragging | 8 | First/last/self drop, scroll during drag, allocation priority |
| C10 | Rename tab with pencil / F2 / right-click | 9 | Hovered versus active target, narrow layout |
| C11 | Commit/cancel/clear tab or phase name | 3, 9 | Enter, Escape, outside click, long/Unicode/empty text |
| C12 | Fork using Ctrl+D / strip + | 9 | Cap, missing ID, independent routes, inherited phase/mode |
| C13 | Close using × / middle-click / Ctrl+W | 7, 9 | Active/non-active/final tab, no accidental tree click |
| C14 | Reopen via footer / Ctrl+Shift+T | 7 | No history, history disabled/full, missing recipe and cap |
| C15 | Toggle one tree's craft/view mode via marker / strip Ctrl-click / EMI control | 10 | Active/inactive tree; sync global flag |
| C16 | Toggle all flags using button / Ctrl+A | 10 | Mixed flags, no trees, parked phases |
| C17 | Create phase using button / Ctrl+G and name immediately where supported | 3 | Active tree required; strip lacks inline phase naming |
| C18 | Assign/reassign phase by header/member drop; ungroup by empty-space/loose drop | 3, 8 | Collapsed/empty group, self/redundant drop; no non-drag method |
| C19 | Rename a phase using pencil / hovered F2 | 3, 9 | No visible name field on strip; cancel/clear |
| C20 | Fold/unfold a phase by header click | 3 | Hidden selected child and unchanged participation |
| C21 | Toggle a phase's craft flags by marker | 10 | Mixed flags, parked group, active member |
| C22 | Park/unpark using pause / right-click | 3, 10 | Flags preserved, empty/mixed group |
| C23 | Focus using target / Shift+right-click | 3, 10 | Ungrouped participants remain; previously parked target |
| C24 | Remove phase using × / middle-click | 3, 9 | Members remain open and ungrouped; no phase undo promised |
| C25 | Open batch expression using # / Ctrl-click sidebar marker | 4 | Narrow panel, inactive target, strip method parity |
| C26 | Enter/preview/correct/apply/cancel expression | 4 | Invalid/overflow/divide-by-zero, multi-output recipe, outside cancel |
| C27 | Read merged recipe/material totals | 5 | Shared stock, tags, by-products, zero demand, completion |
| C28 | Fold/unfold material sections by header row | 5, 12 | Full-row hits, pagination, collapse disabled; distinct from phases |
| C29 | Inspect tree attribution / Shift sub-craft attribution | 5 | One/many consumers, truncation, no optional hook |
| C30 | Inspect progress markers and participation indicators | 5, 10 | New visible range, pooled versus per-tree availability |
| C31 | Open choices via footer / Ctrl+Shift+R | 6 | No conflicts, hidden footer, preserve EMI Ctrl+R |
| C32 | Scroll choices, inspect alternatives and affected trees, apply or leave unchanged | 6 | Gathered route, intentional differences, parked/viewing scope, long lists |
| C33 | Return from choices via Done/back behavior | 6 | Parent reinit, selected tab/viewport, long-list scroll reset |
| C34 | Open native settings via sidebar / loader config entry; preview/save/cancel | 11, 13 | Registration differs per loader; no YACL, hidden footer |
| C35 | Change every setting through available GUI / JSON route | 11–13 | All keys below; slider versus JSON bounds |
| C36 | Read external stock provenance and locate hints | 14 | Optional source/locator, provider failure, truncated results |
| C37 | Disable/re-enable mod / shortcuts / persistence | 13, 15 | Open tree, restored pending state, old file retained |
| C38 | Change/remove EMI default recipe; edit a per-tree resolution | 6, 16 | Inactive trees rebuilt; explicit choices preserved |
| C39 | Restore/reload and migrate files | 7, 15 | Missing/changed recipe, old format, damaged row, cross-version data |
| C40 | Diagnose missing Crafting type / missing settings / compatibility failure | 16 | Favorites fallback, optional hooks versus mandatory mixins |

## Every configuration key

This list is checked against all public configuration fields in `TreeTabsConfig`, not just the
README's most prominent options. Each key needs both normal and reversal/off-on behavior checked.

| Key | HTA | Specific check |
|---|---|---|
| `enabled` | 13 | Stock EMI transition and return with existing/pending trees |
| `openInNewTab` | 2, 13 | Default versus Shift inversion, deliberate replacement and cap |
| `persistTabs` | 7, 13, 15 | On/off/on; current file and memory behavior; save failure |
| `showProgress` | 5, 12 | Hide/reveal without implying a stock change |
| `barAtBottom` | 11 | Bottom strip / right attached panel and input geometry |
| `tabOrientation` | 11 | Auto/horizontal/vertical and explicit narrow-window selection |
| `aggregateCraftingFavorites` | 5, 12 | Merged versus EMI behavior and stale-section clearing |
| `maxTabs` | 2, 7, 9, 13 | Open/fork/reopen/restore at capacity; GUI versus JSON bounds |
| `sharedCraftingInventory` | 5, 12 | Shrinking stock versus independent stock; clear consequence |
| `progressIntervalMs` | 5, 12 | Bounded delay, unchanged inventory, new visible tabs |
| `closedTabHistory` | 7, 13 | Zero, bounded history, missing recipes, session lifetime |
| `keyboardShortcuts` | 8–11, 13 | All bindings disabled, edit keys retained, pointer route availability |
| `craftingPanelSide` | 1, 12 | NONE/LEFT/RIGHT/TOP/BOTTOM fallback and dedicated-page precedence |
| `groupCraftingList` | 5, 12 | Flat/sectioned list with identical underlying demand |
| `showGroupSeparators` | 12 | Drawn separators without extra logical demand/slots |
| `collapsibleGroups` | 5, 12 | Fold/unfold list sections; disable to reveal, not phase parking |
| `markChoiceEntries` | 5, 12 | Tag/choice frame versus fixed stack; no arithmetic effect |
| `useExternalStock` | 14 | Default off, no source, multiple/throwing sources, source labeling |
| `craftingInFavorites` | 1, 12 | Fallback visibility; ignored once Crafting is placed |

## Lifecycle, integration and rendering boundaries

| ID | Operation | HTA | Check |
|---|---|---|---|
| L01 | Tree screen init/reinit/onClose | 8, 15 | Editors reset, viewport and selection retained, deferred flush |
| L02 | EMI reload/default recalculation/adoption of addon-created goal | 15, 16 | No old recipe references; preserve valid choices; enforce intended limits |
| L03 | Disconnect, reconnect, world/server switch, client restart | 15 | Correct persistence scope, no retained world graph, no cross-world contamination |
| L04 | Config hot reload, old format, invalid file, storage failure | 13, 15 | Validation, transaction, retry and recovery copy |
| L05 | Registry absent/incompatible/present | 17 | Frozen signature; safe optional consumer behavior |
| L06 | Register/re-register/unregister stock and locate providers | 14, 17 | Stable IDs, opt-in stock, independent capabilities |
| L07 | Listeners and participant count | 10, 17 | Completed data, duplicates/session lifetime, parked-count semantics |
| L08 | Callback failure and neighboring healthy providers | 14, 17 | Isolation, safe iteration, bounded reporting, no blocked client thread |
| L09 | Missing YACL / Crafting registration / optional mixin target | 11, 16 | Honest degraded state; core still usable where supported |
| L10 | Left/right/top/bottom, GUI scales, resize, long text, non-Latin names | 8, 11–13 | Correct hit targets, text visibility, clipping, tooltip width, native focus |
| L11 | Viewport versus popup/row input routing | 4, 8, 9 | No click/scroll/drag/shortcut leaking through editing controls |
| L12 | Shared material arithmetic and external stock after transitions | 5, 14–16 | Recount after batches, reorder, park, choices, completion and source changes |

## Completion rule

Every row above has an HTA plan and an exception/acceptance target. GOMS supplies a method and
selection rule for every task family; reliability triage covers cross-cutting failure paths.
No inventory entry is a claim that the test has passed. Implementation must retain this inventory,
record results per Minecraft/loader combination and add new rows when adding new behavior.
