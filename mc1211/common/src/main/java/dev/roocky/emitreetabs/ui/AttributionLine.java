package dev.roocky.emitreetabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * One line of a material's breakdown, drawn as the thing itself rather than its name.
 *
 * <p>"20 for Blast Furnace" is a sentence you read; an icon and two numbers is something you take
 * in. Item names are also long and unpredictable — a modpack's are routinely forty characters —
 * which made the widest line decide the tooltip's width and pushed it off the screen edge.
 *
 * <p>The line says both numbers, which is what a name-only line could not: <em>how many of the
 * thing</em> and <em>how much of the material that takes</em>. Without the first, "2 for Redstone
 * Torch" does not tell you whether that is two redstone for one torch or for two.
 */
public final class AttributionLine implements ClientTooltipComponent {

	/** Item icons are 16px; drawn at this scale a line stays close to a text line's height. */
	private static final float SCALE = 0.75f;
	private static final int ICON = (int) (16 * SCALE);
	private static final int GAP = 4;

	private final EmiIngredient icon;
	private final Component count;
	private final Component amount;
	private final int indent;

	/**
	 * @param icon   what the material is going into — a tree's goal, or a sub-craft
	 * @param count  how many of it are needed, in full
	 * @param amount how much of the hovered material that takes
	 * @param indent nesting depth, so a sub-craft sits under the tree it belongs to
	 */
	public AttributionLine(EmiIngredient icon, long count, long amount, int indent) {
		this.icon = icon;
		this.count = Component.translatable("emi.tree_tabs.attribution.count", count);
		this.amount = Component.translatable("emi.tree_tabs.attribution.amount", amount);
		this.indent = indent;
	}

	@Override
	public int getHeight() {
		return ICON + 2;
	}

	@Override
	public int getWidth(Font font) {
		return indentPixels() + ICON + GAP + font.width(count) + GAP * 2 + font.width(amount);
	}

	private int indentPixels() {
		return 4 + indent * 8;
	}

	@Override
	public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
		int ix = x + indentPixels();
		PoseStack pose = graphics.pose();
		pose.pushPose();
		// Scaled about the icon's own corner, so the text beside it keeps its baseline.
		pose.translate(ix, y, 0);
		pose.scale(SCALE, SCALE, 1f);
		icon.render(graphics, 0, 0, 0f, EmiIngredient.RENDER_ICON);
		pose.popPose();
	}

	@Override
	public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix,
			net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer) {
		int tx = x + indentPixels() + ICON + GAP;
		// Centred against the icon rather than sat on the tooltip's text baseline, which would
		// leave every line looking like it had slipped.
		int ty = y + (ICON - 8) / 2;
		font.drawInBatch(count, tx, ty, 0xFFFFFF, false, matrix, buffer,
				Font.DisplayMode.NORMAL, 0, 0xF000F0);
		font.drawInBatch(amount, tx + font.width(count) + GAP * 2, ty, 0xFF9AA0D6, false, matrix,
				buffer, Font.DisplayMode.NORMAL, 0, 0xF000F0);
	}
}
