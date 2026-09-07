package dev.roocky.emitreetabs.ui;

import org.lwjgl.glfw.GLFW;

import dev.roocky.emitreetabs.tab.Formula;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A small box for typing how many of something you want, in the arithmetic that produced it.
 *
 * <p>Accepts an expression as readily as a number — {@code 32 * 4 * 2} rather than 256 — and shows
 * what it evaluates to <em>while you type</em>. That preview is the point: an expression you cannot
 * check before it applies is worse than a calculator, because a typo becomes a wrong batch count
 * that looks deliberate.
 *
 * <p>{@link Formula} does the evaluation and is tested on its own. This is the box around it.
 *
 * <p>There is deliberately no {@code charTyped} handling here, and no mixin for it. {@code BoMScreen}
 * does not declare {@code charTyped}, so injecting into it would fail at load and take the game down;
 * and none is needed, because {@code Screen} forwards typing to whatever {@code setFocused} was given
 * — which is this box. The existing rename field has always worked the same way.
 */
public final class BatchInput {

	private static final int WIDTH = 116;
	private static final int HEIGHT = 40;

	private static final int COLOR_BG = 0xF0121218;
	private static final int COLOR_BORDER = 0xFF5A8CFF;
	private static final int COLOR_TEXT = 0xFFE6E6E6;
	private static final int COLOR_DIM = 0xFF9A9AA2;
	private static final int COLOR_GOOD = 0xFF5BD16A;
	private static final int COLOR_BAD = 0xFFD05050;

	private static EditBox box;
	private static int tabIndex = -1;
	private static int x;
	private static int y;

	private BatchInput() {
	}

	public static boolean isOpen() {
		return box != null;
	}

	/**
	 * Opens the box for a tab, anchored near the control that was clicked.
	 *
	 * <p>Clamped to the screen, because the control that opened it may be at the very edge — the
	 * sidebar's markers are, by design.
	 */
	public static void open(Screen screen, int index, int anchorX, int anchorY) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null) {
			return;
		}
		close(screen);
		tabIndex = index;
		x = Math.max(2, Math.min(anchorX, screen.width - WIDTH - 2));
		y = Math.max(2, Math.min(anchorY, screen.height - HEIGHT - 2));

		box = new EditBox(Minecraft.getInstance().font, x + 4, y + 15, WIDTH - 8, 14,
				Component.translatable("emi.tree_tabs.batch.title"));
		box.setMaxLength(32);
		box.setBordered(true);
		box.setValue(String.valueOf(tab.batches()));
		box.moveCursorToEnd();
		box.setHighlightPos(0);
		screen.setFocused(box);
		box.setFocused(true);
	}

	public static void close(Screen screen) {
		if (box == null) {
			return;
		}
		if (screen != null && screen.getFocused() == box) {
			screen.setFocused(null);
		}
		box = null;
		tabIndex = -1;
	}

	/** Applies the typed value, if it is valid. @return whether it applied. */
	public static boolean commit(Screen screen) {
		if (box == null) {
			return false;
		}
		Formula.Result r = Formula.evaluate(box.getValue());
		if (!r.ok()) {
			// Refuse rather than close: the reason is on screen and the text is still editable.
			return false;
		}
		TreeTabs.setBatches(tabIndex, r.value());
		close(screen);
		return true;
	}

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (box == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		graphics.fill(x, y, x + WIDTH, y + HEIGHT, COLOR_BG);
		graphics.fill(x, y, x + WIDTH, y + 1, COLOR_BORDER);
		graphics.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, COLOR_BORDER);
		graphics.fill(x, y, x + 1, y + HEIGHT, COLOR_BORDER);
		graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, COLOR_BORDER);

		graphics.drawString(font, Component.translatable("emi.tree_tabs.batch.title"),
				x + 4, y + 4, COLOR_DIM, false);
		box.render(graphics, mouseX, mouseY, delta);

		// The preview. Only shown for an actual expression: echoing "256 = 256" is noise.
		String text = box.getValue();
		if (!Formula.isPlainNumber(text)) {
			Formula.Result r = Formula.evaluate(text);
			String line = r.ok() ? "= " + r.value() : r.error();
			graphics.drawString(font, line, x + 4, y + HEIGHT - 10,
					r.ok() ? COLOR_GOOD : COLOR_BAD, false);
		} else {
			graphics.drawString(font, Component.translatable("emi.tree_tabs.batch.hint"),
					x + 4, y + HEIGHT - 10, COLOR_DIM, false);
		}
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (box == null) {
			return false;
		}
		if (box.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// A click anywhere else is a dismissal, not a commit — nothing should apply by accident.
		close(screen);
		return true;
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (box == null) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close(screen);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			commit(screen);
			return true;
		}
		box.keyPressed(keyCode, scanCode, modifiers);
		return true;
	}

	/** Whether the pointer is over the box, so the tree beneath ignores it. */
	public static boolean isOver(double mouseX, double mouseY) {
		return box != null
				&& mouseX >= x && mouseX < x + WIDTH
				&& mouseY >= y && mouseY < y + HEIGHT;
	}
}
