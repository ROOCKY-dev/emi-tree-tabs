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

## The 3.x line — the interface overhaul

Everything from here to 4.0 is one piece of work delivered over several versions. It ships to
GitHub as it lands, and reaches players **once, as 4.0** — and only when the maintainer says so.

**No 3.x version goes to Modrinth or CurseForge.** Store publishing is opt in by design (a
`store-v*` tag, see [PUBLISHING.md](PUBLISHING.md)), which is exactly what makes a long overhaul
possible without shipping half-finished interfaces to players.

| Version | Carries |
|---|---|
| 3.1 | Tab bar sizing (**done**), tooltip placement, drawn icons |
| 3.2 | **The vertical sidebar**, then tab groups and phases on top of it |
| 3.3 | Working across trees — formula input, resolution sync, shared-material attribution |
| 3.4 | The settings screen, on YACL |
| 3.5 | The public API |
| **4.0** | **The overhaul, released.** Store tag pushed only on the maintainer's confirmation. |

### 3.1 — Tab bar sizing

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
- [x] **Tooltip placement** — a `ClientTooltipPositioner` that anchors to the *control* rather than
      the cursor, pushes clear of the whole strip or panel, and clamps last. The default positioner
      is fine mid-screen and wrong at the edges, which is exactly where tabs live: at the top it puts
      the tooltip over the bar it describes, and the sidebar occupies a whole edge.

### What the density ladder gives you at each GUI scale

Measured, not guessed. Available width is the logical screen minus padding and the two buttons;
per-tab width is that divided by the tab count. On a 1920x1080 screen:

| GUI scale | Logical screen | 8 tabs | 10 tabs | 12 tabs | 20 tabs |
|---|---|---|---|---|---|
| 2 | 960 x 540 | 115px names | **92px names** | 76px names | 46px icons |
| 3 | 640 x 360 | 75px names | **60px names** | 50px icons | 30px icons |
| 4 | 480 x 270 | 55px names | 44px icons | 36px icons | 28px icons, scrolling |

So names survive well past ten tabs at scale 2 and 3, and run out around eight at scale 4 — which is
what Minecraft picks by default on a 1080p screen, and where the original complaint came from.

Changing GUI scale is a real answer for a player, not a workaround, and the ladder makes the
trade-off legible instead of silently discarding the close button. Vertical tabs remain worth
building, but for the reason in the next section — room for names *and* group headers at any scale —
not because horizontal sizing cannot cope.

### Vertical tabs — a floating sidebar

Not a narrow-screen fallback. This is the primary layout the overhaul is built around, and it is
specified rather than sketched, because the shape carries the tab-group design too.

**The panel**

- Takes roughly **one fifth of the screen width**, leaving four fifths for the focused tree.
- **Floats.** A clear **10–20px gap on every side** — it is not glued to the top, bottom or either
  edge. That gap is the whole reason it reads as a panel over the tree rather than a chrome strip
  bolted to the window.
- Two small but visible buttons in the **bottom corners**: **settings**, and **toggle crafting for
  every tree**.

**A tab**

- A rectangle carrying the **item icon** and the **batch count**.
- **Hovering swaps the batch count for a crafting-mode toggle** for that tree — the number is only
  information until you reach for it, at which point the same spot becomes the control.
- **The name shows when there is room**, which at a fifth of the screen is most of the time. That is
  the thing the horizontal strip structurally could not do.
- Clicking focuses that tree. **State is carried by the tab's border colour**, not by a corner pip.

**A group**

- Same rectangle, but the **group header is textured differently**: where a tab has only a border,
  the header **fills the sidebar width as its background**.
- It shows the **number of trees** in place of a batch count, and **hovering it toggles crafting for
  every tree in that group** — the same gesture as a tab, scoped to the group.
- Every tab belonging to the group sits **inside a larger border** that starts at the header, so
  membership is visible without reading anything.
- **Collapsible**: clicking the header folds the whole group into it. Click only — never hover,
  since hover is already the crafting toggle.

Open questions to settle while building, not before:

- [x] **Which side** — left. The right is EMI's own sidebar territory and this mod already added a
      page there; the tree pans horizontally, so the left gutter is the free one.
- [x] **When it appears** — `tabOrientation` config of `auto / horizontal / vertical`. Auto takes
      the sidebar whenever the screen can host it, and falls back to the strip when it cannot. Even
      an explicit `vertical` falls back, because a screen too small to draw it is still too small.
- [x] **What happens to the strip** — it stays, as the fallback, behind a `TabUi` dispatcher so the
      mixin never asks which layout is live. Whether the sidebar becomes the default everywhere is
      still open, and is now answerable by comparing them in game.
