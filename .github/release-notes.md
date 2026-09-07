## Craft every tab at once

A new button on the tab strip, or `Ctrl+A`, flips every tab between viewing and crafting, and shows
how many are currently being crafted. Past about half a dozen tabs the state was otherwise only
legible by checking each icon's corner marker one at a time.

`Ctrl`+click a recipe's tree button now opens that tree already in crafting mode, instead of opening
it to read and making you flip it afterwards.

## Fixed: drag-to-reorder dropped tabs in the wrong place

The drop position was measured from the wrong origin — one that differs by the width of the scroll
arrows, so the error only appeared once you had enough tabs for the arrows to show, which is exactly
when you want to reorder. Drops could land up to a third of a tab out. Present since 2.1.0.

Also tightened the strip's right edge, which could select a tab that had been clipped out of view.

## Notes

Requires EMI 1.1+, Minecraft 1.20.1, client-side only. Cloth Config is optional and only drives the
in-game settings screen; `config/emitreetabs.json` works without it.
