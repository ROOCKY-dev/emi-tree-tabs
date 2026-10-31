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
	/**
	 * The widest a line may run before it wraps.
	 *
	 * <p>Without a limit one long description made the whole tooltip as wide as that sentence —
	 * wider than the screen at large GUI scales — and every short line beside it inherited the
	 * width. 200 keeps a sentence to a comfortable read, a little wider than vanilla's item lore.
	 */
	private static final int MAX_LINE_WIDTH = 200;

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
		List<FormattedCharSequence> text = wrap(font, lines, lineWidthFor(screenWidth));
		graphics.renderTooltip(font, text,
				new Positioner(screenWidth, screenHeight, avoid, anchorX), mouseX, mouseY);
	}

	/**
	 * Splits every line at the width limit, keeping its style on each piece. A short line stays one
	 * line; only the long ones wrap.
	 */
	private static List<FormattedCharSequence> wrap(Font font, List<Component> lines, int maxWidth) {
		List<FormattedCharSequence> out = new ArrayList<>(lines.size());
		for (Component line : lines) {
			out.addAll(font.split(line, maxWidth));
		}
		return out;
	}

	/** The line limit, narrowed on a screen too small to fit it with its frame. */
	private static int lineWidthFor(int screenWidth) {
		int roomOnScreen = screenWidth - (FRAME + GAP) * 2;
		return Math.max(40, Math.min(MAX_LINE_WIDTH, roomOnScreen));
	}

	/** A rectangle to keep clear of. Kept local so this class needs no layout type. */
	public record Rect(int x, int y, int width, int height) {
	}

	private record Positioner(int screenWidth, int screenHeight, Rect avoid, int anchorX)
			implements ClientTooltipPositioner {

		@Override
		public Vector2ic positionTooltip(int screenW, int screenH, int mouseX, int mouseY,
				int width, int height) {
			// Above or below the control, preferring below — the strip's case.
			int below = avoid.y() + avoid.height() + GAP;
			int above = avoid.y() - height - GAP;
			if (below + height + FRAME <= screenHeight) {
				return new Vector2i(centredOnAnchor(width), below);
			}
			if (above >= FRAME) {
				return new Vector2i(centredOnAnchor(width), above);
			}
			// Neither fits, which means the control is nearly as tall as the screen — the sidebar's
			// case. Sit beside it, level with the pointer.
			int y = clamp(mouseY, FRAME, screenHeight - height - FRAME);
			return new Vector2i(besideTheControl(width), y);
		}

		/**
		 * Centred on the control rather than the cursor, which is what stops the tooltip sliding
		 * about while you move along one tab.
		 */
		private int centredOnAnchor(int width) {
			return clamp(anchorX - width / 2, FRAME, screenWidth - width - FRAME);
		}

		/**
		 * Clear of the control on whichever side has room, preferring the side the anchor is on.
		 * Centring and clamping alone put a wide tooltip straight back over the panel it describes.
		 */
		private int besideTheControl(int width) {
			int right = avoid.x() + avoid.width() + GAP + FRAME;
			int left = avoid.x() - GAP - FRAME - width;
			boolean preferRight = anchorX >= avoid.x() + avoid.width() / 2;
			boolean rightFits = right + width + FRAME <= screenWidth;
			boolean leftFits = left >= FRAME;
			if (preferRight && rightFits) {
				return right;
			}
			if (leftFits) {
				return left;
			}
			if (rightFits) {
				return right;
			}
			// Too wide for either side: the wrapping above makes this a tiny-screen case only.
			return centredOnAnchor(width);
		}

		private static int clamp(int value, int min, int max) {
			return Math.max(min, Math.min(value, max));
		}
	}
}
