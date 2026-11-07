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
		assertEquals("Break 32 Cobblestone",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "en_us", "Cobblestone"));
		assertEquals("Casser 32 Pierres",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "fr_fr", "Pierres"));
		assertEquals("Romper 32 Piedras",
				QuestPresentation.objectiveText(quest.objectives().getFirst(), "es_es", "Piedras"));
		assertEquals("破坏 32 圆石", QuestPresentation.objectiveText(quest.objectives().getFirst(), "zh_cn", "圆石"));
	}

	@Test
	void doesNotRepeatIdenticalObjectiveAndDescription() {
		assertTrue(QuestPresentation.redundantDescription("", List.of("Break 32 Cobblestone")));
		assertTrue(QuestPresentation.redundantDescription("Break 32 Cobblestone.", List.of("Break 32 Cobblestone")));
		assertFalse(QuestPresentation.redundantDescription("Build a shelter", List.of("Break 32 Cobblestone")));
		assertTrue(QuestPresentation.redundantDescription("Break 32 Cobblestone",
				List.of("Break 32 Cobblestone", "Craft 1 Pickaxe")));
		assertEquals("Craft 4 Pickaxes",
				QuestPresentation.objectiveText(
						new QuestDefinition.Objective("c", "item_craft", "minecraft:iron_pickaxe", 4, Map.of(), null),
						"en_us", "Pickaxes"));
	}
}
