package dev.questplus.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureParityTest {
	@TempDir
	Path root;

	private void quest(String id, String extra, String event, String target, long count) throws Exception {
		Files.writeString(root.resolve(id + ".json"),
				"""
						{"schemaVersion":2,"id":"test:%s","category":"test:category","icon":"minecraft:stone","title":{"en_us":"Test"},
						 "progression":"once","objectives":[{"id":"goal","event":"%s","target":"%s","count":%d}],"rewards":[]%s}
						"""
						.formatted(id, event, target, count, extra));
	}

	@Test
	void startedAndQuorumPrerequisitesUseActualProgressAndKeepReferenceIntegrity() throws Exception {
		quest("a", "", "block_break", "minecraft:stone", 2);
		quest("b",
				",\"requires\":{\"at_least\":{\"count\":1,\"of\":[{\"started\":\"test:a\"},{\"quest\":\"test:a\"}]}}",
				"manual", "test:b", 1);
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var engine = new QuestEngine(registry);
		var player = new PlayerProgress();
		assertTrue(engine.apply(player, new QuestEvent("manual", "test:b", Set.of(), 1, Map.of())).isEmpty());
		engine.apply(player, new QuestEvent("block_break", "minecraft:stone", Set.of(), 1, Map.of()));
		assertFalse(player.quests.get("test:a").completed);
		assertEquals(List.of("test:b"),
				engine.apply(player, new QuestEvent("manual", "test:b", Set.of(), 1, Map.of())));
		quest("b", ",\"requires\":{\"started\":\"test:missing\"}", "manual", "test:b", 1);
		assertThrows(IllegalArgumentException.class, () -> QuestRegistry.load(root, (kind, id) -> {
		}));
	}

	@Test
	void observationStateResetsBeforeCompletionAndStageStateIsAuthoritative() throws Exception {
		quest("look", "", "observe_block", "minecraft:stone", 3);
		quest("stage", "", "stage", "test:chapter", 1);
		var engine = new QuestEngine(QuestRegistry.load(root, (kind, id) -> {
		}));
		var player = new PlayerProgress();
		engine.apply(player, new QuestEvent("observe_block", "minecraft:stone", Set.of(), 1,
				Map.of("stateValues", Map.of("minecraft:stone", 2L))));
		assertEquals(2L, player.quests.get("test:look").counters.get("goal"));
		engine.apply(player, new QuestEvent("observe_block", "minecraft:stone", Set.of(), 1,
				Map.of("stateValues", Map.of("minecraft:stone", 0L))));
		assertEquals(0L, player.quests.get("test:look").counters.get("goal"));
		assertEquals(List.of("test:look"), engine.apply(player, new QuestEvent("observe_block", "minecraft:stone",
				Set.of(), 1, Map.of("stateValues", Map.of("minecraft:stone", 3L)))));
		assertTrue(engine.apply(player, new QuestEvent("stage", "test:chapter", Set.of(), 1, Map.of())).isEmpty());
		assertEquals(List.of("test:stage"), engine.apply(player, new QuestEvent("stage", "test:chapter", Set.of(), 1,
				Map.of("stateValues", Map.of("test:chapter", 1L)))));
	}

	@Test
	void sparseObservationStillResetsOtherTargetsInTheSameSharedQuest() throws Exception {
		Files.writeString(root.resolve("gaze.json"), """
				{"schemaVersion":2,"id":"test:gaze","category":"test:category","icon":"minecraft:stone","title":{"en_us":"Gaze"},
				 "progression":"once","objectives":[{"id":"stone","event":"observe_block","target":"minecraft:stone","count":100},
				 {"id":"dirt","event":"observe_block","target":"minecraft:dirt","count":100}],"rewards":[]}
				""");
		var engine = new QuestEngine(QuestRegistry.load(root, (kind, id) -> {}));
		var shared = new PlayerProgress();
		var targets = Set.of("minecraft:stone", "minecraft:dirt");
		engine.apply(shared, new QuestEvent("observe_block", "minecraft:dirt", Set.of(), 1,
				Map.of("stateValues", new ObservationValues(targets, Map.of("minecraft:dirt", 12L)))));
		engine.apply(shared, new QuestEvent("observe_block", "minecraft:stone", Set.of(), 1,
				Map.of("stateValues", new ObservationValues(targets, Map.of("minecraft:stone", 1L)))));
		assertEquals(1L, shared.quests.get("test:gaze").counters.get("stone"));
		assertEquals(0L, shared.quests.get("test:gaze").counters.get("dirt"));
	}

	@Test
	void observationCannotSatisfyStageWithSameIdentifier() throws Exception {
		Files.writeString(root.resolve("collision.json"),
				"""
						{"schemaVersion":2,"id":"test:collision","category":"test:category","icon":"minecraft:stone","title":{"en_us":"Collision"},
						 "progression":"once","objectives":[{"id":"watch","event":"observe_entity","target":"minecraft:pig","count":20},
						 {"id":"stage","event":"stage","target":"minecraft:pig","count":1}],"rewards":[]}
						""");
		var engine = new QuestEngine(QuestRegistry.load(root, (kind, id) -> {
		}));
		var player = new PlayerProgress();
		assertTrue(engine.apply(player, new QuestEvent("observe_entity", "minecraft:pig", Set.of(), 1,
				Map.of("stateValues", Map.of("minecraft:pig", 20L)))).isEmpty());
		assertEquals(20, player.quests.get("test:collision").counters.get("watch"));
		assertEquals(0, player.quests.get("test:collision").counters.getOrDefault("stage", 0L));
		assertEquals(List.of("test:collision"), engine.apply(player, new QuestEvent("stage", "minecraft:pig", Set.of(),
				1, Map.of("stateValues", Map.of("minecraft:pig", 1L)))));
	}

	@Test
	void extensionObjectiveDataIsValidatedAndItsPredicateIsApplied() throws Exception {
		String event = "test:data_objective";
		QuestExtensions.registerEvent(event, "marker", false);
		QuestExtensions.registerObjective(event, data -> {
			if (!data.keySet().equals(Set.of("mode")) || !data.get("mode").isJsonPrimitive())
				throw new IllegalArgumentException("Invalid mode");
		}, (data, context) -> data.get("mode").getAsString().equals(context.context().get("mode")));
		quest("data", "", event, "test:target", 1);
		var json = JsonParser.parseString(Files.readString(root.resolve("data.json"))).getAsJsonObject();
		var payload = JsonParser.parseString("{\"mode\":\"trusted\"}").getAsJsonObject();
		json.getAsJsonArray("objectives").get(0).getAsJsonObject().add("data", payload);
		Files.writeString(root.resolve("data.json"), json.toString());
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var engine = new QuestEngine(registry);
		var player = new PlayerProgress();
		assertTrue(engine.apply(player, new QuestEvent(event, "test:target", Set.of(), 1, Map.of("mode", "wrong")))
				.isEmpty());
		assertEquals(List.of("test:data"),
				engine.apply(player, new QuestEvent(event, "test:target", Set.of(), 1, Map.of("mode", "trusted"))));
		payload.addProperty("unknown", true);
		Files.writeString(root.resolve("data.json"), json.toString());
		assertThrows(IllegalArgumentException.class, () -> QuestRegistry.load(root, (kind, id) -> {
		}));
	}

	@Test
	void observationRequiresOneContinuousTargetAndConsecutiveServerTicks() {
		var counter = new ObservationCounter();
		assertEquals(1, counter.update("overworld:block:1", 10));
		assertEquals(2, counter.update("overworld:block:1", 11));
		assertEquals(1, counter.update("overworld:block:2", 12));
		assertEquals(0, counter.update(null, 13));
		assertEquals(1, counter.update("overworld:block:2", 14));
		assertEquals(1, counter.update("overworld:block:2", 20));
		assertEquals(1, counter.update("overworld:block:2", 20));
	}
}
