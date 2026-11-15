package dev.roocky.emitreetabs.ui;

/**
 * The status colours both tab layouts lay over EMI's surfaces.
 *
 * <p>Since 6.0 the surfaces themselves — panels, tabs, rows, buttons — are EMI's own textures,
 * drawn by {@link EmiSkin}, so a resource pack that restyles EMI restyles them. What is left here
 * is information rather than skin: progress, crafting, the hovered close, a parked tree. Those stay
 * fixed colours on purpose, so no pack can make "finished" look like "missing".
 *
 * <p>The settings screen's live preview reads from here too, so it keeps matching what it
 * previews.
 */
public final class TabPalette {

	public static final int TEXT = 0xFFE6E6E6;
	public static final int TEXT_DIM = 0xFFB4B4BE;
	/** Ink on a lit control, which is bright enough that light text would vanish on it. */
	public static final int INK_ON_LIT = 0xFF0C0C10;

	public static final int ACCENT = 0xFF5A8CFF;
	public static final int CRAFTING = 0xFF48C8E0;

	/** Laid over a parked tab: its tree has stopped asking for materials. */
	public static final int PARKED = 0x66000000;
	/** A close × under the cursor: on a strip tab, and on a sidebar group header. */
	public static final int CLOSE_HOVER = 0xFFD05050;

	/** Progress, which both layouts draw on a tab's edge. */
	public static final int PROGRESS_COMPLETE = 0xFF5BD16A;
	public static final int PROGRESS_PARTIAL = 0xFFE0A63C;
	public static final int PROGRESS_NONE = 0xFF6E6E76;

	private TabPalette() {
	}
}
