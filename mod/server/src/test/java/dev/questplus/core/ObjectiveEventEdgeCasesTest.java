package dev.questplus.core;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveEventEdgeCasesTest {
	@TempDir
	Path root;

	private static List<String> events() throws Exception {
		var coverage = JsonParser.parseString(Files.readString(Path.of("server/src/gametest/resources/objective-coverage.json")))
				.getAsJsonObject();
		List<String> events = new ArrayList<>();
		for (var entry : coverage.entrySet())
			for (var event : entry.getValue().getAsJsonArray())
				events.add(event.getAsString());
		return events;
	}

	private QuestEngine engine(String event, boolean guarded, boolean condition) throws Exception {
		Path directory = Files.createDirectories(root.resolve(event.replace(':', '_')));
		Files.writeString(directory.resolve("quest.json5"), """
				{schemaVersion:2,id:'test:edge',category:'test:category',icon:'minecraft:stone',title:{en_us:'Edge cases'},
				 progression:'once',objectives:[{id:'goal',event:'%s',target:'test:target',count:%d%s}%s],rewards:[]}
				""".formatted(event, guarded ? 100 : 3, condition ? ",conditions:{sneaking:true}" : "",
				guarded ? ",{id:'guard',event:'manual',target:'test:guard',count:1}" : ""));
		return new QuestEngine(QuestRegistry.load(directory, (kind, target) -> {}));
	}

	private static QuestEvent event(String type, String target, long amount, boolean sneaking) {
		return new QuestEvent(type, target, Set.of(), amount,
				Map.of("submissionQuest", "test:edge", "submissionObjective", "goal", "manualQuest", "test:edge",
						"manualObjective", "goal", "inventory", Map.of("test:target", amount),
						"stateValues", Map.of("test:target", amount), "sneaking", sneaking));
	}

	private static long count(PlayerProgress player) {
		var progress = player.quests.get("test:edge");
		return progress == null ? 0 : progress.counters.getOrDefault("goal", 0L);
	}

	@TestFactory
	List<DynamicTest> wrongTargetReplaySaturationAndTerminalStateForEveryTrigger() throws Exception {
		List<DynamicTest> tests = new ArrayList<>();
		for (String type : events())
			tests.add(DynamicTest.dynamicTest(type, () -> {
				var engine = engine(type, false, false);
				var player = new PlayerProgress();
				assertTrue(engine.apply(player, event(type, "test:wrong", 2, false)).isEmpty());
				assertEquals(0, count(player));
				var first = event(type, "test:target", 2, false);
				assertTrue(engine.apply(player, first).isEmpty());
				assertEquals(2, count(player));
				assertTrue(engine.apply(player, first).isEmpty());
				assertEquals(2, count(player), "Replayed event cannot count twice");
				assertEquals(List.of("test:edge"), engine.apply(player, event(type, "test:target", Long.MAX_VALUE, false)));
				assertEquals(3, count(player), "Counter is capped without overflow");
				assertTrue(engine.apply(player, event(type, "test:target", 1, false)).isEmpty());
				assertEquals(3, count(player), "Terminal quest cannot award again");
			}));
		return tests;
	}

	@TestFactory
	List<DynamicTest> invalidAmountsCannotEnterAnyTrigger() throws Exception {
		List<DynamicTest> tests = new ArrayList<>();
		for (String type : events())
			tests.add(DynamicTest.dynamicTest(type, () -> {
				assertThrows(IllegalArgumentException.class, () -> event(type, "test:target", -1, false));
				assertThrows(IllegalArgumentException.class, () -> new QuestEvent(type, "bad target", Set.of(), 1, Map.of()));
				if (QuestExtensions.event(type).state())
					assertDoesNotThrow(() -> event(type, "test:target", 0, false));
				else
					assertThrows(IllegalArgumentException.class, () -> event(type, "test:target", 0, false));
			}));
		return tests;
	}

	@TestFactory
	List<DynamicTest> stateCountersFallAndInvalidConditionsClearStaleValues() throws Exception {
		List<DynamicTest> tests = new ArrayList<>();
		for (String type : events()) {
			if (!QuestExtensions.event(type).state())
				continue;
			tests.add(DynamicTest.dynamicTest(type, () -> {
				var engine = engine(type, true, true);
				var player = new PlayerProgress();
				engine.apply(player, event(type, "test:target", 8, true));
				assertEquals(8, count(player));
				engine.apply(player, event(type, "test:target", 3, true));
				assertEquals(3, count(player), "State is not accumulated");
				engine.apply(player, event(type, "test:target", 3, false));
				assertEquals(0, count(player), "Lost condition clears previously satisfied state");
				engine.apply(player, event(type, "test:target", 8, true));
				engine.apply(player, new QuestEvent(type, "test:target", Set.of(), 0, Map.of("sneaking", true)));
				assertEquals(0, count(player), "Missing state snapshot cannot preserve stale progress");
			}));
		}
		return tests;
	}
}
