# Roadmap

What is planned for EMI Tree Tabs, roughly in order. **No dates are promised** — this is a
spare-time project, and items move or get dropped when they turn out to be worse ideas than they
looked.

Current release: **2.2.0** (Minecraft 1.20.1, Forge and Fabric).

## Decisions taken

Three things were settled after a research pass on 2026-09-03, so they stop being reopened:

**This is an EMI addon and stays one.** JEI has two recipe-tree addons already — JECT (27K downloads,
four Minecraft versions, three loaders) and JEI Crafting Tree — and, more to the point, EMI does the
tree solving for us. `BoM`, `MaterialTree` and `TreeCost` handle recursive resolution, catalysts,
byproduct reuse and probabilistic outputs; this mod is a *manager* wrapped around that. JEI exposes
recipe lookup and bookmarks and no solver at all, so a JEI version would mean rebuilding EMI's
hardest code before writing any of our own, then arriving third. Meanwhile the largest pack of 2026,
All the Mods 10, ships EMI and not JEI, and TooManyRecipeViewers (3.3M downloads) lets EMI-only packs
run JEI addons anyway. The traffic is going the right way. **We build for EMI and aim to be the best
thing in that space.**

**The chest tracker left this repo.** It became its own project, [**Quartermaster**](../quartermaster) —
a standalone, viewer-agnostic container memory. It optionally depends on Tree Tabs, never the
reverse. Reasoning and full design live in that project's `BRIEF.md`.

**Tree Tabs needs a small public API.** Today this mod reaches into EMI's internals through mixins
and has no API of its own. Quartermaster must not repeat that. So Tree Tabs grows a versioned,
public surface — roughly: register a stock source that feeds the crafting-list arithmetic, register
a "locate this item" provider, listen for crafting-list changes — and Quartermaster consumes only
that. Tree Tabs stays the only mod carrying the EMI-internals risk.

## Supported versions

The loader list is not a preference, it is dictated by what EMI itself publishes:

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 1.20.1 | **supported now** | **supported now** | EMI does not build for it |
| 1.21.1 | planned | EMI stopped at 1.20.2 | planned |

Forge is a dead end after 1.20.2 because EMI publishes no Forge builds past that, so moving to
newer Minecraft means moving to NeoForge. 1.20.1 will keep being supported from its own branch
after the port; it is not going to be abandoned the day 1.21.1 works.

## 2.1 / 2.2 — done

- [x] **Development infrastructure** — CI on every push and pull request, a build attached to each
      release automatically, issue and pull request templates.
- [x] **Multiloader restructure** — the Minecraft-and-EMI code is shared; only entrypoints differ.
- [x] **Fabric support on 1.20.1.**
- [x] **Toggle crafting for every tab at once**, rather than `Ctrl+click`ing each one.
- [x] **Drag-to-reorder fixed** — the drop position was measured from the wrong origin, which only
      showed up once there were enough tabs for the scroll arrows to appear.

## 3.1 — Interface work

The bar works but does not feel finished. The complaints below are all one missing rule, not six
separate bugs: the bar can only shrink. Four constants — `MIN_TAB_WIDTH`, `MAX_TAB_WIDTH`,
`LABEL_THRESHOLD`, `CLOSE_THRESHOLD` — were each tuned by eye and none of them relate to the others.

### The rule: hold a floor, then paginate

Browsers solved this. Chrome and Firefox shrink tabs to a minimum and then **stop**, and scroll past
that. Nothing ever shrinks below the size at which it is still clickable. Three named densities,
chosen by available width ÷ tab count:

| Density | Width per tab | Shows |
|---|---|---|
| Comfortable | ≥ 96px | icon, full name, close |
| Compact | ≥ 54px | icon, truncated name, close |
| Icon only | 28px — the floor, never less | icon and progress; close replaces the progress marker on hover |

Below the floor the bar **scrolls** rather than shrinking further. Scroll arrows appear only when
scrolling is actually possible.

- [x] **Implement the density ladder.** Fixes the vanished close button as a side effect, because it
      is no longer allowed to be traded away. Verified in game at GUI scale 4 from 1 to 20 tabs.
- [x] **Spend the empty space** — tabs grow toward `MAX_TAB_WIDTH` until the bar is used.
- [ ] **Give the active tab a width bonus**, as browsers do. Deferred: it makes tab positions
      non-uniform, which every hit test, the drag drop and the scroll arithmetic currently assume.
      Worth doing, but not worth bundling with the change that fixed the actual complaint.
