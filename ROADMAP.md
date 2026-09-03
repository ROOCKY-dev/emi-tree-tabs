# Roadmap

What is planned for EMI Tree Tabs, roughly in order. **No dates are promised** — this is a
spare-time project, and items move or get dropped when they turn out to be worse ideas than they
looked.

Current release: **2.2.0** (Minecraft 1.20.1, Forge and Fabric).

## Supported versions

The loader list is not a preference, it is dictated by what EMI itself publishes:

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 1.20.1 | **supported now** | **supported now** | EMI does not build for it |
| 1.21.1 | planned | EMI stopped at 1.20.2 | planned |

Forge is a dead end after 1.20.2 because EMI publishes no Forge builds past that, so moving to
newer Minecraft means moving to NeoForge. 1.20.1 will keep being supported from its own branch
after the port; it is not going to be abandoned the day 1.21.1 works.

## 2.1 — done

- [x] **Development infrastructure** — CI on every push and pull request, a build attached to each
      release automatically, issue and pull request templates.
- [x] **Multiloader restructure** — the Minecraft-and-EMI code is shared; only entrypoints differ.
- [x] **Fabric support on 1.20.1.**
- [x] **Toggle crafting for every tab at once**, rather than `Ctrl+click`ing each one.

## 3.0 — Minecraft 1.21.1

- [ ] NeoForge support on 1.21.1.
- [ ] Fabric support on 1.21.1.

The parts of this mod that reach into EMI's internals were checked against EMI 1.1.24 for 1.21.1
and are unchanged, so this is mostly loader plumbing rather than a rewrite.

## 3.1 — Interface work

The bar works but does not feel finished. Notes from actually using it with ten trees open:

- [ ] **Tabs shrink until they lose their close button.** Around ten tabs the width drops below the
      threshold that shows the `x`, so the only way to close one is a keyboard shortcut. Shrinking
      away the primary affordance is the wrong trade.
- [ ] **Names vanish at that size too**, leaving bare icons.
- [ ] **Meanwhile the bar is mostly empty.** Tabs squeeze themselves into a fraction of the width
      while the rest sits unused. Whatever the sizing rule is, it is not responsive in any useful
      sense — it should spend the space it has.
- [ ] **Scrolling the tabs** should be a real, obvious interaction rather than something that only
      appears once things have already gone wrong.
- [ ] **Vertical tabs.** Wanted in their own right, not just as a fallback for narrow screens.
- [ ] **Better tooltip placement** near screen edges, where tooltips get shoved against the edge or
      overlap the bar.

### Group tabs together

Driven by a real workflow rather than tidiness: partway through a large build you realise you need
one whole set of items *before* a later set of machinery. The useful action is to park the current
group, start a new one for the new set, and come back later — not to close and re-open trees.

So grouping needs to support disabling or setting aside a whole group, not merely colouring it.

### Visual direction

- The **colour coding works** and should stay — it carries state legibly.
- The **shapes do not**. They read as flat blocks. The reference point is how a browser handles
  tabs: something lighter, floating, with a clear active tab — translated into something that still
  looks like it belongs in Minecraft rather than pasted in from a website.
- **The toggle-all icon is wrong.** The three-bar glyph reads as "switch to vertical tabs" at a
  glance; it was misread that way on first sight. It needs an icon that says *crafting*, not
  *layout*.
- Consider **drawing proper assets** for these icons rather than borrowing font glyphs, which is
  what makes them look improvised.

## 3.2 — Tab groups

Moved up from later: see the workflow note above. Needs a change to how tabs are saved.

## 4.0 — Chest tracker (idea, not committed)

A bigger feature, and further out. When a crafting tree gets large the hard question stops being
*what do I need* and becomes *where is it*. This would answer that from inside EMI.

The shape of it:

- **Client-side, and it does not scan.** Nothing periodically reads chests around you, which would
  be both a performance problem and arguably an unfair one. It records what a container held **when
  you opened it**, and nothing more.
- **A button that shows where a material is.** From the crafting list, ask "where is my iron" and
  get the containers that had some, and where they are.
- **Integrity matters more than completeness.** Remembered contents go stale the moment someone
  takes something out, so the feature lives or dies on being honest about age and confidence rather
  than presenting a remembered snapshot as fact.
- **It has to cope with sprawl.** Someone with sixty scattered chests is exactly who needs this, and
  is also who a naive list would fail.
- **New UI territory.** Everything the mod does today lives in menus; this one has to talk about
  positions in the world, which is a different design problem.

Deliberately parked until the tab and crafting-list work is genuinely finished. It is a large enough
idea to deserve its own version rather than being bolted onto a point release.

## Found in testing, to fix

- [ ] **Section headers are only clickable on their first slot.** The title text runs across the
      whole row, but only the leftmost ~18px responds, so clicking the words does nothing and the
      feature looks broken. The hit area should be the row.
- [ ] **The tab bar's contrast is too low.** Measured: bar background `(29,24,18)` against tab
      `(37,34,32)`. The buttons at the right edge are almost invisible until you know they are there.
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
