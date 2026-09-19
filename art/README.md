# Icon sources

The tab bar and the sidebar draw from one sprite sheet,
`common/src/main/resources/assets/emitreetabs/textures/gui/icons.png`. **Do not edit that file.**
It is generated:

```bash
python3 art/make_icons.py
```

Needs `rsvg-convert` (librsvg) and Pillow. It writes `art/icons/<name>.svg` from the pixel maps in
the script, rasterises each one, packs them into the sheet, and writes `art/preview.png` — the
whole sheet at 8x on a dark backing, for looking at the art without launching Minecraft.

The SVGs are tracked. The per-sprite PNGs are intermediates and are gitignored.

## Why the sprites are not all 16x16

The roadmap asked for 16x16 at 1x and 2x. Neither survived contact with the controls:

- **The controls are not 16px.** The close badge is 9, the fold caret sits in 10, a scroll arrow
  gets 9 of width, and the craft-all button is 18x20. A uniform 16x16 cell would mean scaling every
  sprite at draw time, which at these sizes is exactly the mush the drawn icons were meant to
  replace. Each sprite is instead the size of the thing it goes in.
- **2x does not help Minecraft.** GUI textures are scaled by the GUI scale with nearest-neighbour
  filtering, so a 32px sprite drawn into a 16px logical box drops every other pixel at GUI scale 1.
  One sheet at 1x is what the game actually wants; the SVG sources are the resolution-independent
  copy if a high-res variant is ever needed.

## Why white masks

Every sprite is a white alpha mask, tinted by `Icons.draw` at the moment it is painted. The strip
and the sidebar say a lot with colour already — dimmed, hovered, active, crafting, close-hover red —
and a pre-coloured sprite would need one copy per state.

## Why the gear became sliders

A 12x12 gear was drawn first. Its teeth and bore collapse at that size into something that reads as
a face rather than a cog; three sliders read correctly at 12px and say "settings" without relying on
the viewer resolving 2px detail. The rejected gear is recorded here so it does not get re-attempted.
