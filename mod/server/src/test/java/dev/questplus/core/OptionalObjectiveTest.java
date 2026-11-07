package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OptionalObjectiveTest {
	@TempDir
	Path root;

	private QuestRegistry load(String mode, int threshold) throws Exception {
		String json = """
				{"schemaVersion":2,"id":"test:options","category":"test:category",
				 "icon":"minecraft:book","title":{"en_us":"Selection"},
				 "objectives":[
				  {"id":"a","event":"block_break","target":"minecraft:stone","count":1},
				  {"id":"b","event":"block_break","target":"minecraft:dirt","count":1},
				  {"id":"c","event":"block_break","target":"minecraft:oak_log","count":1},
				  {"id":"bonus","event":"block_break","target":"minecraft:diamond_ore","count":1,"optional":true}
				 ],"rewards":[],"progression":"once","objectiveMode":"%s"%s}
				""".formatted(mode, mode.equals("at_least") ? ",\"objectiveThreshold\":" + threshold : "");
		Files.writeString(root.resolve("options.json"), json);
		return QuestRegistry.load(root, (type, id) -> {
		});
	}

	private QuestEvent breakBlock(String block) {
		return new QuestEvent("block_break", block, Set.of(), 1, Map.of());
	}

	@Test
	void allIgnoresOptionalObjectives() throws Exception {
		QuestEngine engine = new QuestEngine(load("all", 1));
		var player = new PlayerProgress();
		assertEquals(0, engine.apply(player, breakBlock("minecraft:stone")).size());
		assertEquals(0, engine.apply(player, breakBlock("minecraft:dirt")).size());
		assertEquals(1, engine.apply(player, breakBlock("minecraft:oak_log")).size());
	}

	@Test
	void sequenceAcceptsOptionalActionsWithoutAdvancingRequiredSteps() throws Exception {
		var engine = new QuestEngine(load("sequence", 1));
		var player = new PlayerProgress();
		assertTrue(engine.apply(player, breakBlock("minecraft:diamond_ore")).isEmpty());
		assertEquals(1L, player.quests.get("test:options").counters.get("bonus"));
		assertFalse(player.quests.get("test:options").counters.containsKey("b"));
		engine.apply(player, breakBlock("minecraft:dirt"));
		assertFalse(player.quests.get("test:options").counters.containsKey("b"));
		engine.apply(player, breakBlock("minecraft:stone"));
		engine.apply(player, breakBlock("minecraft:dirt"));
		assertEquals(1, engine.apply(player, breakBlock("minecraft:oak_log")).size());
	}

	@Test
	void atLeastTwoOfThreeAllowsAnyCombination() throws Exception {
		QuestEngine engine = new QuestEngine(load("at_least", 2));
		var player = new PlayerProgress();
		assertEquals(0, engine.apply(player, breakBlock("minecraft:diamond_ore")).size());
		assertEquals(0, engine.apply(player, breakBlock("minecraft:dirt")).size());
		assertEquals(1, engine.apply(player, breakBlock("minecraft:oak_log")).size());
	}

	@Test
	void impossibleThresholdFailsValidation() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> load("at_least", 4));
	}
}
