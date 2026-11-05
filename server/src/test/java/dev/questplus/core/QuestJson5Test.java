package dev.questplus.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;

class QuestJson5Test {
    @Test void parsesJson5CommentsSingleQuotesAndTrailingCommas() {
        JsonElement parsed=QuestJson5.parse("""
            // Pack author note
            { id: 'demo:one', objectives: [{ count: 3, },], hexValue: 0x10 }
            """);
        JsonObject obj=parsed.getAsJsonObject();
        assertEquals("demo:one",obj.get("id").getAsString());
        assertEquals(3,obj.getAsJsonArray("objectives").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(16,obj.get("hexValue").getAsInt());
    }
    @Test void rejectsUnsafeNumericsAndExcessiveInput() {
        assertThrows(IllegalArgumentException.class,()->QuestJson5.parse("{x:Infinity}"));
        assertThrows(IllegalArgumentException.class,()->QuestJson5.parse(" ".repeat(2_000_001)));
    }
}
