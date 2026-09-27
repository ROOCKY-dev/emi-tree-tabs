package dev.roocky.emitreetabs.config;

import dev.roocky.emitreetabs.ui.Icons;
import dev.roocky.emitreetabs.ui.SidebarLayout;
import dev.roocky.emitreetabs.ui.TabLayout;
import dev.roocky.emitreetabs.ui.TabPalette;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A working tab bar drawn inside the settings screen, so a layout option can be seen rather than
 * imagined.
 *
 * <p>"Fold shared materials by default" is a sentence nobody reads. A picture of it folded is
 * understood immediately — and a picture that moves when you change the setting is better still,
 * because the question is usually "what would this do", not "what does this mean".
 *
 * <p>It is the real arithmetic, not a drawing of one. {@link TabLayout} and {@link SidebarLayout}
 * decide the geometry here exactly as they do in game, and the colours come from
 * {@link TabPalette}, so the preview cannot drift from what it previews. What it does <em>not</em>
 * share is the drawing code, which needs live trees, a screen and EMI's item renderer; sample tabs
 * stand in for those, with a coloured block where an item icon would be.
 *
 * <p>Yacl asks for a render at a given width and is told the height used.
 */
public final class BarPreview implements ImageRenderer {

	private final java.util.function.Supplier<String> orientation;
	private final java.util.function.BooleanSupplier barAtBottom;

	/**
	 * @param orientation the <em>pending</em> orientation, not the saved one, so the preview
	 *                    answers "what would this do" rather than "what did this do"
	 * @param barAtBottom likewise
	 */
	public BarPreview(java.util.function.Supplier<String> orientation,
			java.util.function.BooleanSupplier barAtBottom) {
		this.orientation = orientation;
		this.barAtBottom = barAtBottom;
	}

	/** Enough to show the density ladder doing something without filling the description box. */
	private static final int SAMPLE_TABS = 5;
	private static final String[] NAMES = {
			"Blast Furnace", "Copper Pipe", "Mixer", "Fan", "Andesite Alloy",
	};
	/** Stand-ins for item icons, which cannot be rendered without EMI's stack renderer. */
	private static final int[] ICON_TINTS = {
			0xFF8A6A3A, 0xFFB5652E, 0xFF6E7A8A, 0xFF9AA0A6, 0xFF7E8B6B,
	};
	/**
	 * Tall enough for the sidebar to show three rows with names on them.
	 *
	 * <p>At 74 it fitted one nameless row, under a caption promising "room for names at any GUI
	 * scale" - the preview contradicting its own label. The strip only ever needs 22 of this; the
	 * rest is where its caption goes.
	 */
	private static final int PREVIEW_HEIGHT = 120;

	@Override
	public int render(GuiGraphics graphics, int x, int y, int width, float delta) {
		Font font = Minecraft.getInstance().font;
		graphics.fill(x, y, x + width, y + PREVIEW_HEIGHT, 0xFF101018);

		if (vertical(width)) {
			drawSidebar(graphics, font, x, y, width);
		} else {
			drawStrip(graphics, font, x, y, width);
		}
		return PREVIEW_HEIGHT;
	}

	/**
	 * Which layout to draw.
	 *
	 * <p>The box is a thumbnail, not a screen, so it is the wrong thing to ask whether a sidebar
	 * fits — it never does at 74px tall. Asking it meant picking "Floating sidebar" previewed a
	 * horizontal strip, which is the one answer the option must never give.
	 *
	 * <p>So an explicit choice is drawn as chosen, and only {@code auto} asks a question — of the
	 * real window, because that is the screen auto would actually be deciding for.
	 */
	private boolean vertical(int width) {
		String mode = orientation.get();
		if ("horizontal".equalsIgnoreCase(mode)) {
			return false;
		}
		if ("vertical".equalsIgnoreCase(mode)) {
			return true;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getWindow() == null) {
			return false;
		}
		return SidebarLayout.fits(client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight());
	}