- [ ] **Drag to reorder in the sidebar.** The strip has it; the sidebar does not yet.

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
- [x] **Draw proper icons** instead of borrowing font glyphs. Ten sprites on one 64×32 sheet,
      authored in `art/icons/*.svg` and packed by `art/make_icons.py`: craft-all (a 2×2 crafting
      grid with a check — Minecraft's own vocabulary for "make things"), the same check alone for a
      row's marker, new tab, close, park, both scroll arrows, both fold carets, settings. White
      alpha masks, tinted at draw time, which is what keeps the dimmed / hovered / active /
      crafting / close-hover states down to one sprite each.
      Two deviations from the spec above, written up in `art/README.md` so they are not re-tried:
      sprites are the size of the control they fill rather than a uniform 16×16 (the close badge is
      9px, the fold caret 10, an arrow 9), and there is no 2× sheet because nearest-neighbour
      filtering makes a 32px sprite in a 16px box drop every other pixel at GUI scale 1. The scroll
      arrows are drawn here rather than borrowed from vanilla's 6×9 triangles, so the sheet stays
      self-contained across the 1.21 port. A gear was drawn for settings and rejected — at 12px it
      reads as a face — and replaced with three sliders.
- [x] **Replace the toggle-all icon.** The three-bar glyph read as "switch to vertical tabs" — and
      it should, because `≡` *is* the vertical-tabs icon in every browser shipping today, which got
      worse once this mod grew a real vertical layout. It is now the craft-all sprite.
- [x] **Park became visible.** Not on the list, but it fell out of drawing the sprite: a parked
      group showed only as dimmed text, which reads as "disabled" rather than "set aside", and not
      at all when the name was too long to draw. It now carries two bars beside its marker.

## 3.2 — The sidebar, and groups on top of it

### Tab groups, which are really phases

Driven by a real workflow rather than tidiness: partway through a large build you realise you need
one whole set of items *before* a later set of machinery. The request was "group tabs". The problem
is that a big build has **phases**, and the crafting list insists on totalling all of them at once.

So the primitive is not a folder. It is a group with an active/parked flag:

- [x] A group is a name, a colour, a collapsed flag and a **parked** flag.
- [x] **Parking excludes a group's trees from the aggregated crafting list** while keeping the tabs,
      and without touching their own crafting flags, so unparking restores what you had.
      That is the whole point — you stop being told to gather machinery parts while you are still
      making planks.
- [x] One action: **park everything except this group** — shift right click on a header.
- [x] Vertical mode shows real headers, coloured per group.
- [x] Horizontal mode is no longer group-blind — but not with chips. A chip means a run of
      adjacent tabs, and the strip has none: it draws tabs in master order while the sidebar
      derives its order from group membership. Making the strip group-ordered would put
      non-uniform elements in the row, which every hit test, the drag drop and the scroll
      arithmetic assume away — **the same structural problem that deferred the active-tab width
      bonus, and the same job.** So a strip tab carries 2px of its group's colour down its leading
      edge (membership, not adjacency), the group's name on its tooltip, and the sidebar's parked
      dimming.
- [x] **Persistence format change** — file version 2, groups array, group id per tab. Version 1
      files still load and simply have no groups.

## 3.3 — Working across trees

Both of these came out of actually playing with the mod, and both are only possible *because* it
holds several trees at once. Nothing else in the recipe-viewer space can do either.

### Sync a sub-recipe across every tree

**The problem, as it happened:** progression unlocked a cheaper way to make an intermediate part —
a fan, say, which has a cheap recipe and an expensive one. Changing the default on one tree worked.
The other eight machines still quietly used the expensive recipe, and the only way to find them was
to open each tree and hunt through it.

- [x] **Apply a resolution to every tree that uses that ingredient.** Not the whole tree — just
      that one sub-craft. **Not on shift-click, and it cannot be:** EMI's output slot accepts an
      unmodified click and `Ctrl`+click and swallows every other modifier, so shift- and alt-click
      never reach `addResolution` at all. There is no gesture left on EMI's own control. So the
      choice is noticed, the trees that differ are counted, and `Ctrl+Shift+S` — a key this mod
      owns — applies it. Nothing changes by itself; silently rewriting trees the player cannot see
      would be the wrong behaviour anyway.
- [x] **Or find them:** candidate trees are outlined in both layouts while the offer is live, and
      **Shift+click a highlighted tab applies the choice to that tree alone**, leaving the offer up
      for the rest. That is the "decide per tree rather than changing all of them blind" half.
- [x] A confirmation showing how many trees would change, since this edits trees you are not
      looking at — a toast before, and a second one reporting what actually changed.

Feasible cheaply: `MaterialTree.resolutions` is a plain `Map<EmiIngredient, EmiRecipe>` that this mod
already serialises per tab in `TabCodec`. Applying one across tabs is a loop and a recalculation.

### Type a quantity, or the sum that produced it

**The problem, as it happened:** wanting a specific number of one part rather than whatever the
whole tree implies, and the number being arithmetic you did it in your head — *32 machines, 4 each,
2 per that* — so setting it means leaving the tree for a calculator and coming back with 256.

- [x] **An expression evaluator** — `32 * 4 * 2` is as valid as `256`. Four operators, brackets,
      `x` as well as `*`, every refusal carrying a readable reason, overflow refused rather than
      wrapped. 20 tests.
- [x] **Shows the result while you type**, green when it parses and the reason in red when it does
      not, suppressed for a plain number. Enter on an invalid expression refuses and leaves the text
      editable rather than closing.
- [x] **Reachable from the thing being counted** — control-click a tab's marker, which is the square
      already showing the batch count. Plain click still toggles crafting; the modifier acts on what
      the square displays.
- [ ] The same, on a *node* rather than a whole tree. **Still deliberately not built**, on the
      reasoning below: it fights the solver rather than using it, and the question of whether it is
      wanted was explicitly deferred until the cheap reading has been used. It has not been used
      yet, so the answer is still unknown and building it now would be guessing.
- [x] **Scope decided: the cheap reading first.** `MaterialTree.batches` is what the input sets, and
      a sub-craft quantity is usually reachable by opening a tree on that sub-item and setting its
      batches. Whether pinning an amount on a node is still wanted is a question for after this has
      been used. The two readings, kept because the second may yet be needed:
      - Setting `MaterialTree.batches` from a formula is nearly free — the field exists, this is an
        input widget and a small expression parser.
      - Pinning a target amount on a *sub-node* means overriding an amount EMI derives top-down from
        the goal, which fights the solver rather than using it.
      Ship the cheap one first and see whether it actually covers the case; it may well, since a
      sub-craft quantity is usually reachable by setting the batch count of a tree opened on that
      sub-item.

### Show where a shared material is actually going

**The problem, as it happened:** ten machines being built, several of them consuming copper in
different forms — plates, pipes, melted copper. The crafting list correctly says *"you need 184
copper"*, but not how much is for which, so there is no way to know whether spending copper on
plates now starves the pipes later. Working it out means doing the arithmetic by hand.

- [x] **Hover a material to see what wants it** — which tree and how much, sorted by demand. Hidden
      when only one tree wants it, since that is not a split, and capped at six lines.
- [x] Break it down by *sub-craft* as well as by tree. On **Shift**, because both splits at once
      is how a tooltip becomes the problem rather than the answer. With one tree it shows flat;
      with several, each tree's line carries its own sub-crafts, since "60 for plates" summed
      across two machines does not tell you which machine to stop building.
      **The numbers are captured, not calculated.** Walking the tree and adding up leaves gives
      wrong answers: `TreeCost.calculateCost` spends remainders as it descends, so what a leaf
      costs depends on the order the walk reached it. A second walk cannot reproduce that without
      reimplementing the solver, and a tooltip whose whole purpose is arithmetic must not show
      arithmetic of its own. So `TreeCostMixin` rides along with EMI's own walk and takes the
      figures it records. Two more private-method mixins, both `require = 0`: if EMI renames
      either, the split quietly does not appear rather than the mod refusing to load.
- [x] Shown on the material's own tooltip, not a separate screen.

Feasible cheaply too, and further along than it looks: `CraftingFavorites.aggregate` already builds
`Map<EmiIngredient, Set<TreeTab>> costOwners` while summing the list, so it knows *which* tabs need
each shared material. Widening that set into a per-tab quantity map gives the breakdown almost for
free.

## 3.4 — A settings screen people can read

The goal: nobody should ever need to open `emitreetabs.json`.

- [x] **Move from Cloth Config to YACL** — `3.6.6+1.20.1`, the last release for this Minecraft
      version. The screen lives in `common` because `dev.isxander.yacl3.*` is identical on both
      loaders, which means **Fabric has a settings screen for the first time**: the Cloth one was
      written in the Forge module and never ported. Cloth is gone rather than kept as a fallback —
      two screens is two things to keep in step, and the json is the documented fallback.
- [x] **Name the tabs by intent, not by code:** *Tabs & layout*, *Crafting list*, *Behaviour & keys*.
- [ ] **Give every option a picture.** **Blocked, and not on code.** The plumbing is there —
      `OptionDescription.image(...)` takes a `ResourceLocation` and the descriptions are already
      built per option — but the pictures themselves are stills of the real interface in the real
      game, and there is no way to take one without a session. Mock-ups would defeat the point: the
      value is in showing what it actually looks like folded, not something close to it.
- [x] **A live tab bar at the top of the layout tab** — five sample tabs, drawn by the real
      `TabLayout` and `SidebarLayout` arithmetic and the real palette, so it cannot drift from what
      it previews. It reads the **pending** values rather than the saved ones, so it answers "what
      would this do" rather than "what did this do", and it reports which density the current width
      lands in. YACL has no arbitrary widget slot, so it is attached as the description of the two
      layout options — which is where it is looked at anyway.
- [x] **No bare numbers.** Every slider formats its own value: *32 tabs*, *Remember 16*, *Off —
      closing is final*, *Every 0.5s — responsive*.
- [x] **Show every default with a one-click reset** — YACL's own, from the default in each
      binding — **and collapse anything that touches the persistence format into an *Advanced*
      group**, one per category, closed, with a line saying a wrong answer there costs you tabs
      rather than looks.
- [x] **Keep the JSON working.** The screen writes through `TreeTabsConfig.save()`; nothing else
      changed about the file, and hand edits are still picked up within a couple of seconds.

## 3.5 — Public API

Small, versioned, and checked at runtime. Needed by [Quartermaster](../quartermaster); see
*Decisions taken*.

Written up for consumers in [API.md](API.md). Five files in `dev.roocky.emitreetabs.api`;
everything else is internal.

- [x] Register a **stock source** that feeds the crafting list's arithmetic, rendered distinctly
      from the player's own inventory (`Counted from: In chests`), off by default behind
      `useExternalStock`. Takes plain `ItemStack`s rather than EMI types — a container holds stacks,
      and a consumer should not have to learn EMI's stack model to say what is in a box; matching
      against tags stays Tree Tabs' job, because Tree Tabs is the one already carrying EMI.
- [x] Register a **locate provider** for an item, surfaced from a shortfall in the crafting list —
      asked about a material the list says you are short of, at the moment the cursor is on it.
- [x] A **listener** for crafting-list changes, fired after the pass publishes rather than during
      it, so a listener sees the finished list.
- [x] Version the API explicitly and degrade to nothing when a consumer's version does not match.
      One entry method with a frozen signature, `TreeTabsApi.registry(int)`, returning null rather
      than a registry that would break halfway through; everything else hangs off an interface, so
      adding to it never breaks an older consumer. 5 tests cover the gate, which is the one part
      whose failure mode is a crash on a user's machine in a build that compiled cleanly.
- [x] **Consumers are not trusted.** Every call out is wrapped: one that throws is logged once,
      naming the mod, and its integration is dropped for the session rather than taking the
      crafting list, the sidebar and the tree screen down with it.

## 4.0 — Release the overhaul

- [ ] Everything in 3.1 to 3.5 landed and used in a real world for more than a session.
- [ ] **Maintainer confirms.** Only then does a `store-v4.0.0` tag go up; nothing before it reaches
      Modrinth or CurseForge.
- [ ] `publish_game_versions` checked before tagging — it is what the stores are told.

## 5.0 — Minecraft 1.21.1

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

All three are fixed in code and **none has been seen in game yet** — they are colour and hit-area
changes, so the build passing says nothing about whether they look right.

- [x] **Section headers are only clickable on their first slot.** The title text runs across the
      whole row, but only the leftmost ~18px responded, so clicking the words did nothing and the
      feature looked broken. The row is 18px tall by necessity — `ScreenSpace.getY` is
      `ty + row * 18` and hover maps back through separate inverse arithmetic — so the fix was the
      inverse mapping, not the layout: the pad slots the title is drawn over now carry the header
      they belong to, and the click handler accepts either.
- [x] **The tab bar's contrast is too low.** Measured: bar background `(29,24,18)` against tab
      `(37,34,32)` — 1.11:1. The cause was the bar being 88% alpha over the tree, so the world bled
      through and diluted the difference. An opaque panel fill, lifted tab colours and a visible
      border rule give 1.81:1, with label text at 8.87:1 and the dimmed label at 5.38:1, both past
      WCAG AA. **The sidebar had the same bug at 1.09:1** — same constants, and it is what `auto`
      picks on any screen wide enough — so it got the same palette.
- [x] **A tag and a plain item read as duplicates.** A furnace wants `#stone_tool_materials` while a
      piston wants Cobblestone, so they correctly stay in separate sections — but a tag is drawn by
      cycling through its members, so most of the time both are the same grey block and the list
      reads as one thing listed twice. An entry that accepts any of several items now gets a 1px
      frame, drawn in the slot's own padding so it never covers the icon. `markChoiceEntries`.

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
