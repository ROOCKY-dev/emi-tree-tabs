# Roadmap

What is planned for EMI Tree Tabs, roughly in order. **No dates are promised** — this is a
spare-time project, and items move or get dropped when they turn out to be worse ideas than they
looked.

Current release: **2.0.1** (Minecraft 1.20.1, Forge).

## Supported versions

The loader list is not a preference, it is dictated by what EMI itself publishes:

| Minecraft | Fabric | Forge | NeoForge |
|---|---|---|---|
| 1.20.1 | planned | **supported now** | EMI does not build for it |
| 1.21.1 | planned | EMI stopped at 1.20.2 | planned |

Forge is a dead end after 1.20.2 because EMI publishes no Forge builds past that, so moving to
newer Minecraft means moving to NeoForge. 1.20.1 will keep being supported from its own branch
after the port; it is not going to be abandoned the day 1.21.1 works.

## 2.1

- [ ] **Development infrastructure** — CI on every push and pull request, a build attached to each
      release automatically, issue and pull request templates. *(in progress)*
- [ ] **Toggle crafting for every tab at once**, rather than `Ctrl+click`ing each one.

## 3.0 — Multiple loaders and Minecraft 1.21.1

- [ ] Split the project so the Minecraft-and-EMI code is shared and only the loader entrypoints
      differ.
- [ ] Fabric support on 1.20.1.
- [ ] NeoForge support on 1.21.1.
- [ ] Fabric support on 1.21.1.

The parts of this mod that reach into EMI's internals were checked against EMI 1.1.24 for 1.21.1
and are unchanged, so this is mostly loader plumbing rather than a rewrite.

## 3.1 — Interface work

- [ ] **UI and UX overhaul** — clearer and cleaner on every screen size, not just a large window at
      default GUI scale.
- [ ] **Vertical tabs**, for people who would rather spend horizontal space on the tree.
- [ ] **Better tooltip placement**, especially for tabs near a screen edge, where tooltips currently
      get shoved against the edge or overlap the tab bar.

## 3.2 and later

- [ ] **Group tabs together**, for keeping related trees in one place. Needs a change to how tabs
      are saved, so it is deliberately after the interface work.

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
