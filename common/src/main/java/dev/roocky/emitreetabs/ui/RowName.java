package dev.roocky.emitreetabs.ui;

import dev.roocky.emitreetabs.tab.TabGroup;
import dev.roocky.emitreetabs.tab.TreeTab;
import dev.roocky.emitreetabs.tab.TreeTabs;
import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Typing a row's name, in place on the row itself — a phase header or a tab.
 *
 * <p>In place rather than in a popup, because that is how the strip already renames a tab and a
 * phase name is the same kind of edit — short, and about the row you are looking at. A popup would
 * cover the rows whose grouping you are naming.
 *
 * <p>Opened on creation, so a new phase is named at the moment it exists rather than living as
 * "Phase 2" until someone remembers the gesture.
 *
 * <p>It handles tabs as well as phases because {@code TabBar}'s rename box cannot: that one is
 * drawn by {@code TabBar.render}, which never runs while the sidebar is the layout, so F2 in the
 * sidebar used to open a text box positioned with strip geometry and never painted. You typed
 * blind into a control you could not see.
 */
public final class RowName {

	private static EditBox box;
	private static int groupId = -1;
	private static int tabIndex = -1;

	private RowName() {
	}

	/** True while this phase's header is being edited, so the row draws the box instead of a name. */
	public static boolean editingGroup(int id) {
		return box != null && groupId == id;
	}

	/** The same for a tab row. */
	public static boolean editingTab(int index) {
		return box != null && tabIndex == index;
	}

	public static void openGroup(Screen screen, int id, Rect row) {
		TabGroup group = TreeTabs.group(id);
		if (group == null || screen == null || row == null) {
			return;
		}
		close(screen);
		groupId = id;
		open(screen, row, SidebarLayout.ROW_PAD + SidebarLayout.COLLAPSE + 4, group.name);
	}

	/** Renames a tree. Its own name, not the goal's — clearing it falls back to the goal. */
	public static void openTab(Screen screen, int index, Rect row) {
		TreeTab tab = TreeTabs.tab(index);
		if (tab == null || screen == null || row == null) {
			return;
		}
		close(screen);
		tabIndex = index;
		open(screen, row, SidebarLayout.ROW_PAD + SidebarLayout.ICON + 5,
				tab.customName != null ? tab.customName : tab.goalName().getString());
	}

	private static void open(Screen screen, Rect row, int inset, String value) {
		int width = Math.max(40, row.width() - inset - SidebarLayout.MARKER - SidebarLayout.ROW_PAD * 2);
		box = new EditBox(Minecraft.getInstance().font, row.x() + inset,
				row.y() + (row.height() - 14) / 2, width, 14,
				Component.translatable("emi.tree_tabs.rename"));
		box.setMaxLength(32);
		box.setBordered(true);
		box.setValue(value);
		box.moveCursorToEnd();
		box.setHighlightPos(0);
		screen.setFocused(box);
		box.setFocused(true);
	}

	public static void commit(Screen screen) {
		if (box == null) {
			return;
		}
		if (groupId >= 0) {
			TreeTabs.renameGroup(groupId, box.getValue());
		} else if (tabIndex >= 0) {
			TreeTabs.rename(tabIndex, box.getValue());
		}
		close(screen);
	}

	public static void close(Screen screen) {
		if (box == null) {
			return;
		}
		box = null;
		groupId = -1;
		tabIndex = -1;
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
		tabIndex = -1;
	}
}
