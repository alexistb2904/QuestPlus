package dev.questplus.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class TransferObjectiveTest {
	@Test
	void fluidTransferHasItsOwnRegistry() {
		assertEquals("fluid", QuestExtensions.event("fluid_transfer").registry());
		assertFalse(QuestExtensions.event("fluid_transfer").state());
	}

	@Test
	void energyTransferUsesExplicitMarkerAndEventAmounts() {
		assertEquals("marker", QuestExtensions.event("energy_transfer").registry());
		QuestEvent event = new QuestEvent("energy_transfer", "questplus:fe", Set.of(), 8000,
				Map.of("dimension", "minecraft:overworld"));
		assertEquals(8000, event.amount());
		assertThrows(IllegalArgumentException.class,
				() -> new QuestEvent("energy_transfer", "questplus:fe", Set.of(), 0, Map.of()));
		assertThrows(IllegalArgumentException.class,
				() -> new QuestEvent("fluid_transfer", "BAD ID", Set.of(), 1000, Map.of()));
	}
}
