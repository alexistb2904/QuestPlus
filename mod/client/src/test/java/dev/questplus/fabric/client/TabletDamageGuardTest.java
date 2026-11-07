package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TabletDamageGuardTest {
	@Test
	void damageClosesTabletByDefault() {
		var guard = new TabletDamageGuard();
		assertFalse(guard.observe(true, false, 20, 0));
		assertTrue(guard.observe(true, false, 18, 0));
		assertFalse(guard.observe(true, false, 18, 0));
		assertFalse(guard.observe(true, false, 18, 2));
		assertTrue(guard.observe(true, false, 18, 1));
	}

	@Test
	void absorptionDamageIsDetectedEvenWhenHealthDoesNotChange() {
		var guard = new TabletDamageGuard();
		assertFalse(guard.observe(true, false, 20, 10));
		assertTrue(guard.observe(true, false, 20, 6));
	}

	@Test
	void optInPreventsCloseAndStateStillTracks() {
		var guard = new TabletDamageGuard();
		assertFalse(guard.observe(true, true, 20, 4));
		assertFalse(guard.observe(true, true, 17, 3));
		assertFalse(guard.observe(true, false, 17, 3));
		assertTrue(guard.observe(true, false, 16, 3));
	}

	@Test
	void reopeningDoesNotTreatPriorDamageAsCurrentDamage() {
		var guard = new TabletDamageGuard();
		guard.observe(true, false, 20, 0);
		guard.observe(false, false, 10, 0);
		assertFalse(guard.observe(true, false, 10, 0));
		assertTrue(guard.observe(true, false, 9, 0));
		guard.reset();
		assertFalse(guard.observe(true, false, 5, 0));
	}
}
