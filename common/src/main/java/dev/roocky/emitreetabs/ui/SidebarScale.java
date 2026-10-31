package dev.roocky.emitreetabs.ui;

import dev.roocky.emitreetabs.ui.SidebarLayout.Rect;

/**
 * Converts between sidebar layout units and screen coordinates.
 *
 * <p>Sidebar contents draw at 65% of the normal GUI size. Its width is controlled separately by
 * SidebarLayout.WIDTH_FRACTION, so smaller controls create room for names without shrinking the
 * attached panel. Rendering and hit testing share this conversion.
 */
public final class SidebarScale {

	/** Text, icons, buttons and spacing are 35% smaller than the normal GUI. */
	public static final double CONTENT_SCALE = 0.65;

	public final double factor;

	private SidebarScale(double factor) {
		this.factor = factor;
	}

	public static SidebarScale standard() {
		return new SidebarScale(CONTENT_SCALE);
	}

	/** A fixed factor for coordinate conversion tests. */
	public static SidebarScale of(double factor) {
		return new SidebarScale(factor);
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

}