	private void drawStrip(GuiGraphics g, Font font, int x, int y, int width) {
		// Not negated. TreeSidebar passes !barAtBottom because its last argument is "on the left";
		// TabLayout's is "at the bottom", and copying the ! put the preview's bar on the wrong
		// edge - visible in game as a strip that ignored the toggle right next to it.
		TabLayout l = new TabLayout(width, PREVIEW_HEIGHT, SAMPLE_TABS, barAtBottom.getAsBoolean());
		int barY = y + l.barY;
		g.fill(x, barY, x + width, barY + TabLayout.HEIGHT, TabPalette.BAR);
		int borderY = l.barAtBottom ? barY : barY + TabLayout.HEIGHT - 1;
		g.fill(x, borderY, x + width, borderY + 1, TabPalette.BORDER);

		for (int i = 0; i < SAMPLE_TABS; i++) {
			int tx = x + l.tabX(i, 0);
			if (tx + l.tabWidth < x || tx > x + width) {
				continue;
			}
			boolean active = i == 0;
			g.fill(tx, barY + 1, tx + l.tabWidth - 1, barY + TabLayout.HEIGHT - 1,
					active ? TabPalette.TAB_ACTIVE : TabPalette.TAB);
			int accentY = l.barAtBottom ? barY + TabLayout.HEIGHT - 3 : barY + 1;
			g.fill(tx, accentY, tx + l.tabWidth - 1, accentY + (active ? 2 : 1),
					active ? TabPalette.ACCENT : TabPalette.PROGRESS_PARTIAL);

			int iconX = l.density == TabLayout.Density.ICON
					? tx + (l.tabWidth - TabLayout.ICON_SIZE) / 2 : tx + 4;
			g.fill(iconX, barY + 3, iconX + TabLayout.ICON_SIZE, barY + 3 + TabLayout.ICON_SIZE,
					ICON_TINTS[i % ICON_TINTS.length]);

			int budget = l.labelBudget(true);
			if (budget > 4) {
				g.drawString(font, trim(font, NAMES[i % NAMES.length], budget),
						tx + 4 + TabLayout.ICON_SIZE + 3, barY + 7,
						active ? TabPalette.TEXT : TabPalette.TEXT_DIM, false);
			}
			TabLayout.Rect close = l.closeRect(i, 0, true, active);
			Icons.centred(g, Icons.CLOSE, x + close.x(), y + close.y(), close.width(),
					close.height(), TabPalette.TEXT_DIM);
		}

		Icons.centred(g, Icons.CRAFT_ALL, x + l.allButtonX(), barY + 1,
				TabLayout.ALL_BUTTON_WIDTH, TabLayout.HEIGHT - 2, TabPalette.TEXT_DIM);
		Icons.centred(g, Icons.NEW_TAB, x + l.addButtonX(), barY + 1,
				TabLayout.ADD_BUTTON_WIDTH, TabLayout.HEIGHT - 2, TabPalette.TEXT_DIM);

		caption(g, font, x, y, width, l.barAtBottom, Component.translatable(
				"emi.tree_tabs.config.preview.density." + l.density.name().toLowerCase(),
				l.tabWidth));
	}

	private void drawSidebar(GuiGraphics g, Font font, int x, int y, int width) {
		// A fifth in game, a third here. The box is far wider than it is tall compared with a
		// screen, so a literal fifth of it is a strip too narrow to draw a name in - which is the
		// one thing this layout exists to have room for.
		int panelWidth = Math.max(SidebarLayout.MIN_PANEL_WIDTH, width / 3);
		// Same rule the real sidebar uses: barAtBottom doubles as "put it on the right".
		int panelX = barAtBottom.getAsBoolean() ? x + width - panelWidth - 4 : x + 4;
		int panelY = y + 4;
		int panelHeight = PREVIEW_HEIGHT - 22;

		g.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, TabPalette.PANEL);
		g.fill(panelX, panelY, panelX + panelWidth, panelY + 1, TabPalette.PANEL_HI);
		g.fill(panelX, panelY, panelX + 1, panelY + panelHeight, TabPalette.PANEL_HI);
		g.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight,
				TabPalette.PANEL_LO);
		g.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight,
				TabPalette.PANEL_LO);

		int rowY = panelY + SidebarLayout.PAD;
		int rowX = panelX + SidebarLayout.PAD;
		int rowWidth = panelWidth - SidebarLayout.PAD * 2;
		for (int i = 0; i < 3 && rowY + SidebarLayout.ROW_HEIGHT < panelY + panelHeight; i++) {
			g.fill(rowX, rowY, rowX + rowWidth, rowY + SidebarLayout.ROW_HEIGHT,
					i == 0 ? TabPalette.TAB_ACTIVE : TabPalette.TAB);
			g.fill(rowX + 5, rowY + 5, rowX + 5 + SidebarLayout.ICON, rowY + 5 + SidebarLayout.ICON,
					ICON_TINTS[i % ICON_TINTS.length]);
			int budget = rowWidth - SidebarLayout.ICON - SidebarLayout.MARKER - 20;
			if (budget > 6) {
				g.drawString(font, trim(font, NAMES[i % NAMES.length], budget),
						rowX + SidebarLayout.ICON + 10, rowY + (SidebarLayout.ROW_HEIGHT - 8) / 2,
						i == 0 ? TabPalette.TEXT : TabPalette.TEXT_DIM, false);
			}
			int mx = rowX + rowWidth - SidebarLayout.MARKER - 5;
			int my = rowY + (SidebarLayout.ROW_HEIGHT - SidebarLayout.MARKER) / 2;
			g.fill(mx, my, mx + SidebarLayout.MARKER, my + SidebarLayout.MARKER,
					TabPalette.MARKER_BG);
			g.drawString(font, "1", mx + 6, my + 4, TabPalette.TEXT_DIM, false);
			rowY += SidebarLayout.ROW_HEIGHT + SidebarLayout.ROW_GAP;
		}

		caption(g, font, x, y, width, false,
				Component.translatable("emi.tree_tabs.config.preview.sidebar"));
	}

	/**
	 * The caption goes in whichever half the bar is not using.
	 *
	 * <p>It was pinned to the bottom, which put it straight through the strip whenever the strip
	 * was down there too - the label describing the bar, drawn on top of the bar.
	 */
	private void caption(GuiGraphics g, Font font, int x, int y, int width, boolean barAtBottom,
			Component text) {
		String s = text.getString();
		int ty = barAtBottom ? y + 6 : y + PREVIEW_HEIGHT - 11;
		g.drawString(font, s, x + (width - font.width(s)) / 2, ty, TabPalette.TEXT_DIM, false);
	}

	private static String trim(Font font, String text, int budget) {
		if (font.width(text) <= budget) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, budget - font.width(".."))) + "..";
	}

	@Override
	public void close() {
	}
}
