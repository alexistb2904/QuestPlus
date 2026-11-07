package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletAnimationControllerTest {
	@Test
	void interruptedTransitionsAreContinuousAndFinish() {
		var pose = new TabletAnimationController();
		pose.set(true, 1_000_000_000L, true);
		assertEquals(0, pose.value(1_000_000_000L, true));
		long midpoint = 1_000_000_000L + TabletFirstPersonAnimation.OPEN_NANOS / 2;
		float halfway = pose.value(midpoint, true);
		assertEquals(.5f, halfway, 1e-6);
		pose.set(false, midpoint, true);
		assertEquals(halfway, pose.value(midpoint, true), 1e-6);
		pose.set(true, 1_800_000_000L, true);
		assertEquals(1, pose.value(3_000_000_000L, true));
		pose.set(false, 3_000_000_000L, true);
		assertTrue(pose.finishedClosed(4_200_000_000L, true));
	}

	@Test
	void reducedMotionSwitchesImmediately() {
		var pose = new TabletAnimationController();
		pose.set(true, 0, false);
		assertEquals(1, pose.value(0, false));
		pose.set(false, 1, false);
		assertTrue(pose.finishedClosed(1, false));
	}
}
