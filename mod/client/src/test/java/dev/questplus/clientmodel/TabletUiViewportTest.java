package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TabletUiViewportTest {
	@Test
	void retainsTheSameTabletAspectRatioAtAllGuiScales() {
		for (int[] dimensions : new int[][] { { 1920, 1080 }, { 565, 300 }, { 480, 270 }, { 320, 180 }, { 160, 90 },
				{ 40, 35 } }) {
			for (double preference : new double[] { .75, 1, 1.25 }) {
				var viewport = TabletUiViewport.fit(dimensions[0], dimensions[1], preference);
				assertTrue(viewport.scale() > 0);
				assertTrue(viewport.scale() <= preference);
				assertEquals(dimensions[0] / 2.0, viewport.screenX(250), 1e-8);
				assertEquals(dimensions[1] / 2.0, viewport.screenY(150), 1e-8);
				assertTrue(viewport.screenX(500) <= dimensions[0], "x");
				assertTrue(viewport.screenY(300) <= dimensions[1], "y");
				assertEquals(5.0 / 3.0, (500 * viewport.scale()) / (300 * viewport.scale()), 1e-9);
			}
		}
	}

	@Test
	void mouseHitsStayAlignedWhenChangingScale() {
		for (double uiScale : new double[] { .75, 1, 1.25 }) {
			var viewport = TabletUiViewport.fit(565, 300, uiScale);
			for (int x : new int[] { 0, 14, 232, 486, 500 }) {
				for (int y : new int[] { 0, 28, 148, 270, 300 }) {
					assertEquals(x, viewport.localX(viewport.screenX(x)), 1e-8);
					assertEquals(y, viewport.localY(viewport.screenY(y)), 1e-8);
				}
			}
			assertEquals(42, viewport.localDelta(42 * viewport.scale()), 1e-8);
		}
	}

	@Test
	void rejectsInvalidScreenOrScale() {
		assertThrows(IllegalArgumentException.class, () -> TabletUiViewport.fit(0, 1080, 1));
		assertThrows(IllegalArgumentException.class, () -> TabletUiViewport.fit(800, 600, Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> TabletUiViewport.fit(800, 600, 0));
	}
}