- [x] **Scrolling as a real interaction** — arrows appear exactly when tabs reach the floor, and
      each one is dimmed when it cannot move. Verified in game at 20 tabs.
- [ ] **Better tooltip placement** near screen edges. Use the public
      `GuiGraphics.renderTooltip(Font, List<FormattedCharSequence>, ClientTooltipPositioner, int, int)`
      overload with a custom positioner: anchor to the hovered tab's centre, clamp horizontally, and
      never overlap the bar.

### What the density ladder does not fix

Verified in game, and worth being straight about: at **GUI scale 4 on a 1920x1080 screen the game
hands us a 480x270 screen**, which leaves 440 logical pixels for tabs. Ten tabs is 44 pixels each,
and a readable label needs about 54. So at that scale, past roughly eight tabs, **names cannot come
back no matter how the horizontal strip is sized** — there is no room, and any rule that claims
otherwise is lying about the arithmetic.

The ladder fixes the close button vanishing, which was a real defect. Names at high tab counts are
not a sizing problem; they are a *direction* problem, and the fix for them is the next section.

### Vertical tabs

Wanted in their own right, not only as a narrow-screen fallback. There is a good precedent inside
Minecraft modding: **AE2: Tabbed View Cells** places its tabs to the right of the terminal *or above
it, depending on GUI scale and settings* — orientation as a function of available space.

- [ ] **Auto rule:** go vertical when the screen affords at least six 18px rows of tab column *and*
      the horizontal strip would otherwise be at icon-only density or scrolling. Config offers
      `Auto / Horizontal / Vertical`, defaulting to Auto.
- [ ] **Left edge, not right.** The right side is EMI's own sidebar territory and we already added a
      page there; the tree pans horizontally, so the left gutter is the free one.
- [ ] Progress moves from a corner marker to a **left stripe** — a stripe reads down a column at a
      glance, a corner dot does not.

Vertical buys the thing horizontal structurally cannot: full names at any tab count, plus the room
where groups become legible.

### Refactor first

- [x] **Split `TabBar.java`'s geometry out** into `TabLayout`, which imports nothing from Minecraft
      or EMI and is covered by 27 tests.
- [ ] **Split rendering from input.** They still share a class. Worth doing eventually, but the
      geometry was where the value was and this half has not bought anything yet.

Not tidiness: every tab bar bug so far has been a geometry bug — the drag-drop origin measured from
the wrong x, the inclusive right edge — and geometry is the one piece testable without launching
Minecraft. Both of those regressions now have tests. It is also what makes the live config preview
(3.4) possible.

### Visual direction

- The **colour coding works** and should stay — it carries state legibly.
- The **shapes do not.** "Floaty like a browser" translated into Minecraft means **the gap and the
  separate surface, not rounded corners** — Minecraft's interface has no rounded corners anywhere.
  So: each tab is its own bevelled panel (light top and left, dark bottom and right, vanilla's own
  scheme), separated by a 2px gap, sitting on the dim overlay rather than carved out of one
  continuous strip. The active tab is brighter and drops its bottom bevel so it reads as joined to
  the content.
