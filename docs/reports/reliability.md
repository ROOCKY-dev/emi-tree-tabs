# Reliability findings and delivery plan

See [review scope](README.md). Severity is proposed triage: **P1** = protect work before adding
features; **P2** = correct misleading state, recovery or integration behavior; **P3** = maintenance.
“Source-confirmed” means the control flow was inspected on both branches. It does not mean a
runtime failure was reproduced. Reproduction cases below are future acceptance tests using isolated
config directories, never the player's real saves.

## Source-confirmed findings

### R1 — P1: saving truncates the destination without atomic replacement

`TreeTabs.writeFile` opens the existing tabs file directly with `Files.newBufferedWriter`
(main line 933). `TreeTabsConfig.save` uses the same direct-write pattern (line 199). Neither path
stages a complete replacement or preserves a last-good backup. An interruption/write failure after
opening the writer can leave a partial or empty destination.

**Proposed fix:** serialize successfully, write a sibling temporary file, then replace the destination
atomically where supported. Define an explicit safe fallback and retain a last-good recovery copy;
do not claim atomic replacement alone guarantees power-loss durability.
**Acceptance:** inject failure before/during write and replacement; the previous valid state remains
loadable, temporary files do not become authoritative, and a successful retry writes the new state.

### R2 — P1: failed tab saves clear the retry flag

`TreeTabs.flush` sets `dirty = false` before calling `writeFile` (lines 901–906). The writer catches
`IOException` and only logs it. With no further mutation, subsequent flushes do nothing, even after
storage becomes writable again. This is separate from R1: an atomic writer alone does not fix retry.

**Proposed fix:** report save success to the caller and clear dirty only after a successful write;
bound retries and surface a concise failure state without logging every frame.
**Acceptance:** mark dirty, fail a write, restore access, flush again without another edit; the latest
state is saved and the failure state clears. Persist-disabled behavior is explicitly specified.

### R3 — P1: malformed saved entries are not isolated during restoration

`TreeTabs.tryRestore` calls `TabCodec.load` for every pending object without a per-entry boundary
(lines 584–589). The codec directly reads typed fields (`goal`, `resolutions`, `folds`, batches and
viewport); malformed types can throw. A bad row can abort restoration of valid neighbors. The
codec also lower-bounds persisted batches but does not apply the `Formula.MAX` upper bound used by
interactive batch changes (`TabCodec` lines 134–137).

**Proposed fix:** validate a snapshot before constructing/costing a tree, isolate individual failures,
retain rejected source data for recovery, and summarize failures. Apply common bounds and finite
viewport validation. Keep version-1 compatibility and missing-recipe fallback semantics deliberate.
**Acceptance:** valid/bad/valid entries restore the valid two; wrong JSON types, missing recipe IDs,
extreme batches and malformed fold paths cannot abort the entire restore or discard the only backup.
Test recipe-removal and recipe-return separately; indefinite missing-recipe retention is a product
choice, not silently promised behavior.

### R4 — P1: opening at the tab limit replaces work without closed-history recovery

`TreeTabs.onGoalSet` enters the replacement branch when a new tab is requested but `maxTabs` is
reached (lines 129–148). It displays a toast, then replaces the active tree and clears its name and
snapshot. It does not use `close` or otherwise put the displaced tree into `CLOSED`.

**Proposed behavior decision:** prefer rejecting the new-tab request while restoring the prior active
EMI tree, or explicitly offering recoverable replacement. Preserve deliberate Shift replacement.
Since EMI has already set the new goal, implementation must restore the old `BoM.tree` as well as
leave `TABS` intact when refusing. A toast by itself is insufficient recovery.
**Acceptance:** at a small limit, request another tab; the original named tree, resolutions, batches
and phase remain available. Verify selected/global tree consistency and deliberate replacement.

### R5 — P2: unsuccessful reopen consumes history and ignores the tab limit

`reopenClosed` removes the head with `CLOSED.poll` before rebuilding it; a null codec result returns
without restoring the entry (lines 244–252). The method also lacks the capacity check present in
`duplicate`. Missing recipes can therefore consume history, and repeated close/open/reopen sequences
can exceed the configured limit.

**Proposed fix:** check capacity first, peek and rebuild before removing the entry, and make an
unrestorable entry visible/recoverable without trapping access to older history.
**Acceptance:** a missing goal or full tab list leaves history available; after recovery, the correct
entry restores once. Define how to skip/quarantine an unavailable history head; do not silently drop it.

