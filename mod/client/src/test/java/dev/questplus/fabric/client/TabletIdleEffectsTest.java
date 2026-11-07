package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletIdleEffectsTest {
	@Test
	void glowIsSlowBoundedAndPeriodic() {
		for (int tick = 0; tick < 720; tick++) {
			double seconds = tick / 20.0;
			var frame = new TabletIdleEffects.Frame(seconds, true);
			assertTrue(frame.glow() >= .1199f && frame.glow() <= .4001f);
			assertEquals(frame.glow(), new TabletIdleEffects.Frame(seconds + 6, true).glow(), .00001f);
			assertTrue(Math.abs(frame.glow() - new TabletIdleEffects.Frame(seconds + .05, true).glow()) < .008);
			for (int i = 0; i < 4; i++) {
				assertTrue(frame.opacity(i) >= 0 && frame.opacity(i) <= .6501f);
				assertEquals(frame.opacity(i), new TabletIdleEffects.Frame(seconds + 3.6, true).opacity(i), .00001f);
			}
		}
	}

	@Test
	void reducedMotionKeepsGlowStaticAndSuppressesMotes() {
		for (double seconds : new double[] { 0, 1, 5, 12345 }) {
			var frame = new TabletIdleEffects.Frame(seconds, false);
			assertEquals(.26f, frame.glow());
			for (int i = 0; i < 4; i++)
				assertEquals(0, frame.opacity(i));
		}
	}
}