- [ ] **Draw proper icons** instead of borrowing font glyphs. Author in SVG, export to 16×16 PNG at
      1× and 2×. Five sprites: craft-all (a 2×2 crafting grid with a check — Minecraft's own
      vocabulary for "make things"), new tab, scroll arrows (reuse vanilla's 6×9 triangles), park,
      close.
- [ ] **Replace the toggle-all icon.** The three-bar glyph reads as "switch to vertical tabs" — and
      it should, because `≡` *is* the vertical-tabs icon in every browser shipping today. The fault
      is borrowing a glyph, not choosing the wrong one.

## 3.2 — Tab groups, which are really phases

Driven by a real workflow rather than tidiness: partway through a large build you realise you need
one whole set of items *before* a later set of machinery. The request was "group tabs". The problem
is that a big build has **phases**, and the crafting list insists on totalling all of them at once.

So the primitive is not a folder. It is a group with an active/parked flag:

- [ ] A group is a name, a colour, a collapsed flag and a **parked** flag.
- [ ] **Parking excludes a group's trees from the aggregated crafting list** while keeping the tabs.
      That is the whole point — you stop being told to gather machinery parts while you are still
      making planks.
- [ ] One action: **park everything except this group.** That is the workflow in a single click.
- [ ] Horizontal mode shows a coloured group chip before each run of tabs; vertical mode shows real
      headers.
- [ ] **Persistence format change.** `TabCodec` gains a groups array and each tab carries a group id.
      Version the file and migrate on read — a mod that loses your tabs on upgrade is worse than one
      without groups.

## 3.3 — Working across trees

Both of these came out of actually playing with the mod, and both are only possible *because* it
holds several trees at once. Nothing else in the recipe-viewer space can do either.

### Sync a sub-recipe across every tree

**The problem, as it happened:** progression unlocked a cheaper way to make an intermediate part —
a fan, say, which has a cheap recipe and an expensive one. Changing the default on one tree worked.
The other eight machines still quietly used the expensive recipe, and the only way to find them was
to open each tree and hunt through it.

- [ ] **Shift-click a resolution to apply it to every tree that uses that ingredient.** Not the whole
      tree — just that one sub-craft.
- [ ] **Or find them:** search for a sub-recipe and highlight the trees using it, so you can decide
      per tree rather than changing all of them blind.
- [ ] A confirmation showing how many trees would change, since this edits trees you are not
      looking at.

Feasible cheaply: `MaterialTree.resolutions` is a plain `Map<EmiIngredient, EmiRecipe>` that this mod
already serialises per tab in `TabCodec`. Applying one across tabs is a loop and a recalculation.

### Show where a shared material is actually going

**The problem, as it happened:** ten machines being built, several of them consuming copper in
different forms — plates, pipes, melted copper. The crafting list correctly says *"you need 184
copper"*, but not how much is for which, so there is no way to know whether spending copper on
plates now starves the pipes later. Working it out means doing the arithmetic by hand.

- [ ] **Hover a material in the crafting sidebar to break its total down by what needs it** — which
      tree, how much, and for which sub-craft.
- [ ] Show it in the same hover, not a separate screen: the question is asked mid-decision.

Feasible cheaply too, and further along than it looks: `CraftingFavorites.aggregate` already builds
`Map<EmiIngredient, Set<TreeTab>> costOwners` while summing the list, so it knows *which* tabs need
each shared material. Widening that set into a per-tab quantity map gives the breakdown almost for
free.

## 3.4 — A settings screen people can read

The goal: nobody should ever need to open `emitreetabs.json`.

- [ ] **Move from Cloth Config to YACL.** Cloth is stale by its own developer's account. YACL is
      actively developed (~119.9M downloads), supports **Forge 1.20.1 and NeoForge 1.20.4+** — exactly
      our matrix — and offers tabs, collapsible groups, several controls per data type, and **rich
      descriptions with image previews**. Note YACL will not support Forge past 1.20.1, which happens
      to be where we stop anyway.
- [ ] **Name the tabs by intent, not by code:** *Tabs & layout*, *Crafting list*, *Behaviour & keys*.
- [ ] **Give every option a picture.** Nobody reads "fold shared materials by default"; everybody
      understands a still of it folded. Largest usability gain available, and mostly a screenshotting
      job.
- [ ] **A live tab bar at the top of the layout tab** — five sample tabs that redraw as you change
      density and orientation. Needs the layout engine from 3.1 to exist first, which is why this
      milestone comes after it.
- [ ] **No bare numbers.** Sliders labelled at both ends (*Narrow ←→ Wide*), never a raw integer
      without a range.
- [ ] **Show every default with a one-click reset**, and collapse anything that touches the
      persistence format into an *Advanced* group.
- [ ] **Keep the JSON working.** YACL is the front end, not the store — pack authors ship configs as
      files and should keep being able to.

## 3.5 — Public API

Small, versioned, and checked at runtime. Needed by [Quartermaster](../quartermaster); see
*Decisions taken*.

- [ ] Register a **stock source** that feeds the crafting list's arithmetic, rendered distinctly from
      the player's own inventory, off by default.
- [ ] Register a **locate provider** for an item, surfaced from a shortfall in the crafting list.
- [ ] A **listener** for crafting-list changes.
- [ ] Version the API explicitly and degrade to nothing when a consumer's version does not match.
      `NoSuchMethodError` on a user's machine is the failure mode to design against.

## 4.0 — Minecraft 1.21.1

- [ ] NeoForge support on 1.21.1.
- [ ] Fabric support on 1.21.1.

The parts of this mod that reach into EMI's internals were checked against EMI 1.1.24 for 1.21.1
and are unchanged, so this is mostly loader plumbing rather than a rewrite.

Moved *after* the interface work deliberately: the UI is being rewritten anyway, and doing it once
on the current version beats doing it twice.

## Ideas taken from EMI's own issue tracker

People have been asking EMI for these for years. Several are things an addon can do without
upstream, and each one is a group of users who already want it. Numbers are EMI issues.

| Idea | EMI issue | Note |
|---|---|---|
| Ingredient merging / partial usage across a tree | [#1247](https://github.com/emilyploszaj/emi/issues/1247) | Newest, and closest to the aggregation we already do |
| Collapse the tree by layer | [#1104](https://github.com/emilyploszaj/emi/issues/1104) | Fold-state work we already touch |
| Favourite groups and subgroups | [#528](https://github.com/emilyploszaj/emi/issues/528) | Independent confirmation that 3.2 is wanted |
| Remember which synthetic favourites you already obtained | [#162](https://github.com/emilyploszaj/emi/issues/162) | Fits the per-tab progress we already track |
| Names on recipe trees | [#1151](https://github.com/emilyploszaj/emi/issues/1151) | **We already do this** — tab rename |
| Sophisticated Backpacks counted by the tree | [#1024](https://github.com/emilyploszaj/emi/issues/1024) | Lands naturally once the stock-source API exists |
| Reverse tree (what can I make from this?) | [#98](https://github.com/emilyploszaj/emi/issues/98) | Open since 2022, nobody has built it |
| Hide sidebars when empty | [#185](https://github.com/emilyploszaj/emi/issues/185), [#348](https://github.com/emilyploszaj/emi/issues/348) | Applies to our own crafting page |
| Independent sidebar GUI scale | [#897](https://github.com/emilyploszaj/emi/issues/897) | Would fix several of our own layout complaints |

## Watching upstream

**EMI implementing multi-tree natively is this project's largest single risk**, and it is further
along than a feature request:

- [#1041 Multitree / Add Tree](https://github.com/emilyploszaj/emi/issues/1041) — open since Sept
  2025, 20 comments, describes this mod almost exactly.
- [PR #1100](https://github.com/emilyploszaj/emi/pull/1100) — a working implementation by a
  contributor, +544/−82 across 18 files. **Open, conflicting, last updated Feb 2026.** Two review
  comments, no approval.
- Related PR #1101 (multi-tree plus bookmarks) was closed. PR #959 (search bookmarks) is open and
  conflicting.
- EMI's last release was **May 2026**, with unmerged PRs going back to July 2026.

So it is not imminent, but code exists and people are already running patched EMI builds to get it.
If it lands, tree-swapping stops being a reason to install this mod. What survives: groups and
phases, persistence, the crafting list's shrinking inventory pool, the settings screen, and the
Quartermaster integration. **Keeping those separable from the tree-swapping code is the insurance**,
and it is the same module boundary the public API already requires.

## Found in testing, to fix

- [ ] **Section headers are only clickable on their first slot.** The title text runs across the
      whole row, but only the leftmost ~18px responds, so clicking the words does nothing and the
      feature looks broken. The row is 18px tall by necessity — `ScreenSpace.getY` is `ty + row * 18`
      and hover maps back through separate inverse arithmetic — but the *hit area* can span the full
      row even when the drawn slot does not. Fix the inverse mapping, not the layout.
- [ ] **The tab bar's contrast is too low.** Measured: bar background `(29,24,18)` against tab
      `(37,34,32)`. Vanilla's own panels are `(198,198,198)` on a 60%-black overlay. Give the bar a
      real panel fill and a 1px border rather than a tint.
- [ ] **A tag and a plain item read as duplicates.** A furnace wants `#stone_tool_materials` while a
      piston wants Cobblestone, so they correctly stay in separate sections — but to a reader they
      look like the same grey block listed twice. Worth making the distinction visible rather than
      leaving people to hover and work it out.

## Known limitations that are unlikely to change

These are documented in the [README](README.md) with the reasoning:

- Section titles in the crafting sidebar take a full row, because EMI's sidebar is a fixed grid and
  mouse position is mapped back through the same arithmetic. Shrinking the row risks clicks landing
  on the wrong item.
- Sections align to rows rather than pages, so a section can straddle a page break.
- There is no single combined tree view, because a merged tree could only hold one recipe per
  ingredient.

## Suggesting something

Open a [feature request](../../issues/new/choose). Describing the problem you are trying to solve is
more useful than describing a solution.
