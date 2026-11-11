package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;

class EnginePerformanceTest {
	@TempDir
	Path root;

	private static final class CountedProgress extends HashMap<String, PlayerProgress.QuestProgress> {
		int scans;

		@Override
		public void forEach(BiConsumer<? super String, ? super PlayerProgress.QuestProgress> action) {
			scans++;
			super.forEach(action);
		}
	}

	private void quest(String id, String extra) throws Exception {
		Files.writeString(root.resolve(id + ".json"),
				"""
						{"schemaVersion":2,"id":"test:%s","category":"test:c","icon":"minecraft:stone","title":{"en_us":"Q"},
						 "progression":"once","objectives":[{"id":"one","event":"block_break","target":"minecraft:stone","count":1000000}],"rewards":[]%s}
						"""
						.formatted(id, extra));
	}

	private QuestEvent event() {
		return new QuestEvent("block_break", "minecraft:stone", Set.of(), 1, Map.of());
	}

	@Test
	void sparseEventsDoNotScanThousandsOfHistoricalQuests() throws Exception {
		quest("active", "");
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var engine = new QuestEngine(registry);
		var state = new PlayerProgress();
		var counted = new CountedProgress();
		state.quests = counted;
		for (int i = 0; i < 1000; i++) {
			var p = new PlayerProgress.QuestProgress();
			p.completed = true;
			p.fingerprint = "old";
			counted.put("test:old" + i, p);
		}
		Set<String> changed = new HashSet<>();
		engine.applyDetailed(state, event(), changed);
		assertEquals(Set.of("test:active"), changed);
		assertEquals(0, counted.scans);
		assertEquals(1L, state.quests.get("test:active").counters.get("one"));
	}

	@Test
	void prerequisiteSnapshotIsReusedUntilProgressChanges() throws Exception {
		quest("first", "");
		var engine = new QuestEngine(QuestRegistry.load(root, (kind, id) -> {
		}));
		var state = new PlayerProgress();
		var before = QuestEngine.prerequisiteState(state);
		assertSame(before, QuestEngine.prerequisiteState(state));
		engine.grant(state, "test:first");
		assertTrue(QuestEngine.prerequisiteState(state).contains("test:first"));
		assertFalse(before.contains("test:first"), "Earlier snapshots stay pre-event");
	}

	@Test
	void fanoutSharesSnapshotsAndKeepsPreEventStartedSemantics() throws Exception {
		quest("first", "");
		quest("not_started", ",\"requires\":{\"not\":{\"started\":\"test:first\"}}");
		for (int i = 0; i < 100; i++)
			quest("next" + i, ",\"requires\":{\"started\":\"test:first\"}");
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var engine = new QuestEngine(registry);
		var state = new PlayerProgress();
		var counted = new CountedProgress();
		state.quests = counted;
		engine.applyDetailed(state, event());
		assertFalse(state.quests.containsKey("test:next0"));
		assertEquals(1L, state.quests.get("test:not_started").counters.get("one"));
		counted.scans = 0;
		Set<String> changed = new HashSet<>();
		engine.applyDetailed(state, event(), changed);
		// The pre-event snapshot is the memo left by the previous event. Only the post-event one is rebuilt.
		assertEquals(1, counted.scans);
		assertEquals(101, changed.size());
		assertFalse(changed.contains("test:not_started"));
		for (int i = 0; i < 100; i++)
			assertEquals(1L, state.quests.get("test:next" + i).counters.get("one"));
	}

	@Test
	void trackedCascadesIncludeAllAwardsAndDependencyConsumers() throws Exception {
		quest("first", "");
		quest("next", ",\"progressBeforeUnlock\":true,\"requires\":{\"quest\":\"test:first\"}");
		quest("last", ",\"progressBeforeUnlock\":true,\"requires\":{\"quest\":\"test:next\"}");
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var engine = new QuestEngine(registry);
		var state = new PlayerProgress();
		engine.applyDetailed(state, event());
		state.quests.values().forEach(p -> p.counters.put("one", 999999L));
		var changed = new HashSet<String>();
		var awards = engine.applyDetailed(state, event(), changed);
		assertEquals(Set.of("test:first", "test:next", "test:last"),
				new HashSet<>(awards.stream().map(QuestEngine.Completion::questId).toList()));
		assertEquals(Set.of("test:first", "test:next", "test:last"), changed);
		assertEquals(Set.of("test:next"), registry.dependentQuests("test:first"));
		assertTrue(state.quests.get("test:last").terminal);
		assertTrue(engine.applyDetailed(state, event()).isEmpty());
	}
}
