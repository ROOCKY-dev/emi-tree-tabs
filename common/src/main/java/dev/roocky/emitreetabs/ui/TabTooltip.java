package dev.roocky.emitreetabs.ui;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Places a tab's tooltip somewhere it does not cover the thing it describes.
 *
 * <p>Minecraft's default positioner puts a tooltip next to the cursor and clamps it to the screen.
 * That is fine in the middle of a screen and wrong at its edges, which is exactly where tabs live:
 * with the strip at the top the tooltip is forced down over the strip itself, and near the right
 * edge it flips across the cursor. Either way it hides the tab you are pointing at.
 *
 * <p>So the tooltip is anchored to the <em>control</em> rather than the pointer: pushed clear of the
 * whole strip or panel, aligned to the tab's own centre, and clamped last.
 */
public final class TabTooltip {

	/** Space left between the tooltip and the control it belongs to. */
	private static final int GAP = 4;
	/** Minecraft's own tooltip inset; positions are for the text, not the frame. */
	private static final int FRAME = 3;

	private TabTooltip() {
	}

	/**
	 * Draws a tooltip that avoids a rectangle.
	 *
	 * @param avoid the control being described — the tooltip never overlaps it
	 * @param anchorX where the tooltip would like to be centred, usually the tab's centre
	 */
	public static void render(GuiGraphics graphics, Font font, List<Component> lines,
			int screenWidth, int screenHeight, Rect avoid, int anchorX, int mouseX, int mouseY) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		List<FormattedCharSequence> text = new ArrayList<>(lines.size());
		for (Component line : lines) {
			text.add(line.getVisualOrderText());
		}
		graphics.renderTooltip(font, text,
				new Positioner(screenWidth, screenHeight, avoid, anchorX), mouseX, mouseY);
	}

	/** A rectangle to keep clear of. Kept local so this class needs no layout type. */
	public record Rect(int x, int y, int width, int height) {
	}

	private record Positioner(int screenWidth, int screenHeight, Rect avoid, int anchorX)
			implements ClientTooltipPositioner {

		@Override
		public Vector2ic positionTooltip(int screenW, int screenH, int mouseX, int mouseY,
				int width, int height) {
			// Vertical: whichever side of the control has room, preferring below.
			int below = avoid.y() + avoid.height() + GAP;
			int above = avoid.y() - height - GAP;
			int y;
			if (below + height + FRAME <= screenHeight) {
				y = below;
			} else if (above >= FRAME) {
				y = above;
			} else {
				// Neither side fits, which means the control is nearly as tall as the screen. Sit
				// beside it rather than on it, and let the horizontal clamp below sort the rest.
				y = Math.max(FRAME, Math.min(mouseY, screenHeight - height - FRAME));
			}

			// Horizontal: centred on the tab, then clamped. Centring on the control rather than the
			// cursor is what stops the tooltip sliding about while you move along one tab.
			int x = anchorX - width / 2;
			x = Math.max(FRAME, Math.min(x, screenWidth - width - FRAME));
			return new Vector2i(x, y);
		}
	}
}
