package dev.roocky.emitreetabs.ui;

/**
 * Where every part of the tab strip is, and nothing else.
 *
 * <p>This class deliberately imports nothing from Minecraft or EMI. Every tab bar defect so far has
 * been an arithmetic one — a drop position measured from the wrong origin, a right edge that was
 * inclusive when it should have been exclusive — and arithmetic is the one part that can be tested
 * without launching the game. Keeping it here is what makes those tests possible.
 *
 * <p>An instance is a snapshot: build one per frame or per input event from the current screen size
 * and tab count, read from it, and throw it away. Scroll is passed in rather than held, because the
 * caller owns it and it changes between reads.
 *
 * <h2>The sizing rule</h2>
 *
 * The old strip could only shrink. It divided the available width by the tab count and accepted
 * whatever came out, so past a certain count tabs fell below the width that shows a label, then
 * below the width that shows a close button, and finally stopped being controls at all.
 *
 * <p>Browsers solved this a long time ago: shrink to a floor, then <em>stop</em> and scroll. Nothing
 * is ever narrower than the size at which it can still be clicked. That is the rule here.
 *
 * <pre>
 *   ideal = available / count
 *   ideal &lt; MIN_TAB_WIDTH   -&gt; width = MIN_TAB_WIDTH, and the strip scrolls
 *   otherwise               -&gt; width = min(ideal, MAX_TAB_WIDTH), no scrolling
 * </pre>
 *
 * {@link Density} then follows from the width alone, so a tab never advertises an affordance it has
 * no room to draw.
 */
public final class TabLayout {

	/** How much of a tab there is room to draw. Derived from width; never chosen independently. */
	public enum Density {
		/** Icon, full label, close button. */
		COMFORTABLE,
		/** Icon, truncated label, close button. */
		COMPACT,
		/** Icon only. The close button becomes a corner badge on hover; middle click still works. */
		ICON
	}

	public static final int HEIGHT = 22;
	public static final int PAD = 2;
	public static final int ARROW_WIDTH = 9;
	public static final int ADD_BUTTON_WIDTH = 18;
	public static final int ALL_BUTTON_WIDTH = 18;
	public static final int ICON_SIZE = 16;

	/**
	 * The floor. A tab is never narrower than this, because at this width it is still a target you
	 * can hit; below it the strip scrolls instead. 28 = 4px padding + a 16px icon + 8px for the
	 * hover close badge.
	 */
	public static final int MIN_TAB_WIDTH = 28;
	/** Tabs stop growing here, the way browser tabs do, rather than stretching across the bar. */
	public static final int MAX_TAB_WIDTH = 124;
	/** At or above this, a label has room to be worth reading. */
	public static final int COMFORTABLE_WIDTH = 96;
	/** At or above this, a truncated label plus a close button still fit. */
	public static final int COMPACT_WIDTH = 54;

	/** Width of the corner close badge shown on hover at {@link Density#ICON}. */
	public static final int CLOSE_BADGE = 9;
	/** Width of the inline close button at the other two densities. */
	public static final int CLOSE_INLINE = 10;

	public final int screenWidth;
	public final int screenHeight;
	public final int count;
	public final boolean barAtBottom;

	public final int barY;
	public final int tabWidth;
	public final Density density;
	/** True when tabs hit the floor and the strip has to scroll to show them all. */
	public final boolean scrolling;
	public final int stripLeft;
	public final int stripWidth;
	public final double maxScroll;

	public TabLayout(int screenWidth, int screenHeight, int count, boolean barAtBottom) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.count = Math.max(0, count);
		this.barAtBottom = barAtBottom;
		this.barY = barAtBottom ? screenHeight - HEIGHT : 0;

		// Room for tabs with no scroll arrows. The two buttons on the right are always present.
		int available = Math.max(MIN_TAB_WIDTH, screenWidth - PAD * 2 - ADD_BUTTON_WIDTH - ALL_BUTTON_WIDTH);
		int ideal = this.count == 0 ? MAX_TAB_WIDTH : available / this.count;

		if (ideal < MIN_TAB_WIDTH) {
			// At the floor, so the strip scrolls and gives up room to the arrows. Width is pinned,
			// which is what stops this from feeding back into the decision above.
			this.scrolling = true;
			this.tabWidth = MIN_TAB_WIDTH;
			this.stripLeft = PAD + ARROW_WIDTH;
			this.stripWidth = Math.max(MIN_TAB_WIDTH, available - ARROW_WIDTH * 2);
		} else {
			this.scrolling = false;
			this.tabWidth = Math.min(ideal, MAX_TAB_WIDTH);
			this.stripLeft = PAD;
			this.stripWidth = available;
		}

