package dev.roocky.emitreetabs.ui;

import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;

/**
 * How much larger than the rest of the GUI the sidebar draws, and the conversion between the
 * sidebar's own units and the screen's.
 *
 * <p>At the GUI's own size the sidebar's rows, names and buttons were small for a panel a fifth of
 * the window wide. So the whole panel — text, item icons, buttons, gaps — is drawn under one
 * scale, and {@link SidebarLayout} is handed the screen measured in <em>sidebar units</em>. Layout,
 * hit tests and their tests are unchanged by this; only the edges convert.
 *
 * <p>Like the layout, this imports nothing from Minecraft, so it is tested without the game.
 */
public final class SidebarScale {

	/** The panel is never drawn smaller than this, relative to the rest of the GUI. */
	public static final double MIN = 1.30;
	/** Nor larger than this, however wide the window. */
	public static final double MAX = 1.45;

	/** Units to screen pixels: a factor of 1.4 draws a 10-unit row 14 pixels tall. */
	public final double factor;

	private SidebarScale(double factor) {
		this.factor = factor;
	}

	/**
	 * The scale for a screen of this width at this GUI scale.
	 *
	 * <p>As large as the range allows while the panel still has room for names — a bigger panel
	 * whose rows have lost their labels is a worse panel, not a better one. Then snapped, where the
	 * range allows, to a factor that lands on whole screen pixels, because the font and the icon
	 * sheet are pixel art and blur unevenly at in-between sizes.
	 *
	 * @param screenWidth the GUI-scaled width, which is what a Screen is handed
	 * @param guiScale    screen pixels per GUI pixel
	 */
	public static SidebarScale forScreen(int screenWidth, double guiScale) {
		double panelWidth = screenWidth * SidebarLayout.WIDTH_FRACTION;
		double roomForNames = clamp(panelWidth / SidebarLayout.NAME_WIDTH, MIN, MAX);
		return new SidebarScale(snapToWholePixels(roomForNames, guiScale));
	}

	/** A fixed factor, for tests and for anything that must not depend on the window. */
	public static SidebarScale of(double factor) {
		return new SidebarScale(factor);
	}

	/**
	 * The largest factor in range, no larger than {@code wanted}, that makes one unit a whole
	 * number of screen pixels. At GUI scale 3, 4/3 makes a unit exactly four pixels.
	 *
	 * <p>Never larger than wanted: going up would cost the panel its names, which is the thing
	 * {@code wanted} was chosen to protect. When no whole-pixel factor fits, {@code wanted} stands
	 * and the panel is a little soft rather than a little cramped.
	 */
	static double snapToWholePixels(double wanted, double guiScale) {
		if (guiScale <= 0) {
			return wanted;
		}
		int mostPixels = (int) Math.floor(wanted * guiScale + 1e-9);
		double snapped = mostPixels / guiScale;
		return snapped >= MIN - 1e-9 ? snapped : wanted;
	}

	// --------------------------------------------------------------- conversions

	/** A screen coordinate in sidebar units. */
	public double toUnits(double screen) {
		return screen / factor;
	}

	/** A screen length in whole sidebar units, rounded up so the attached panel covers the edge. */
	public int toUnits(int screen) {
		return (int) Math.ceil(screen / factor);
	}

	/** A sidebar coordinate on the screen. */
	public int toScreen(double units) {
		return (int) Math.round(units * factor);
	}

	/** A sidebar rectangle on the screen, for anything drawn outside the scaled panel. */
	public Rect toScreen(Rect units) {
		int x = toScreen(units.x());
		int y = toScreen(units.y());
		return new Rect(x, y, toScreen(units.x() + units.width()) - x,
				toScreen(units.y() + units.height()) - y);
	}

	/**
	 * Only Auto may choose the strip because of available space. An explicit sidebar selection
	 * must stay a sidebar: at large GUI scales the old fit guard silently ignored the saved choice.
	 */
	public boolean useSidebar(String orientation, int screenWidth, int screenHeight) {
		if ("vertical".equalsIgnoreCase(orientation)) {
			return true;
		}
		if ("horizontal".equalsIgnoreCase(orientation)) {
			return false;
		}
		return fits(screenWidth, screenHeight);
	}

	/** Whether a screen this size can host the sidebar once it is drawn at this scale. */
	public boolean fits(int screenWidth, int screenHeight) {
		return SidebarLayout.fits(toUnits(screenWidth), toUnits(screenHeight));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(value, max));
	}
}
