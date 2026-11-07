package dev.questplus.fabric.api;

import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPlusApiTest {
	@Test
	void completionRejectsUnsupportedProgressions() {
		assertDoesNotThrow(() -> QuestPlusApi.validateCompletionType("once"));
		assertDoesNotThrow(() -> QuestPlusApi.validateCompletionType("repeatable"));
		assertThrows(IllegalArgumentException.class, () -> QuestPlusApi.validateCompletionType("tiered"));
		assertThrows(IllegalArgumentException.class, () -> QuestPlusApi.validateCompletionType("infinite"));
	}

	@Test
	void progressIsDetachedAndDoesNotExposeTheRewardLedger() {
		var source = new PlayerProgress.QuestProgress();
		source.counters.put("goal", 2L);
		var snapshot = QuestPlusApi.snapshot(source);
		assertTrue(snapshot.started());
		source.counters.put("goal", 3L);
		assertEquals(2L, snapshot.objectives().get("goal"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.objectives().put("goal", 5L));
		assertFalse(QuestPlusApi.snapshot(null).started());
	}
}
