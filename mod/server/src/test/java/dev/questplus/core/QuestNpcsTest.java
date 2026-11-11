package dev.questplus.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestNpcsTest {
	@TempDir
	Path root;

	private static final String NPCS = """
			[{"id":"miner","name":{"en_us":"Old Miner","fr_fr":"Vieux mineur"},"quests":["test:stone"],
			  "spawns":[{"type":"structure","target":"#minecraft:village","chance":0.1},
			            {"type":"structure","target":"minecraft:village_snowy","chance":0.5},
			            {"type":"random","target":"minecraft:overworld","chance":0.001}]}]
			""";

	private static Map<String, QuestNpcs.Npc> parse(String json, List<String> checked) {
		return QuestNpcs.parse(JsonParser.parseString(json), Set.of("test:stone"),
				(kind, target) -> checked.add(kind + " " + target));
	}

	@Test
	void parsesNpcAndValidatesEverySpawnTarget() {
		var checked = new ArrayList<String>();
		var npc = parse(NPCS, checked).get("miner");
		assertEquals(List.of("test:stone"), npc.quests());
		assertEquals("Vieux mineur", npc.name().get("fr_fr"));
		assertEquals(List.of("structure_visit #minecraft:village", "structure_visit minecraft:village_snowy",
				"dimension minecraft:overworld"), checked);
	}

	@Test
	void exactStructureBeatsTagThenTagApplies() {
		var npc = parse(NPCS, new ArrayList<>()).get("miner");
		var tags = Set.of("minecraft:village");
		assertEquals(0.5, QuestNpcs.structureRule(npc, "minecraft:village_snowy", tags).orElseThrow().chance());
		assertEquals(0.1, QuestNpcs.structureRule(npc, "minecraft:village_plains", tags).orElseThrow().chance());
		assertTrue(QuestNpcs.structureRule(npc, "minecraft:mineshaft", Set.of()).isEmpty());
	}

	@Test
	void rejectsUnknownQuestsBadChancesAndDuplicates() {
		for (String bad : List.of("[{\"id\":\"a\",\"name\":{\"en_us\":\"A\"},\"quests\":[\"test:missing\"]}]",
				"[{\"id\":\"a\",\"name\":{\"en_us\":\"A\"},\"quests\":[\"test:stone\"],"
						+ "\"spawns\":[{\"type\":\"structure\",\"target\":\"minecraft:x\",\"chance\":1.5}]}]",
				"[{\"id\":\"a\",\"name\":{\"en_us\":\"A\"},\"quests\":[\"test:stone\"],"
						+ "\"spawns\":[{\"type\":\"random\",\"target\":\"#minecraft:x\",\"chance\":0.5}]}]",
				"[{\"id\":\"a\",\"name\":{\"fr_fr\":\"A\"},\"quests\":[\"test:stone\"]}]",
				"[{\"id\":\"a\",\"name\":{\"en_us\":\"A\"},\"quests\":[\"test:stone\"]},"
						+ "{\"id\":\"a\",\"name\":{\"en_us\":\"B\"},\"quests\":[\"test:stone\"]}]"))
			assertThrows(IllegalArgumentException.class, () -> parse(bad, new ArrayList<>()), bad);
	}

	private static String png(int width, int height) {
		var bytes = java.nio.ByteBuffer.allocate(33).putLong(0x89504E470D0A1A0AL).putInt(13).putInt(0x49484452)
				.putInt(width).putInt(height);
		return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.array());
	}

	@Test
	void appearanceDefaultsToAPlainsVillagerAndAcceptsOnlySkinSizedPngs() {
		String base = "{\"id\":\"a\",\"name\":{\"en_us\":\"A\"},\"quests\":[]";
		assertEquals(QuestNpcs.Look.DEFAULT, parse("[" + base + "}]", new ArrayList<>()).get("a").look());
		var look = parse("[" + base + ",\"model\":\"player\",\"skin\":\"" + png(64, 64) + "\"}]", new ArrayList<>())
				.get("a").look();
		assertEquals("player", look.model());
		assertEquals("taiga", parse("[" + base + ",\"villager\":\"taiga\"}]", new ArrayList<>()).get("a").look().villager());
		for (String bad : List.of(",\"model\":\"zombie\"", ",\"villager\":\"moon\"",
				",\"model\":\"player\",\"skin\":\"" + png(64, 32) + "\"",
				",\"skin\":\"" + png(64, 64) + "\"", ",\"model\":\"player\",\"skin\":\"data:image/jpeg;base64,AAAA\""))
			assertThrows(IllegalArgumentException.class, () -> parse("[" + base + bad + "}]", new ArrayList<>()), bad);
	}

	@Test
	void offeredQuestOnlyProgressesAfterAcceptance() throws Exception {
		Files.writeString(root.resolve("stone.json"), """
				{"schemaVersion":2,"id":"test:stone","category":"test:category","icon":"minecraft:book",
				 "title":{"en_us":"Stone"},"rewards":[],"progression":"repeatable",
				 "objectives":[{"id":"a","event":"block_break","target":"minecraft:stone","count":1}]}
				""");
		var engine = new QuestEngine(QuestRegistry.load(root, (type, id) -> {
		}), System::currentTimeMillis, Set.of("test:stone"));
		var player = new PlayerProgress();
		var stone = new QuestEvent("block_break", "minecraft:stone", Set.of(), 1, Map.of());
		assertTrue(engine.apply(player, stone).isEmpty());
		assertFalse(player.quests.containsKey("test:stone"));

		engine.accept(player, "test:stone", Map.of());
		assertEquals(Optional.of("active"), engine.acceptBlocker(player, "test:stone", Map.of()));
		assertEquals(List.of("test:stone"),
				engine.apply(player, new QuestEvent("block_break", "minecraft:stone", Set.of(), 1, Map.of())));

		// A repeatable quest is offered again once completed; accepting starts a fresh attempt.
		assertTrue(engine.acceptBlocker(player, "test:stone", Map.of()).isEmpty());
		engine.accept(player, "test:stone", Map.of());
		assertEquals(0L, player.quests.get("test:stone").counters.getOrDefault("a", 0L));
	}
}
