package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletNativeAnimationTest {
	@Test
	void allPlayerTabletClipsAreAvailableWithoutPal() {
		for (String name : new String[] {"open", "close", "idle", "carry", "carry_open", "carry_close", "carry_attack"}) {
			assertTrue(TabletNativeAnimation.loaded("questplus." + name));
		}
		assertEquals(1, TabletFirstPersonAnimation.duration("questplus.open"));
		assertEquals(.35, TabletFirstPersonAnimation.duration("questplus.carry_attack"));
	}

	@Test
	void samplesFullRigIncludingRootWithoutExternalBoneTypes() {
		var root = TabletFirstPersonAnimation.boneValue("questplus.open", 0, "fp_player_arms", "rotation");
		assertTrue(Math.abs(root.x()) > 1);
		var start = TabletFirstPersonAnimation.pose("questplus.open", 0);
		var end = TabletFirstPersonAnimation.pose("questplus.open", 1);
		assertNotEquals(start.tabletRotation(), end.tabletRotation());
	}
}
