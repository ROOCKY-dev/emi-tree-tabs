package dev.roocky.emitreetabs.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Routes the tree screen's events to whichever tab layout is in use.
 *
 * <p>There are two: the horizontal strip ({@link TabBar}) and the floating sidebar
 * ({@link TreeSidebar}). Exactly one is live at a time, decided by config and by whether the screen
 * can actually host a sidebar.
 *
 * <p>This exists so the mixin has one thing to call. Injecting into another mod's private screen is
 * the most fragile part of this mod, and the version of it that asked "which layout is it?" at
 * every one of seven injection points would be the version that quietly diverges.
 */
public final class TabUi {

	private TabUi() {
	}

	/** True when the sidebar, rather than the strip, is drawing the tabs. */
	public static boolean vertical(Screen screen) {
		return TreeSidebar.active(screen);
	}

	public static void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (vertical(screen)) {
			TreeSidebar.render(screen, graphics, mouseX, mouseY, delta);
		} else {
			TabBar.tickDrag(screen);
			TabBar.render(screen, graphics, mouseX, mouseY, delta);
		}
	}

	/** Whether the pointer is over the tabs, and so the tree beneath should ignore it. */
	public static boolean isOver(Screen screen, double mouseX, double mouseY) {
		return vertical(screen)
				? TreeSidebar.isOver(screen, mouseX, mouseY)
				: TabBar.isOver(screen, mouseX, mouseY);
	}

	public static boolean mouseClicked(Screen screen, double mouseX, double mouseY, int button) {
		return vertical(screen)
				? TreeSidebar.mouseClicked(screen, mouseX, mouseY, button)
				: TabBar.mouseClicked(screen, mouseX, mouseY, button);
	}

	/** Dragging reorders tabs on the strip. The sidebar does not support it yet. */
	public static boolean mouseDragged(Screen screen, double mouseX, double mouseY, int button) {
		return !vertical(screen) && TabBar.mouseDragged(screen, mouseX, mouseY, button);
	}

	public static boolean mouseScrolled(Screen screen, double mouseX, double mouseY, double amount) {
		return vertical(screen)
				? TreeSidebar.mouseScrolled(screen, mouseX, mouseY, amount)
				: TabBar.mouseScrolled(screen, mouseX, mouseY, amount);
	}

	/**
	 * Keyboard shortcuts stay with {@link TabBar} whichever layout is showing.
	 *
	 * <p>They are about tabs, not about where tabs are drawn — Ctrl+Tab should cycle trees the same
	 * way in both — and the rename box lives there too.
	 */
	public static boolean keyPressed(Screen screen, int keyCode, int scanCode, int modifiers) {
		return TabBar.keyPressed(screen, keyCode, scanCode, modifiers);
	}

	public static void ensureVisible(Screen screen, int index) {
		if (vertical(screen)) {
			TreeSidebar.ensureVisible(screen, index);
		} else {
			TabBar.ensureVisible(screen, index);
		}
	}

	/** Called whenever the tree screen is rebuilt, or the world goes away. */
	public static void reset() {
		TabBar.reset();
		TreeSidebar.reset();
	}
}
