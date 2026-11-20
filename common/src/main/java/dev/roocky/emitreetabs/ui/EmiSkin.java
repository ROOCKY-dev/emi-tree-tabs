package dev.roocky.emitreetabs.ui;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Every surface the tab strip and the sidebar draw, taken from EMI's own GUI textures.
 *
 * <p>Up to 5.x both layouts were painted with flat fills from {@link TabPalette}. They looked
 * nothing like the EMI screens around them, and no resource pack could touch them: a pack that
 * reskins EMI replaces {@code emi:textures/gui/background.png}, {@code buttons.png} and {@code
 * widgets.png}, and a fill colour is not a texture. Drawing the same regions EMI draws, through
 * EMI's own {@link EmiRenderHelper#drawNinePatch}, means a pack that restyles EMI restyles this too.
 *
 * <p>The regions are not guesses. Each one is copied from the call EMI makes for the same job, read
 * out of the 1.1.24 bytecode with {@code javap -c} (identical on 1.20.1 and 1.21.1, textures
 * included):
 * <ul>
 * <li>{@code RecipeScreen.render} draws its panel as {@code background.png} (0,0), corner 4, centre
 * 1, and its category tabs as (9,0) when selected and (18,0) otherwise, same corners. The
 * unselected piece's bottom rows are transparent and the selected one's are panel fill, which is
 * how a selected tab merges into the panel under it.</li>
 * <li>Its category title sits on (0,16), corner 3, centre 6: a flat inset bar.</li>
 * <li>{@code EmiScreenManager$SidebarPanel} draws the sidebar frame as (0,32), corner 8, centre
 * 1.</li>
 * <li>{@code EmiTexture.SLOT} is {@code widgets.png} (0,0), 18 square.</li>
 * <li>{@code buttons.png} keeps every button three times down the sheet: normal at v, hovered at
 * v+12, disabled at v+24. (72,0) is the blank one; (0,0) and (12,0) are the page arrows.</li>
 * </ul>
 *
 * <p>Status that is information rather than skin — progress, crafting, a group's colour, the
 * active accent — stays a {@link TabPalette} colour laid over these surfaces. A resource pack
 * restyles what a tab looks like; it should not be able to make "finished" look like "missing".
 *
 * <p>EMI's internals, so check the regions against the jar on an EMI update.
 */
public final class EmiSkin {

	/** EMI draws its own text white with a shadow on these surfaces, so that is what reads on them. */
	public static final int TEXT = 0xFFFFFFFF;
	/** Light enough to read on a light EMI tab face once the shadow is under it. */
	public static final int TEXT_DIM = 0xFFD0D0D0;

	private static final int BUTTON_BLANK_U = 72;
	private static final int BUTTON_STATE_STEP = 12;
	public static final int ARROW_SIZE = 12;

	/** How a button is drawn: the three rows {@code buttons.png} has for every button. */
	public enum State {
		NORMAL, HOVERED, DISABLED;

		public static State of(boolean enabled, boolean hovered) {
			return !enabled ? DISABLED : hovered ? HOVERED : NORMAL;
		}

		int v(int base) {
			return base + ordinal() * BUTTON_STATE_STEP;
		}
	}

	private EmiSkin() {
	}

	/** EMI's recipe-screen panel: the raised, bevelled surface. Needs at least 8x8. */
	public static void panel(GuiGraphics g, int x, int y, int width, int height) {
		nine(g, EmiRenderHelper.BACKGROUND, x, y, width, height, 0, 0, 4, 1);
	}

	/** EMI's sidebar frame, the one it draws round its own item list. Needs at least 16x16. */
	public static void sidebarPanel(GuiGraphics g, int x, int y, int width, int height) {
		nine(g, EmiRenderHelper.BACKGROUND, x, y, width, height, 0, 32, 8, 1);
	}

	/** The flat bar EMI puts a recipe category's title on. */
	public static void inset(GuiGraphics g, int x, int y, int width, int height) {
		nine(g, EmiRenderHelper.BACKGROUND, x, y, width, height, 0, 16, 3, 6);
	}

	/** EMI's item slot, stretched: a sunken well. */
	public static void slot(GuiGraphics g, int x, int y, int width, int height) {
		nine(g, EmiRenderHelper.WIDGETS, x, y, width, height, 0, 0, 1, 16);
	}

	/**
	 * One of EMI's recipe-category tabs.
	 *
	 * <p>EMI's tabs only ever hang off the top of a panel, so the open edge is at the bottom. With
	 * {@code openUp} the piece is mirrored so it hangs off a panel above it instead — the strip at
	 * the bottom of the screen. Mirrored rather than redrawn, so a pack's tab art still applies.
	 */
	public static void tab(GuiGraphics g, int x, int y, int width, int height, boolean selected,
			boolean openUp) {
		int u = selected ? 9 : 18;
		if (!openUp) {
			nine(g, EmiRenderHelper.BACKGROUND, x, y, width, height, u, 0, 4, 1);
			return;
		}
		g.pose().pushPose();
		// y' = 2y + h - y: the rectangle maps onto itself, upside down.
		g.pose().translate(0f, 2f * y + height, 0f);
		g.pose().scale(1f, -1f, 1f);
		// Mirroring reverses the winding, and the GUI leaves face culling on.
		RenderSystem.disableCull();
		nine(g, EmiRenderHelper.BACKGROUND, x, y, width, height, u, 0, 4, 1);
		RenderSystem.enableCull();
		g.pose().popPose();
	}

	/** EMI's blank button, stretched to any size of at least 2x2. */
	public static void button(GuiGraphics g, int x, int y, int width, int height, State state) {
		nine(g, EmiRenderHelper.BUTTONS, x, y, width, height, BUTTON_BLANK_U, state.v(0), 1, 10);
	}

	/** EMI's own page arrow, the 12x12 one its sidebars page with. */
	public static void arrow(GuiGraphics g, int x, int y, boolean left, State state) {
		EmiDrawContext.wrap(g).drawTexture(EmiRenderHelper.BUTTONS, x, y, ARROW_SIZE, ARROW_SIZE,
				left ? 0 : ARROW_SIZE, state.v(0), ARROW_SIZE, ARROW_SIZE, 256, 256);
	}

	/** EMI's hover wash: what it lays over a slot under the cursor. */
	public static void highlight(GuiGraphics g, int x, int y, int width, int height) {
		EmiRenderHelper.drawSlotHightlight(EmiDrawContext.wrap(g), x, y, width, height, 0);
	}

	private static void nine(GuiGraphics g, net.minecraft.resources.ResourceLocation texture,
			int x, int y, int width, int height, int u, int v, int corner, int centre) {
		// drawNinePatch draws four corners at full size whatever the box, so a box smaller than two
		// corners would draw them overlapping and past its own edge.
		if (width < corner * 2 || height < corner * 2) {
			return;
		}
		EmiRenderHelper.drawNinePatch(EmiDrawContext.wrap(g), texture, x, y, width, height, u, v,
				corner, centre);
	}
}
