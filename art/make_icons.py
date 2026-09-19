#!/usr/bin/env python3
"""
Authors the tab bar's icon sheet.

The sprites are designed here as pixel maps, written out as SVG (one rect per lit pixel, on a
1-unit grid) and rasterised from that SVG by rsvg-convert. The SVG is the committed source; the
sheet PNG is generated and should never be edited by hand.

Every sprite is a white alpha mask. Colour comes from GuiGraphics.setColor at draw time, which is
what lets one sprite carry the dim / hover / active / crafting / close-hover states the strip and
the sidebar already distinguish by colour.

Run:  python3 make_icons.py
"""
import pathlib
import subprocess

OUT = pathlib.Path(__file__).parent
SHEET = OUT.parent / "common/src/main/resources/assets/emitreetabs/textures/gui/icons.png"

# '#' is opaque white, '+' is a 60% shadow pixel, '.' is transparent.
SPRITES = {
    # A 2x2 crafting grid with a check over its corner: Minecraft's own vocabulary for "make
    # things", which the borrowed three-bar glyph was not - that reads as "vertical tabs".
    "craft_all": (12, 12, """
#######.....
#..#..#.....
#..#..#.....
#######.....
#..#..#.....
#..#..#....#
#######...##
.........##.
.....#...##.
.....##.##..
......####..
.......##...
"""),
    # The same idea scoped to one tree, for the square at a row's end.
    "craft_one": (10, 10, """
..........
..........
........##
.......##.
......##..
#....##...
##..##....
.####.....
..##......
..........
"""),
    # A drawn plus, 2px strokes. The button forks the active tree, and a plus is what every
    # browser draws for it; the fault was borrowing the glyph, not choosing the shape.
    "new_tab": (12, 12, """
............
.....##.....
.....##.....
.....##.....
.....##.....
.##########.
.##########.
.....##.....
.....##.....
.....##.....
.....##.....
............
"""),
    "close": (8, 8, """
........
.#....#.
..#..#..
...##...
...##...
..#..#..
.#....#.
........
"""),
    # Two bars: a parked group has been set aside, not switched off.
    "park": (8, 8, """
........
.##..##.
.##..##.
.##..##.
.##..##.
.##..##.
.##..##.
........
"""),
    "arrow_left": (5, 8, """
....#
...##
..###
.####
.####
..###
...##
....#
"""),
    "arrow_right": (5, 8, """
#....
##...
###..
####.
####.
###..
##...
#....
"""),
    "caret_right": (4, 7, """
#...
##..
###.
####
###.
##..
#...
"""),
    "caret_down": (7, 4, """
#######
.#####.
..###..
...#...
"""),
    # A phase: a header bar filling the width, with two indented members under it. That is
    # exactly how a group draws in the sidebar - the header fills the panel as its own
    # background, members are inset - so the icon is a small picture of the thing it makes.
    # A plus was tried and dropped: at 12px it merged into the member rows, and "new" is what
    # the tooltip is for.
    "new_group": (12, 12, """
............
............
############
############
............
...#########
...#########
............
...#########
...#########
............
............
"""),
    # Three sliders, for the sidebar's settings corner. A gear was drawn first and rejected: at
    # 12px its teeth and bore collapse into something that reads as a face, not a cog.
    "settings": (12, 12, """
............
...##.......
############
...##.......
............
........##..
############
........##..
............
.....##.....
############
.....##.....
"""),
}

# Where each sprite lands on the sheet. Kept explicit rather than a uniform grid, because the
# controls these fill are not a uniform size: a close badge is 9px and a footer button is 16.
PLACEMENT = {
    "craft_all": (0, 0),
    "new_tab": (12, 0),
    "settings": (24, 0),
    "craft_one": (36, 0),
    "close": (48, 0),
    "park": (56, 0),
    "new_group": (0, 44),
    "arrow_left": (0, 16),
    "arrow_right": (6, 16),
    "caret_right": (12, 16),
    "caret_down": (17, 16),
}
SHEET_W, SHEET_H = 64, 64


def rows(art, w, h):
    lines = [ln for ln in art.strip("\n").split("\n")]
    assert len(lines) == h, f"expected {h} rows, got {len(lines)}"
    for ln in lines:
        assert len(ln) == w, f"expected {w} columns, got {len(ln)} in {ln!r}"
    return lines


def svg(name, w, h, art):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
             f'viewBox="0 0 {w} {h}" shape-rendering="crispEdges">']
    for y, line in enumerate(rows(art, w, h)):
        for x, ch in enumerate(line):
            if ch == "#":
                parts.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="#ffffff"/>')
            elif ch == "+":
                parts.append(f'<rect x="{x}" y="{y}" width="1" height="1" fill="#ffffff" '
                             f'fill-opacity="0.6"/>')
    parts.append("</svg>")
    return "\n".join(parts)


def main():
    from PIL import Image
    src = OUT / "icons"
    src.mkdir(exist_ok=True)
    sheet = Image.new("RGBA", (SHEET_W, SHEET_H), (0, 0, 0, 0))
    for name, (w, h, art) in SPRITES.items():
        svg_path = src / f"{name}.svg"
        svg_path.write_text(svg(name, w, h, art) + "\n")
        png_path = src / f"{name}.png"
        subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h),
                        "-o", str(png_path), str(svg_path)], check=True)
        sprite = Image.open(png_path).convert("RGBA")
        assert sprite.size == (w, h), f"{name}: rasterised to {sprite.size}, wanted {(w, h)}"
        x, y = PLACEMENT[name]
        assert x + w <= SHEET_W and y + h <= SHEET_H, f"{name} runs off the sheet"
        sheet.paste(sprite, (x, y))
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(SHEET)
    print(f"wrote {SHEET.relative_to(OUT.parent)} ({SHEET_W}x{SHEET_H}), "
          f"{len(SPRITES)} sprites from {src.relative_to(OUT.parent)}/*.svg")
    # A magnified contact sheet, for looking at the art without launching the game.
    preview = sheet.resize((SHEET_W * 8, SHEET_H * 8), Image.NEAREST)
    backing = Image.new("RGBA", preview.size, (30, 30, 36, 255))
    backing.alpha_composite(preview)
    backing.save(OUT / "preview.png")
    print(f"wrote art/preview.png ({preview.width}x{preview.height}, 8x)")


if __name__ == "__main__":
    main()
