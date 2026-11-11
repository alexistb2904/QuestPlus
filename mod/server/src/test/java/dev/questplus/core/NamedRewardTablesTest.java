package dev.questplus.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedRewardTablesTest {
	@Test
	void chainsResolveToIndependentSnapshotsAndCyclesAndUnknownNamesFail() {
		var authored = JsonParser
				.parseString(
						"""
								[{"name":"base","reward":{"type":"questplus:random","data":{"options":[{"type":"experience","amount":7,"weight":1}]}}},
								 {"name":"alias","reward":{"type":"questplus:table","data":{"name":"base"}}}]
								""")
				.getAsJsonArray();
		var registry = new NamedRewardTables(authored);
		var ref = JsonParser.parseString("{\"type\":\"questplus:table\",\"data\":{\"name\":\"alias\"}}")
				.getAsJsonObject();
		var snapshot = registry.expand(ref);
		snapshot.getAsJsonObject("data").addProperty("rolls", 4);
		assertFalse(registry.expand(ref).getAsJsonObject("data").has("rolls"));
		authored.get(0).getAsJsonObject().add("reward", ref);
		assertThrows(IllegalArgumentException.class, () -> new NamedRewardTables(authored));
		ref.getAsJsonObject("data").addProperty("name", "missing");
		assertThrows(IllegalArgumentException.class, () -> registry.expand(ref));
	}

	/** Same file the Studio test asserts its export against: Studio-made crates must open on the server. */
	@Test
	void studioCrateTablesOpenDropAndGiveRarityOnTheServer(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
			throws Exception {
		java.nio.file.Files.copy(java.nio.file.Path.of("server/src/test/resources/studio-crate-tables.json"),
				root.resolve("queststudio-compatibility.json"));
		var tables = NamedRewardTables.load(root.resolve("quests"));
		for (String crate : List.of("Common chest", "Jackpot", "Jackpot alias")) {
			var list = new JsonArray();
			list.add(tables.crate(crate));
			double[] chance = { 1 };
			var rewards = RewardTables.resolve(QuestRegistry.rewards(list, (kind, id) -> {
			}), chance);
			// The Studio's default new table keeps an explicit 50% empty roll.
			assertTrue(rewards.size() == 1 || crate.equals("Common chest") && rewards.isEmpty(), crate);
			boolean netherite = !rewards.isEmpty() && "minecraft:netherite_ingot".equals(rewards.getFirst().item());
			if (!crate.equals("Common chest"))
				assertEquals(netherite ? 4 : 0, RewardTables.rarity(chance[0]), crate);
		}
		assertThrows(IllegalArgumentException.class, () -> tables.crate("Pick one"));
		var dropped = tables.drop("boss", 0, new java.util.Random(1));
		var components = dropped.getAsJsonObject("components");
		assertEquals("Common chest", components.getAsJsonObject("minecraft:custom_data").get("questplus_table").getAsString());
		assertEquals("Coffre commun", components.getAsJsonObject("minecraft:custom_name").get("text").getAsString());
		assertEquals("#4aa8ff", components.getAsJsonObject("minecraft:custom_name").get("color").getAsString());
		assertTrue(components.get("minecraft:enchantment_glint_override").getAsBoolean());
		assertNull(tables.drop("passive", 0, new java.util.Random(1)), "Only the authored drop weights apply");
	}
}
