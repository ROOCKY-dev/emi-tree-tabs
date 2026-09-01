package dev.ahmed.emitreetabs.sidebar;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.ahmed.emitreetabs.TreeTabsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * A section title in the crafting sidebar, which also draws the rule above itself.
 *
 * <p>The divider used to be its own row of entries, which cost a full row of slots per section —
 * visible as soon as you hovered one and got an item highlight on empty space. Drawing it along the
 * top edge of the header's row instead makes it free, and makes it read as a border rather than as
 * a row of item boxes.
 *
 * <p>The header occupies one slot but paints across the whole row: the entries after it are blanks,
 * so there is nothing to overlap.
 */
public final class GroupHeader extends SidebarEntry {

	private static final int LINE_COLOR = 0x50FFFFFF;
	private static final int TEXT_COLOR = 0xFFD2D2DA;
	private static final int COUNT_COLOR = 0xFF7A7A86;
	/** Titles are drawn smaller than an item so a section header does not read as a slot. */
	private static final float TEXT_SCALE = 0.75f;

	/** Stable identity for the section, so a fold survives the list being rebuilt. */
	public final String id;
	private final Component label;
	private final int count;
	/** How wide to paint, in pixels: the full row this header sits on. */
	private final int span;
	private final boolean firstSection;

	public GroupHeader(String id, Component label, int count, int span, boolean firstSection) {
		this.id = id;
		this.label = label;
		this.count = count;
		this.span = span;
		this.firstSection = firstSection;
	}

	public boolean isCollapsed() {
		return TreeTabsConfig.collapsibleGroups && CraftingGroups.isCollapsed(id);
	}

	public void toggle() {
		CraftingGroups.toggleCollapsed(id);
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
		if (!firstSection && TreeTabsConfig.showGroupSeparators) {
			// Sits in the gap above the row, so it costs no slots of its own.
			graphics.fill(x, y - 2, x + span, y - 1, LINE_COLOR);
		}
		Font font = Minecraft.getInstance().font;
		String arrow = !TreeTabsConfig.collapsibleGroups ? "" : isCollapsed() ? "▸ " : "▾ ";

		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x + 1, y + 5, 0);
		pose.scale(TEXT_SCALE, TEXT_SCALE, 1f);
		int width = font.width(arrow + label.getString());
		graphics.drawString(font, arrow + label.getString(), 0, 0, TEXT_COLOR, false);
		graphics.drawString(font, "(" + count + ")", width + 4, 0, COUNT_COLOR, false);
		pose.popPose();
	}

	@Override
	public List<ClientTooltipComponent> getTooltip() {
		return List.of();
	}
}