### R6 — P2: progress cache does not account for newly visible tabs

`pollProgress` returns when inventory and structure version are unchanged (lines 464–469). Only the
requested visible range is calculated after that guard. Scroll changes the requested range without
changing either cached key. A previously offscreen inactive tab can keep an old/default marker until
an inventory or structural change occurs. In grouped sidebar order, the min/max original indices
also span potentially hidden tabs; that is a performance hypothesis to measure, not a correctness
claim about the solver.

**Proposed fix:** track freshness per tab/inventory generation, or include visible membership in cache
invalidation. Preserve bounded polling; avoid recalculating every graph every frame.
**Acceptance:** change stock while target tabs are offscreen, allow the current range to poll, then
scroll/unfold to the targets without changing stock. Their markers update within the configured
poll interval. Test reordered/grouped tabs and both layouts.

### R7 — P2: list-change listeners run before the merged result is published

`CraftingFavorites.aggregate` calls `craftingListChanged` in its `finally` block (line 137), before
assigning `ATTRIBUTION` and rebuilding the final synthetic list/sections (lines 146 onward). The
listener contract says the list “has been rebuilt.” A callback inspecting the list sees an
intermediate result; it is also notified when the pass exits through an exception.

**Proposed fix:** restore EMI globals in `finally`, publish the complete result, then notify once
on success. Define notification behavior for clearing/empty results and failure, and guard against
unsupported re-entry. Keep exception isolation around other mods' callbacks.
**Acceptance:** a recording listener observes the new merged totals, attribution and restored active
tree on each successful change, including empty transitions; a failed pass does not announce a
successful rebuild. Check parked phases and inventory updates.

### R8 — P2: failing providers modify a map during its traversal

`ApiRegistry.stock` and `locate` iterate their respective `LinkedHashMap.entrySet`; `guard` removes
failed IDs from both maps inside that traversal (lines 145–153). If a provider fails before an
unvisited neighbor, the next iterator advance can throw `ConcurrentModificationException` outside
the callback guard. Catching the original callback exception does not safely isolate its failure.
This source path is identical on both supported branches; it was not reproduced in Minecraft here.

**Proposed fix:** defer removals until traversal finishes, or use a stable snapshot with explicit
failure removal. Keep healthy providers running and define whether failure of one capability should
remove another capability registered under the same ID.
**Acceptance:** register three providers, fail first/middle/last in turn for stock, stock label and
locate; all healthy neighbors are queried as intended, without iterator errors, and failure is
reported once. Also test a throwing listener: current code logs it but leaves it registered, contrary
to the blanket API guide promise of dropping failed integrations. Align listener behavior and its
contract without mutating its active iterator unsafely.

## Additional risks and MASK maintenance

- **P2 contract gap:** `craftingTreeCount` returns raw crafting flags, including parked tabs, although
  its API wording implies current contributors. Specify raw versus effective count and test both
  before changing consumer-visible semantics. Repeated listener registration also appends rather
  than replacing keyed registrations; document and test that distinct lifetime rule.
- **P2 investigation:** config reading mutates static fields incrementally before a later field can
  throw. `TreeTabsConfig.read` then reports “using defaults,” although earlier assignments remain.
  Validate into a pending config and apply only after success; test mixed valid/invalid keys.
- **P2 investigation:** `refreshCraftingList` catches all throwables at debug level. A stale list may
  have no normal-level explanation. Define a once-per-cause warning and visible stale/unavailable
  state if reproduced; avoid a warning on every refresh.
- **P3:** comments contradict current behavior: `ConfigScreenHook` names Cloth Config instead of
  YACL; `RecipeChoicesScreen` describes a conditional button although it is now always drawn;
  `RecipeChoices` says scans never run on a timer although `TreeSidebar.choiceCount` runs them
  approximately once per second; duplicate/misplaced Javadocs precede `pollProgress`, `groupTab`
  and `CraftingFavorites.attribution`. Correct these alongside the relevant change so comments
  explain the actual constraints. This is not justification for broad renaming or rearchitecture.
- **P3 measurement:** selections/renames call the same dirty path as changes requiring a full
  crafting recount. Measure unnecessary rebuild cost with many trees before splitting invalidation.

