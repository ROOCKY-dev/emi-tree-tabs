package dev.roocky.emitreetabs.ui;

import dev.roocky.emitreetabs.tab.TabGroup;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Typing a phase's name, in place on its own header.
 *
 * <p>In place rather than in a popup, because that is how the strip already renames a tab and a
 * phase name is the same kind of edit — short, and about the row you are looking at. A popup would
 * cover the rows whose grouping you are naming.
 *
 * <p>Opened on creation, so a new phase is named at the moment it exists rather than living as
 * "Phase 2" until someone remembers the gesture.
 */
public final class GroupName {

	private static EditBox box;
	private static int groupId = -1;

	private GroupName() {
	}

	/** True while this group's header is being edited, so the row draws the box instead of a name. */
	public static boolean editing(int id) {
		return box != null && groupId == id;
	}

	public static void open(Screen screen, int id, Rect row) {
		TabGroup group = TreeTabs.group(id);
		if (group == null || screen == null || row == null) {
			return;
		}
		close(screen);
		int inset = SidebarLayout.ROW_PAD + SidebarLayout.COLLAPSE + 4;
		int width = Math.max(40, row.width() - inset - SidebarLayout.MARKER - SidebarLayout.ROW_PAD * 2);
		box = new EditBox(Minecraft.getInstance().font, row.x() + inset,
				row.y() + (row.height() - 14) / 2, width, 14,
				Component.translatable("emi.tree_tabs.group.rename"));
		box.setMaxLength(32);
		box.setBordered(true);
		box.setValue(group.name);
		box.moveCursorToEnd();
		box.setHighlightPos(0);
		groupId = id;
		screen.setFocused(box);
		box.setFocused(true);
	}

	public static void commit(Screen screen) {
		if (box == null) {
			return;
		}
		TreeTabs.renameGroup(groupId, box.getValue());
		close(screen);
	}

	public static void close(Screen screen) {
		if (box == null) {
			return;
		}
		box = null;
		groupId = -1;
		if (screen != null && screen.getFocused() instanceof EditBox) {
			screen.setFocused(null);
		}
	}

	public static void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (box != null) {
			box.render(graphics, mouseX, mouseY, delta);
		}
	}

	/** @return true when the click was the box's, or was outside it and therefore committed. */
	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		if (box == null) {
			return false;
		}
		if (box.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		// Clicking away keeps what was typed. Losing a name to a stray click would be worse than
		// keeping one that was half-finished, which is one keystroke to fix.
		commit(screen);
		return false;
	}

	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		if (box == null) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			commit(screen);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			// Escape abandons the edit rather than closing the tree screen behind it.
			close(screen);
			return true;
		}
		return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput();
	}

	public static void reset() {
		box = null;
		groupId = -1;
	}
}