		this.density = this.tabWidth >= COMFORTABLE_WIDTH ? Density.COMFORTABLE
				: this.tabWidth >= COMPACT_WIDTH ? Density.COMPACT
				: Density.ICON;
		this.maxScroll = Math.max(0, this.count * (double) this.tabWidth - this.stripWidth);
	}

	// ------------------------------------------------------------- positions

	/** Left edge of a tab, in screen coordinates. May fall outside the strip when scrolled. */
	public int tabX(int index, double scroll) {
		return (int) (stripLeft + index * (long) tabWidth - scroll);
	}

	public int allButtonX() {
		return screenWidth - PAD - ADD_BUTTON_WIDTH - ALL_BUTTON_WIDTH;
	}

	public int addButtonX() {
		return screenWidth - PAD - ADD_BUTTON_WIDTH;
	}

	public int leftArrowX() {
		return PAD;
	}

	public int rightArrowX() {
		return stripLeft + stripWidth;
	}

	// ------------------------------------------------------------ hit tests

	/** True when the pointer is anywhere over the strip, including its background. */
	public boolean isOver(double mouseX, double mouseY) {
		return mouseY >= barY && mouseY < barY + HEIGHT && mouseX >= 0 && mouseX < screenWidth;
	}

	/**
	 * The tab under the pointer, or -1.
	 *
	 * <p>The right edge is exclusive on purpose. Treating it as inclusive let a click one pixel
	 * past the strip select a tab that had been clipped out of view.
	 */
	public int tabAt(double mouseX, double mouseY, double scroll) {
		if (!isOver(mouseX, mouseY) || mouseX < stripLeft || mouseX >= stripLeft + stripWidth) {
			return -1;
		}
		int index = (int) Math.floor((mouseX - stripLeft + scroll) / tabWidth);
		return index >= 0 && index < count ? index : -1;
	}

	/**
	 * Where a tab dragged to {@code dragX} would be dropped.
	 *
	 * <p>Measured from {@link #stripLeft}, not {@link #PAD}. Those differ by exactly the width of
	 * the scroll arrows, which appear precisely when there are enough tabs to want to reorder them,
	 * so getting this wrong is invisible until the moment it matters.
	 */
	public int dropTarget(double dragX, double scroll) {
		if (count == 0) {
			return 0;
		}
		int target = (int) Math.floor((dragX - stripLeft + scroll) / tabWidth);
		return Math.max(0, Math.min(target, count - 1));
	}

	/** True when the close affordance for this tab is drawn at all. */
	public boolean closeVisible(boolean hovered, boolean active) {
		return density == Density.ICON ? hovered : (hovered || active);
	}

	/**
	 * The close target for a tab, or null when there is none.
	 *
	 * <p>At {@link Density#ICON} this is a small badge in the tab's top corner rather than an inline
	 * button, so the rest of the tab still selects. That is what keeps the close affordance from
	 * vanishing at small sizes, which is the failure the sizing rule exists to prevent.
	 */
	public Rect closeRect(int index, double scroll, boolean hovered, boolean active) {
		if (!closeVisible(hovered, active)) {
			return null;
		}
		int x = tabX(index, scroll);
		if (density == Density.ICON) {
			return new Rect(x + tabWidth - CLOSE_BADGE - 1, barY + 2, CLOSE_BADGE, CLOSE_BADGE);
		}
		return new Rect(x + tabWidth - CLOSE_INLINE - 3, barY + 5, CLOSE_INLINE, 11);
	}

	public boolean overClose(int index, double mouseX, double mouseY, double scroll,
			boolean hovered, boolean active) {
		Rect r = closeRect(index, scroll, hovered, active);
		return r != null && r.contains(mouseX, mouseY);
	}

	public boolean overAllButton(double mouseX, double mouseY) {
		if (!isOver(mouseX, mouseY) || count == 0) {
			return false;
		}
		int x = allButtonX();
		return mouseX >= x && mouseX < x + ALL_BUTTON_WIDTH;
	}

	public boolean overAddButton(double mouseX, double mouseY) {
		if (!isOver(mouseX, mouseY)) {
			return false;
		}
		return mouseX >= addButtonX() && mouseX < addButtonX() + ADD_BUTTON_WIDTH;
	}

	public boolean overLeftArrow(double mouseX, double mouseY) {
		return scrolling && isOver(mouseX, mouseY)
				&& mouseX >= leftArrowX() && mouseX < leftArrowX() + ARROW_WIDTH;
	}

	public boolean overRightArrow(double mouseX, double mouseY) {
		return scrolling && isOver(mouseX, mouseY)
				&& mouseX >= rightArrowX() && mouseX < rightArrowX() + ARROW_WIDTH;
	}

	/** How wide a label may be drawn, or 0 when this density draws none. */
	public int labelBudget(boolean closeShown) {
		if (density == Density.ICON) {
			return 0;
		}
		int textX = 4 + ICON_SIZE + 3;
		int budget = tabWidth - textX - (closeShown ? CLOSE_INLINE + 4 : 4);
		return Math.max(0, budget);
	}

	// --------------------------------------------------------------- scroll

	public double clampScroll(double scroll) {
		return Math.max(0, Math.min(scroll, maxScroll));
	}

	/** Scroll just far enough that the given tab sits fully inside the strip. */
	public double ensureVisible(int index, double scroll) {
		if (index < 0 || index >= count) {
			return clampScroll(scroll);
		}
		double left = index * (double) tabWidth;
		double right = left + tabWidth;
		if (left < scroll) {
			scroll = left;
		} else if (right > scroll + stripWidth) {
			scroll = right - stripWidth;
		}
		return clampScroll(scroll);
	}

	/** First and last tab index that can be on screen, for costing and drawing. */
	public int firstVisible(double scroll) {
		return Math.max(0, (int) Math.floor(scroll / tabWidth));
	}

	public int lastVisible(double scroll) {
		return Math.min(Math.max(0, count - 1), (int) Math.ceil((scroll + stripWidth) / tabWidth));
	}

	/** A rectangle in screen coordinates. Exists so hit-testing and drawing cannot disagree. */
	public record Rect(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}
	}
}
