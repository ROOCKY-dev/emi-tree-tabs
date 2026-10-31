package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarScaleTest {
	@Test
	void explicitSidebarSurvivesANarrowGuiScaledWindow() {
		SidebarScale scale = SidebarScale.standard();
		assertFalse(scale.fits(100, 220));
		assertTrue(scale.useSidebar("vertical", 100, 220));
		assertFalse(scale.useSidebar("auto", 100, 220));
		assertFalse(scale.useSidebar("horizontal", 960, 540));
		assertTrue(scale.useSidebar("auto", 960, 540));
	}

	@Test
	void contentsAreSmallerWithoutShrinkingThePanel() {
		SidebarScale scale = SidebarScale.standard();
		assertEquals(0.65, scale.factor);
		assertTrue(scale.toScreen(SidebarLayout.ROW_HEIGHT) < SidebarLayout.ROW_HEIGHT);
	}

	@Test
	void scaledButtonHitTargetMatchesItsDrawing() {
		for (double factor : new double[] {0.55, 0.65, 0.70}) {
			SidebarScale scale = SidebarScale.of(factor);
			for (boolean left : new boolean[] {true, false}) {
				SidebarLayout layout = new SidebarLayout(scale.toUnits(960), scale.toUnits(540),
						List.of(SidebarLayout.Row.tab()), 0, left);
				SidebarLayout.Slot slot = layout.slots().get(0);
				SidebarLayout.Rect button = scale.toScreen(slot.close());
				assertEquals(SidebarLayout.RowAction.CLOSE, layout.actionAt(slot,
						scale.toUnits(button.x() + button.width() / 2.0),
						scale.toUnits(button.y() + button.height() / 2.0)));
				assertEquals(336, scale.toScreen(layout.panel.width()), 1);
				assertEquals(540, scale.toScreen(layout.panel.height()), 2);
			}
		}
	}
}
