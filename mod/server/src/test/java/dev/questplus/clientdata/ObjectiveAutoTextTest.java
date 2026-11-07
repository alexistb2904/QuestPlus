package dev.questplus.clientdata;

import dev.questplus.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveAutoTextTest {
	@TempDir
	Path temp;

	@Test
	void missingDescriptionAndObjectiveTextAreValid() throws Exception {
		Files.writeString(temp.resolve("stone.json"), """
				{"schemaVersion":2,"id":"questplus:mining/cobble","category":"questplus:mining",
				"title":{"en_us":"Cobblestone"},"icon":"minecraft:cobblestone",
				"objectives":[{"id":"break","event":"block_break","target":"minecraft:cobblestone","count":32}],
				"rewards":[],"progression":"once"}
				""");
		var registry = QuestRegistry.load(temp, (kind, id) -> {
		});
		var quest = registry.get("questplus:mining/cobble");
		assertTrue(quest.description().isEmpty());
		assertTrue(quest.objectives().getFirst().text().isEmpty());
		assertEquals("Break 32 cobblestone blocks",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "en_us", "Cobblestone"));
		assertEquals("Casser 32 pierres",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "fr_fr", "Pierres"));
		assertEquals("Romper 32 piedras",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "es_es", "Piedras"));
		assertEquals("破坏 32 圆石", QuestPresentation.objectiveText(quest.objectives().getFirst(), "zh_cn", "圆石"));
	}

	@Test
	void generatedLabelsUseTheCurrentTierAcrossAllSupportedLanguages() {
		var objective = new QuestDefinition.Objective("stone", "block_break", "minecraft:stone", 1,
				Map.of(), null);
		var tiers = List.of(
				new QuestDefinition.Tier("first", 250, List.of()),
				new QuestDefinition.Tier("second", 1000, List.of()));
		var quest = new QuestDefinition("questplus:stone_mountain", "questplus:quarries",
				"minecraft:stone", Map.of(), Map.of(), List.of(objective), null, List.of(),
				"tiered", null, Map.of(), "fingerprint", "all", tiers, "stop", 0, 0,
				false, "standard", "manual");
		var first = com.google.gson.JsonParser.parseString("{tier:0}").getAsJsonObject();
		var second = com.google.gson.JsonParser.parseString("{tier:1}").getAsJsonObject();
		assertEquals(250, QuestPresentation.target(quest, objective, first));
		assertEquals(1000, QuestPresentation.target(quest, objective, second));
		String[] locales = { "en_us", "fr_fr", "es_es", "zh_cn" };
		String[] targets = { "Stone", "Roche", "Piedra", "石头" };
		String[] firstLabels = { "Break 250 stone blocks", "Casser 250 roches",
				"Romper 250 piedras", "破坏 250 石头" };
		String[] secondLabels = { "Break 1000 stone blocks", "Casser 1000 roches",
				"Romper 1000 piedras", "破坏 1000 石头" };
		for (int i = 0; i < locales.length; i++) {
			assertEquals(firstLabels[i], QuestPresentation.objectiveText(objective, locales[i], targets[i],
					QuestPresentation.target(quest, objective, first)), locales[i]);
			assertEquals(secondLabels[i], QuestPresentation.objectiveText(objective, locales[i], targets[i],
					QuestPresentation.target(quest, objective, second)), locales[i]);
		}
	}

	@Test
	void changingTierUpdatesDistanceLabelsToo() {
		var distance = new QuestDefinition.Objective("distance", "travel_distance", "questplus:travel", 100,
				Map.of(), null);
		assertEquals("Travel 250 m", QuestPresentation.objectiveText(distance, "en_us", "", 25000));
		assertEquals("Parcourir 1000 m", QuestPresentation.objectiveText(distance, "fr_fr", "", 100000));
		assertEquals("Recorrer 1000 m", QuestPresentation.objectiveText(distance, "es_es", "", 100000));
		assertEquals("行进 1000 m", QuestPresentation.objectiveText(distance, "zh_cn", "", 100000));
	}

	@Test
	void doesNotRepeatIdenticalObjectiveAndDescription() {
		assertTrue(QuestPresentation.redundantDescription("", List.of("Break 32 Cobblestone")));
		assertTrue(QuestPresentation.redundantDescription("Break 32 Cobblestone.", List.of("Break 32 Cobblestone")));
		assertFalse(QuestPresentation.redundantDescription("Build a shelter", List.of("Break 32 Cobblestone")));
		assertTrue(QuestPresentation.redundantDescription("Break 32 Cobblestone",
				List.of("Break 32 Cobblestone", "Craft 1 Pickaxe")));
		assertEquals("Craft 4 pickaxes",
				QuestPresentation.objectiveText(
						new QuestDefinition.Objective("c", "item_craft", "minecraft:iron_pickaxe", 4, Map.of(), null),
						"en_us", "Pickaxes"));
	}
}
