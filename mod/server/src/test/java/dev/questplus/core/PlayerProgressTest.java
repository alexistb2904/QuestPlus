package dev.questplus.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerProgressTest {
	@Test
	void emptyRepeatableOutcomesDoNotAccumulateOrRemoveReservedAndNonemptyRewards() {
		var state = new PlayerProgress();
		var empty = new PlayerProgress.RewardGrant("questplus:repeat", List.of());
		var ready = new PlayerProgress.RewardGrant("questplus:repeat",
				List.of(new QuestDefinition.Reward("experience", null, 0, 3)));
		state.entitle("ready", ready);
		state.entitlements.put("reserved-empty", empty);
		state.claims.put("reserved-empty", "reserved");
		for (int cycle = 0; cycle < 100; cycle++)
			state.entitle("cycle-" + cycle, empty);
		assertEquals(Set.of("ready", "reserved-empty", "cycle-99"), state.entitlements.keySet());
		state.entitle("next-ready", ready);
		assertEquals(Set.of("ready", "next-ready", "reserved-empty"), state.entitlements.keySet());
		state.claims.put("delivered", "delivered");
		state.entitle("delivered", empty);
		assertFalse(state.entitlements.containsKey("delivered"));
		state.validate();
	}
}
