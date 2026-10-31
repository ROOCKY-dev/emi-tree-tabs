# AI use in this project

Modrinth requires projects to disclose the use of generative AI. This is that disclosure.

## Short version

**This mod is developed with AI assistants, including Anthropic's Claude and OpenAI's Codex,
working from the maintainer's direction.** The maintainer sets the goals, tests the builds in
Minecraft, reports bugs, and accepts or rejects interface changes. The original 1.20.1 work was
checked in a GregTech pack; the 1.21.1 port was playtested separately on NeoForge and Fabric.

The pixel icons and English language file were also made during AI-assisted development. Icons
are small pixel patterns exported to SVG and PNG by `art/make_icons.py`. There are no generated
sounds or illustrations. README screenshots and the page-switching recording are real captures
supplied by the maintainer.

## What that means in practice

- **Design decisions were mine.** The tab metaphor, the crafting-list grouping, wanting a real
  second sidebar panel rather than a hijacked favourites panel — those came from me, and several of
  the assistant's first proposals were rejected before we landed on what shipped.
- **Implementation was the assistant's**, including the EMI mixins, the reflection used to add a
  sidebar page type, and the layout work.
- **Verification was real, not assumed.** Behaviour was checked against EMI 1.1.24's actual
  bytecode rather than guessed at, and every release was run in-game before the next one was
  started. Several bugs found this way are documented in the commit history.

## Known limitations of that process

Being straight about it: an AI wrote this, and AI-written code can be confidently wrong. Two
examples that were caught during development and are worth knowing about:

- An early build used `type="required"` in `mods.toml`, which is NeoForge syntax that Forge 47
  rejects outright. It was caught on the first launch.
- A planned design for merging trees would have thrown `IndexOutOfBoundsException` inside EMI's
  cost calculation. It was caught by reading EMI's source before writing the code, not by testing.

Both were found and fixed. The point is that a reviewer should treat this code the way they would
treat any code from a contributor they have not worked with before.

## Reporting problems

If you hit a bug, please open an issue with the crash report or `latest.log`. The mod hooks EMI
internals rather than a public API, so an EMI update is the most likely thing to break it.
