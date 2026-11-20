package dev.roocky.emitreetabs.config;

import dev.roocky.emitreetabs.ui.EmiSkin;
import dev.roocky.emitreetabs.ui.Icons;
import dev.roocky.emitreetabs.ui.SidebarLayout;
import dev.roocky.emitreetabs.ui.SidebarScale;
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
	private final java.util.function.BooleanSupplier frame;

	/**
	 * @param orientation the <em>pending</em> orientation, not the saved one, so the preview
	 *                    answers "what would this do" rather than "what did this do"
	 * @param barAtBottom likewise
	 */
	public BarPreview(java.util.function.Supplier<String> orientation,
			java.util.function.BooleanSupplier barAtBottom, java.util.function.BooleanSupplier frame) {
		this.frame = frame;
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
	 * scale" - the preview contradicting its own label. The strip only ever needs 26 of this; the
	 * rest is where its caption goes.
	 */
	private static final int PREVIEW_HEIGHT = 120;

	@Override
	public int render(GuiGraphics graphics, int x, int y, int width, float delta) {
		Font font = Minecraft.getInstance().font;
		graphics.fill(x, y, x + width, y + PREVIEW_HEIGHT, 0xFF101018);

		if (vertical()) {
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
	private boolean vertical() {
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
		return SidebarScale.standard().fits(client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight());
	}

	private void drawStrip(GuiGraphics g, Font font, int x, int y, int width) {
		// Not negated. TreeSidebar passes !barAtBottom because its last argument is "on the left";
		// TabLayout's is "at the bottom", and copying the ! put the preview's bar on the wrong
		// edge - visible in game as a strip that ignored the toggle right next to it.
		TabLayout l = new TabLayout(width, PREVIEW_HEIGHT, SAMPLE_TABS, barAtBottom.getAsBoolean());
		int barY = y + l.barY;
		// The same pieces TabBar draws, in the same order, so a resource pack's look shows here too.
		g.fillGradient(x, barY, x + width, barY + TabLayout.HEIGHT, 0xC0101010, 0xD0101010);
		int shelfY = l.barAtBottom ? barY : barY + TabLayout.HEIGHT - TabLayout.SHELF;
		int contentY = y + l.contentY();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < SAMPLE_TABS; i++) {
				boolean active = i == 0;
				// Unselected tabs first, then the shelf, then the selected one over it.
				if (active != (pass == 1)) {
					continue;
				}
				int tx = x + l.tabX(i, 0);
				if (tx + l.tabWidth < x || tx > x + width) {
					continue;
				}
				int drop = active ? 0 : 2;
				int artHeight = TabLayout.HEIGHT - TabLayout.SHELF - drop + 3;
				int artY = l.barAtBottom ? barY + TabLayout.SHELF - 3 : barY + drop;
				EmiSkin.tab(g, tx, artY, l.tabWidth - 1, artHeight, active, l.barAtBottom);

				int lineY = l.barAtBottom ? contentY - 2 : contentY + 16;
				g.fill(tx + 2, lineY, tx + l.tabWidth - 3, lineY + 2, TabPalette.PROGRESS_PARTIAL);

				int iconX = l.density == TabLayout.Density.ICON
						? tx + (l.tabWidth - 1 - TabLayout.ICON_SIZE) / 2 : tx + 5;
				g.fill(iconX, contentY, iconX + TabLayout.ICON_SIZE, contentY + TabLayout.ICON_SIZE,
						ICON_TINTS[i % ICON_TINTS.length]);

				int budget = l.labelBudget(true);
				if (budget > 4) {
					g.drawString(font, trim(font, NAMES[i % NAMES.length], budget),
							tx + 5 + TabLayout.ICON_SIZE + 3, contentY + 4,
							active ? EmiSkin.TEXT : EmiSkin.TEXT_DIM, true);
				}
				TabLayout.Rect close = l.closeRect(i, 0, true, active);
				Icons.centred(g, Icons.CLOSE, x + close.x(), y + close.y(), close.width(),
						close.height(), EmiSkin.TEXT_DIM);
			}
			if (pass == 0) {
				g.enableScissor(x, shelfY, x + width, shelfY + TabLayout.SHELF);
				EmiSkin.panel(g, x - TabLayout.SHELF, l.barAtBottom ? shelfY - TabLayout.SHELF : shelfY,
						width + TabLayout.SHELF * 2, TabLayout.SHELF * 2);
				g.disableScissor();
			}
		}

		int buttonY = contentY - 1;
		EmiSkin.button(g, x + l.allButtonX(), buttonY, TabLayout.ALL_BUTTON_WIDTH,
				TabLayout.ICON_SIZE + 2, EmiSkin.State.NORMAL);
		Icons.centred(g, Icons.CRAFT_ALL, x + l.allButtonX(), buttonY,
				TabLayout.ALL_BUTTON_WIDTH, TabLayout.ICON_SIZE + 2, EmiSkin.TEXT_DIM);
		EmiSkin.button(g, x + l.addButtonX(), buttonY, TabLayout.ADD_BUTTON_WIDTH,
				TabLayout.ICON_SIZE + 2, EmiSkin.State.NORMAL);
		Icons.centred(g, Icons.NEW_TAB, x + l.addButtonX(), buttonY,
				TabLayout.ADD_BUTTON_WIDTH, TabLayout.ICON_SIZE + 2, EmiSkin.TEXT_DIM);

		caption(g, font, x, y, width, l.barAtBottom, Component.translatable(
				"emi.tree_tabs.config.preview.density." + l.density.name().toLowerCase(),
				l.tabWidth));
	}

	private void drawSidebar(GuiGraphics g, Font font, int x, int y, int width) {
		// The preview is much wider than it is tall, so it exaggerates the panel's width enough
		// to show a name. In game the panel takes 25% of the full window with smaller contents.
		int panelWidth = Math.max(SidebarLayout.MIN_PANEL_WIDTH, width / 3);
		// Same rule the real sidebar uses: barAtBottom doubles as "put it on the right".
		int panelX = barAtBottom.getAsBoolean() ? x + width - panelWidth : x;
		int panelY = y;
		int panelHeight = PREVIEW_HEIGHT - 18;

		if (frame.getAsBoolean()) {
			EmiSkin.sidebarPanel(g, panelX, panelY, panelWidth, panelHeight);
		}

		// The same geometry as the real sidebar: toolbar on top, then rows with their gaps.
		int inner = panelWidth - SidebarLayout.PAD * 2;
		int barX = panelX + SidebarLayout.PAD;
		int barY = panelY + SidebarLayout.PAD;
		if (frame.getAsBoolean()) {
			EmiSkin.inset(g, barX, barY, inner, SidebarLayout.TOOLBAR);
		}
		int buttonY = barY + SidebarLayout.TOOLBAR_PAD;
		EmiSkin.button(g, barX + SidebarLayout.TOOLBAR_PAD, buttonY, SidebarLayout.BUTTON,
				SidebarLayout.BUTTON, EmiSkin.State.NORMAL);
		EmiSkin.button(g, barX + inner - SidebarLayout.TOOLBAR_PAD - SidebarLayout.BUTTON, buttonY,
				SidebarLayout.BUTTON, SidebarLayout.BUTTON, EmiSkin.State.NORMAL);

		int rowY = barY + SidebarLayout.TOOLBAR + SidebarLayout.TOOLBAR_GAP;
		int rowX = barX;
		int rowWidth = inner;
		int rowBottom = panelY + panelHeight - SidebarLayout.PAD;
		for (int i = 0; i < 3 && rowY + SidebarLayout.ROW_HEIGHT <= rowBottom; i++) {
			if (i == 0) {
				EmiSkin.panel(g, rowX, rowY, rowWidth, SidebarLayout.ROW_HEIGHT);
			} else {
				EmiSkin.slot(g, rowX, rowY, rowWidth, SidebarLayout.ROW_HEIGHT);
			}
			int iconX = rowX + SidebarLayout.ROW_PAD + 2;
			int iconY = rowY + (SidebarLayout.ROW_HEIGHT - SidebarLayout.ICON) / 2;
			g.fill(iconX, iconY, iconX + SidebarLayout.ICON, iconY + SidebarLayout.ICON,
					ICON_TINTS[i % ICON_TINTS.length]);
			int mx = rowX + rowWidth - SidebarLayout.MARKER - SidebarLayout.ROW_PAD;
			int my = rowY + (SidebarLayout.ROW_HEIGHT - SidebarLayout.MARKER) / 2;
			int budget = mx - (iconX + SidebarLayout.ICON + 5) - 4;
			if (budget > 6) {
				g.drawString(font, trim(font, NAMES[i % NAMES.length], budget),
						iconX + SidebarLayout.ICON + 5, rowY + (SidebarLayout.ROW_HEIGHT - 8) / 2,
						i == 0 ? EmiSkin.TEXT : EmiSkin.TEXT_DIM, true);
			}
			EmiSkin.slot(g, mx, my, SidebarLayout.MARKER, SidebarLayout.MARKER);
			g.drawString(font, "1", mx + 6, my + 4, EmiSkin.TEXT, true);
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
