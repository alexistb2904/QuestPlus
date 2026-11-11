package dev.questplus.fabric.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class QuestSkinTest {
	private static int luma(int color) {
		return ((color >> 16 & 255) * 299 + (color >> 8 & 255) * 587 + (color & 255) * 114) / 1000;
	}

	@Test
	void coloursPickedForADarkGroundAreDarkenedToReadOnParchment() {
		assertEquals(QuestSkin.INK, QuestSkin.onPaper(QuestSkin.INK), "dark colours are left alone");
		for (int authored : new int[] { 0xffffffff, 0xffc6a986, 0xff55ff55, 0xffffff55 })
			assertTrue(luma(QuestSkin.onPaper(authored)) <= 84, Integer.toHexString(authored));
		int yellow = QuestSkin.onPaper(0xffffff55);
		assertEquals(0xff000000, yellow & 0xff000000, "alpha is kept");
		assertTrue((yellow >> 16 & 255) > (yellow & 255), "hue is kept: yellow stays more red than blue");
	}

	@Test
	void glyphSampleMatchesTheDensityItLandsOn() {
		// Samples are 1, 2, 3, 4, 6 and 8 texels per glyph unit.
		assertEquals(3, QuestSkin.sample(4), "the 4x tablet atlas uses the 4x rasterisation");
		assertEquals(1, QuestSkin.sample(2), "GUI scale 2 uses the 2x rasterisation");
		assertEquals(0, QuestSkin.sample(.6), "a zoomed-out chart drops to the 1x rasterisation");
		assertEquals(5, QuestSkin.sample(40), "densities beyond the set clamp to the finest sample");
		assertEquals(4, QuestSkin.sample(5.5), "nearest by ratio, not by difference");
	}
}
