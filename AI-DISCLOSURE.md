# AI use in this project

Modrinth requires projects to disclose the use of generative AI. This is that disclosure.

## Short version

**The code in this mod was written by an AI assistant (Anthropic's Claude), working from my
direction, in a back-and-forth over a single day.** I set the goals, tested every build in a real
293-mod GregTech pack, reported the bugs and rejected the designs I did not want. I did not write
the Java by hand.

No AI-generated art, textures, sounds or translations are included. The mod ships no assets beyond
a single English language file, which is also AI-written.

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
