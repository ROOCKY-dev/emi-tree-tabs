package dev.roocky.emitreetabs.ui;

/**
 * The colours both tab layouts draw with.
 *
 * <p>They were duplicated: the strip and the sidebar were written months apart from the same
 * starting values, and when the bar's contrast turned out to be 1.09:1 the sidebar had the
 * identical bug from the identical constants. Two copies of a palette is two places to fix
 * everything, and the second one gets forgotten.
 *
 * <p>The settings screen's live preview reads from here too, which is the other reason: a preview
 * that keeps its own colours is a preview that quietly stops matching what it previews.
 *
 * <h2>The contrast rule</h2>
 *
 * The surface a tab sits on must be far enough from the tab to be findable without moving the
 * mouse, and the tab must stay dark enough for light text. Measured, not judged by eye: panel to
 * tab is 1.81:1, label text on a tab 8.87:1, the dimmed label 5.38:1 — both past WCAG AA. Change
 * one of these and check the other two.
 */
public final class TabPalette {

	/** The strip's background, and the sidebar's panel. Opaque: alpha let the world bleed through. */
	public static final int BAR = 0xFF08080A;
	public static final int PANEL = 0xF00B0B12;
	/** Vanilla's bevel: light on the top and left, dark on the bottom and right. */
	public static final int PANEL_HI = 0xFF40404E;
	public static final int PANEL_LO = 0xFF04040A;
	/** The bar's content-facing edge. Black was invisible against a dark tree; a rule is not. */
	public static final int BORDER = 0xFF4A4A56;

	public static final int TAB = 0xFF3B3B45;
	public static final int TAB_HOVER = 0xFF4E4E5C;
	public static final int TAB_ACTIVE = 0xFF5E5E78;
	public static final int GROUP = 0xFF2A2A38;
	public static final int GROUP_HOVER = 0xFF373748;

	public static final int TEXT = 0xFFE6E6E6;
	public static final int TEXT_DIM = 0xFFB4B4BE;
	/** Ink on a lit control, which is bright enough that light text would vanish on it. */
	public static final int INK_ON_LIT = 0xFF0C0C10;

	public static final int ACCENT = 0xFF5A8CFF;
	public static final int CRAFTING = 0xFF48C8E0;
	public static final int CRAFTING_EDGE = 0xFF7FE0F0;
	/** Outlines a small control drawn on a row, so it has to out-read the row, not the panel. */
	public static final int OUTLINE = 0xFF6A6A7C;
	public static final int MARKER_BG = 0xFF15151C;
	public static final int DIVIDER = 0x40FFFFFF;

	/** Laid over a parked tab: its tree has stopped asking for materials. */
	public static final int PARKED = 0x66000000;
	/** A scroll arrow that cannot move. Present, so the strip does not look like it simply ends. */
	public static final int DISABLED = 0xFF55555E;

	/** Progress, which both layouts draw on a tab's edge. */
	public static final int PROGRESS_COMPLETE = 0xFF5BD16A;
	public static final int PROGRESS_PARTIAL = 0xFFE0A63C;
	public static final int PROGRESS_NONE = 0xFF6E6E76;

	private TabPalette() {
	}
}