Sources: [TreeTabs](../../common/src/main/java/dev/roocky/emitreetabs/tab/TreeTabs.java),
[TabCodec](../../common/src/main/java/dev/roocky/emitreetabs/tab/TabCodec.java),
[TreeTabsConfig](../../common/src/main/java/dev/roocky/emitreetabs/TreeTabsConfig.java),
[CraftingFavorites](../../common/src/main/java/dev/roocky/emitreetabs/tab/CraftingFavorites.java),
[listener contract](../../common/src/main/java/dev/roocky/emitreetabs/api/CraftingListListener.java),
[API registry](../../common/src/main/java/dev/roocky/emitreetabs/tab/ApiRegistry.java).
Line references refer to the reviewed main revision, not future edits.

## Existing coverage and gaps

| Existing test class | What it establishes | Important boundary it does not cover |
|---|---|---|
| TabLayoutTest | Strip density, scroll, hit regions, label budgets and drag destination arithmetic | Actual tree switching and mouse/key routing |
| SidebarLayoutTest | Panel geometry, rows, collapse, action placement, clipping/hits and footer spacing | Rendered readability, focus/narration and real pointer accuracy |
| SidebarRowsTest / SidebarDragTest | Group display mapping and assignment/reorder decisions | Tree state after real EMI drag/reload |
| SidebarScaleTest | Scale conversion, matching drawn/hit coordinates and explicit layout choice | Comfortable target size across physical displays |
| FormulaTest | Operators, bounds, malformed expressions, overflow and error results | Persisted batch validation and edit/apply/cancel behavior |
| TreeTabsApiTest | Version gate and registry availability/identity | Stock arithmetic, callback ordering and hostile integrations |
| tools/tests/test_release_all.py | Coordinated release decisions and guards | Store moderation/visibility and actual in-game behavior |

Fresh command results are recorded in the review index when complete. Historical build/playtest
records are not treated as reproductions of R1–R8. Documentation review alone cannot certify
correctness or reliability of both produced jars.

## Delivery packages and release gate

| Package | Contents | Required evidence before release |
|---|---|---|
| A: preserve work | R1–R3; transactional config read if confirmed in fault tests | File-operation fault tests, old-format fixtures, corrupt-neighbor restoration and restart checks |
| B: safe task transitions | R4–R5; accurate failure feedback | Real EMI capacity/reopen scenarios, no lost goals, no limit bypass and correct active-tree state |
| C: trustworthy status | R6–R8; state/scope explanations U2/U5 | Visible-range regression, listener ordering, provider failure isolation and two-tree shrinking-inventory fixtures |
| D: accessible workflows | U1/U3/U4; relevant comment corrections | Fresh screenshot sequence, HTA/GOMS task trials, keyboard/non-drag route and long-text checks |

For every shared package, implement and test the corresponding change on both branches with the
same mod version. Keep Java 17/Forge APIs on 1.20.1 and Java 21/NeoForge APIs on 1.21.1. Do not add
Minecraft dependencies to pure layout/arithmetic classes merely to make a test convenient.

Run all four combinations: **1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge, 1.21.1 Fabric**.
Each requires actual EMI runtime checks for preset apply/reapply/Revert/Done; tree switching,
phase assignment/park/focus; batch expression/cancel; conflict resolution; shared inventory;
close/reopen; F3+T/resource reload; leave/rejoin; and full restart. Verify optional-YACL absence
and fallback when the Crafting type cannot be registered. Use isolated save/config directories.

Minimum arithmetic scenarios: two trees each needing 10 of the same material with only 10 stock;
a deterministic by-product carried forward; probabilistic by-products not promised as stock;
park/unpark preserving flags; reorder changing allocation without double-counting; tag versus
plain-item demand; external stock disabled/enabled and a provider throwing. Specify expected
values before running each scenario; do not invent recipe semantics or signatures—inspect the
pinned EMI jars when implementation touches them.

Visual checks include left/right panels, horizontal mode, GUI scales 2/3/4, narrow windows,
long names, mixed-language text, many tabs and no-YACL entry points. Verify hover/click/scroll
routing against what is drawn; geometry tests alone do not complete this gate.

Use the existing coordinated release command in [RELEASES.md](../../RELEASES.md) only after both
lines satisfy the package's acceptance checks. This report requests no release or version bump.
