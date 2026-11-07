package dev.questplus.fabric.api;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestStudioDescriptorsTest {
	@Test
	void acceptsTypedAuthoringMetadataWithoutCode() {
		JsonArray fields = new JsonArray();
		fields.add(JsonParser.parseString("""
				{"key":"resource","type":"item","label":"Required item"}
				"""));
		QuestStudioDescriptors.register("event", "questplus_test:resource", "Resource task", fields);
		var list = QuestStudioDescriptors.snapshot();
		assertTrue(list.asList().stream()
				.anyMatch(x -> x.getAsJsonObject().get("id").getAsString().equals("questplus_test:resource")));
		assertThrows(IllegalArgumentException.class, () -> QuestStudioDescriptors.register("event",
				"questplus_test:resource", "Duplicate", new JsonArray()));
	}

	@Test
	void allRegistryFieldTypesRemainDataOnly() {
		for (String type : java.util.List.of("fluid", "structure", "statistic", "advancement", "effect")) {
			var fields = new JsonArray();
			var field = new JsonObject();
			field.addProperty("key", "target");
			field.addProperty("type", type);
			field.addProperty("label", "Target");
			fields.add(field);
			assertDoesNotThrow(() -> QuestStudioDescriptors.register("condition", "questplus_test:" + type,
					"Registry picker", fields));
		}
	}

    @Test
    void acceptsBoundedNumbersAndChoicesButNoExecutableDescriptors() {
        var fields = JsonParser.parseString("""
                [{"key":"rate","type":"number","label":"Rate","min":0.25,"max":5.5,"default":1.5},
                 {"key":"mode","type":"choice","label":"Mode","options":["press","crusher"],"default":"press"}]
                """).getAsJsonArray();
        QuestStudioDescriptors.register("event", "questplus_test:typed", "Typed fields", fields);
        var descriptor = QuestStudioDescriptors.snapshot().asList().stream()
                .map(JsonElement::getAsJsonObject)
                .filter(v -> v.get("id").getAsString().equals("questplus_test:typed")).findFirst().orElseThrow();
        assertEquals(fields, descriptor.getAsJsonArray("fields"));

        for (String invalid : java.util.List.of(
                "[{key:'mode',type:'choice',label:'Mode',options:[]}]",
                "[{key:'mode',type:'choice',label:'Mode',options:['a'],default:'b'}]",
                "[{key:'rate',type:'integer',label:'Rate',min:1,max:2,default:1.5}]",
                "[{key:'run',type:'string',label:'Run',script:'alert(1)'}]")) {
            assertThrows(IllegalArgumentException.class, () -> QuestStudioDescriptors.register("event",
                    "questplus_test:invalid", "Invalid", JsonParser.parseString(invalid).getAsJsonArray()));
        }
    }

	@Test
	void rejectsExecutableOrAmbiguousEditorFields() {
		JsonArray fields = new JsonArray();
		fields.add(JsonParser.parseString("""
				{"key":"script","type":"javascript","label":"Run JavaScript"}
				"""));
		assertThrows(IllegalArgumentException.class,
				() -> QuestStudioDescriptors.register("reward", "questplus_test:unsafe", "Unsafe", fields));
	}
}
