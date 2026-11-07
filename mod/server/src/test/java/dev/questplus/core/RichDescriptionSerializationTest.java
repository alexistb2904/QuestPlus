package dev.questplus.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class RichDescriptionSerializationTest {
	@Test
	void pixelTextAndImagesRoundTripWithoutChangingTheLegacyDescription() {
		JsonObject text = new JsonObject();
		text.addProperty("type", "text");
		text.addProperty("text", "Quest description");
		text.addProperty("sizePx", 36);
		JsonObject image = new JsonObject();
		image.addProperty("type", "image");
		image.addProperty("data", "data:image/png;base64,YWJj");
		image.addProperty("width", 640);
		image.addProperty("height", 512);
		JsonArray parts = new JsonArray();
		parts.add(text);
		parts.add(image);
		var quest = new QuestDefinition("questplus:test", "questplus:test", "minecraft:book", Map.of("en_us", "Test"),
				Map.of("en_us", "Quest description"), List.of(), null, List.of(), "once", null, Map.of(), "fingerprint",
				"all", List.of(), "stop", 0, 0, false, "standard", "manual", false, 32, Map.of("en_us", parts));
		var gson = new Gson();
		var json = gson.toJsonTree(quest).getAsJsonObject();
		assertEquals("Quest description", json.getAsJsonObject("description").get("en_us").getAsString());
		assertEquals(36, json.getAsJsonObject("descriptionRich").getAsJsonArray("en_us").get(0).getAsJsonObject()
				.get("sizePx").getAsInt());
		var restored = gson.fromJson(json, QuestDefinition.class);
		assertEquals(640, restored.descriptionRich().get("en_us").get(1).getAsJsonObject().get("width").getAsInt());
		assertEquals("Quest description", restored.description().get("en_us"));
	}

	@Test
	void oldConstructorsDoNotRequireRichDescription() {
		var legacy = new QuestDefinition("questplus:old", "questplus:test", "minecraft:book", Map.of("en_us", "Old"),
				Map.of("en_us", "Legacy"), List.of(), null, List.of(), "once", null, Map.of(), "fingerprint", "all",
				List.of(), "stop", 0, 0, false, "standard", "manual", false, 32);
		assertTrue(legacy.descriptionRich().isEmpty());
		assertEquals(32, legacy.size());
	}
}
