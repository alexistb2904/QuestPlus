package dev.questplus.fabric.api;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestStudioDescriptorsTest {
    @Test void acceptsTypedAuthoringMetadataWithoutCode() {
        JsonArray fields=new JsonArray();
        fields.add(JsonParser.parseString("""
           {"key":"resource","type":"item","label":"Required item"}
           """));
        QuestStudioDescriptors.register("event","questplus_test:resource","Resource task",fields);
        var list=QuestStudioDescriptors.snapshot();
        assertTrue(list.asList().stream().anyMatch(x->x.getAsJsonObject().get("id").getAsString().equals("questplus_test:resource")));
        assertThrows(IllegalArgumentException.class,()->
            QuestStudioDescriptors.register("event","questplus_test:resource","Duplicate",new JsonArray()));
    }
    @Test void rejectsExecutableOrAmbiguousEditorFields() {
        JsonArray fields=new JsonArray();
        fields.add(JsonParser.parseString("""
           {"key":"script","type":"javascript","label":"Run JavaScript"}
           """));
        assertThrows(IllegalArgumentException.class,()->
            QuestStudioDescriptors.register("reward","questplus_test:unsafe","Unsafe",fields));
    }
}
