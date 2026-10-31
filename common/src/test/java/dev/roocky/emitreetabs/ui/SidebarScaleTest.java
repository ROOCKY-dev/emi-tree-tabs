package dev.roocky.emitreetabs.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class SidebarScaleTest {
	@Test
	void explicitSidebarSurvivesANarrowGuiScaledWindow() {
		SidebarScale scale = SidebarScale.forScreen(360, 3);
		assertFalse(scale.fits(360, 220));
		assertTrue(scale.useSidebar("vertical", 360, 220));
		assertFalse(scale.useSidebar("auto", 360, 220));
		assertFalse(scale.useSidebar("horizontal", 960, 540));
		assertTrue(scale.useSidebar("auto", 960, 540));
	}

	@Test
	void scaleStaysInRequestedRange() {
		for (int width : new int[] {320, 480, 640, 960, 1920}) {
			for (double gui : new double[] {1, 2, 3, 4}) {
				double factor = SidebarScale.forScreen(width, gui).factor;
				assertTrue(factor >= 1.30 && factor <= 1.45);
			}
		}
	}

	@Test
	void scaledButtonHitTargetMatchesItsDrawing() {
		for (double factor : new double[] {1.30, 4.0 / 3.0, 1.45}) {
			SidebarScale scale = SidebarScale.of(factor);
			for (boolean left : new boolean[] {true, false}) {
				SidebarLayout layout = new SidebarLayout(scale.toUnits(960), scale.toUnits(540),
						List.of(SidebarLayout.Row.tab()), 0, left);
				SidebarLayout.Slot slot = layout.slots().get(0);
				SidebarLayout.Rect button = scale.toScreen(slot.close());
				assertEquals(SidebarLayout.RowAction.CLOSE, layout.actionAt(slot,
						scale.toUnits(button.x() + button.width() / 2.0),
						scale.toUnits(button.y() + button.height() / 2.0)));
				assertEquals(192, scale.toScreen(layout.panel.width()), 1);
				assertEquals(540, scale.toScreen(layout.panel.height()), 2);
			}
		}
	}
}
