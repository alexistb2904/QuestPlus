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
}
