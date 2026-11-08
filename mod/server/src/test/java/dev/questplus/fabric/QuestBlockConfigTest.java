package dev.questplus.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestBlockConfigTest {
    @Test void roundTripAndDefaults() {
        assertEquals(QuestBlockConfig.DEFAULT,QuestBlockConfig.parse(QuestBlockConfig.DEFAULT.encode()));
        assertFalse(QuestBlockConfig.DEFAULT.automation());
    }
    @Test void rejectsUntrustedConfiguration() {
        var value=QuestBlockConfig.JSON.toJsonTree(QuestBlockConfig.DEFAULT).getAsJsonObject();
        value.addProperty("energy",1000);
        assertThrows(IllegalArgumentException.class,()->QuestBlockConfig.parse(value.toString()));value.remove("energy");
        value.addProperty("faces",64);
        assertThrows(IllegalArgumentException.class,()->QuestBlockConfig.parse(value.toString()));value.addProperty("faces",63);
        value.addProperty("threshold",1.5);
        assertThrows(ArithmeticException.class,()->QuestBlockConfig.parse(value.toString()));value.addProperty("threshold",1);
        value.addProperty("automation","true");
        assertThrows(IllegalArgumentException.class,()->QuestBlockConfig.parse(value.toString()));
    }
    @Test void rejectsDuplicateAndOversizedTargets() {
        var value=QuestBlockConfig.JSON.toJsonTree(QuestBlockConfig.DEFAULT).getAsJsonObject();
        var ids=new com.google.gson.JsonArray();ids.add("questplus:one");ids.add("questplus:one");value.add("quests",ids);
        assertThrows(IllegalArgumentException.class,()->QuestBlockConfig.parse(value.toString()));
        assertThrows(IllegalArgumentException.class,()->QuestBlockConfig.parse(" ".repeat(8193)));
    }
}
