package dev.questplus.core;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.*;

class QuestJson5Test {
	@Test
	void parsesJson5CommentsSingleQuotesAndTrailingCommas() {
		JsonElement parsed = QuestJson5.parse("""
				// Pack author note
				{ id: 'demo:one', objectives: [{ count: 3, },], hexValue: 0x10 }
				""");
		JsonObject obj = parsed.getAsJsonObject();
		assertEquals("demo:one", obj.get("id").getAsString());
		assertEquals(3, obj.getAsJsonArray("objectives").get(0).getAsJsonObject().get("count").getAsInt());
		assertEquals(16, obj.get("hexValue").getAsInt());
	}

	@Test
	void rejectsUnsafeNumericsAndExcessiveInput() {
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse("{x:Infinity}"));
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse(" ".repeat(2_000_001)));
	}
	@Test
	void handlesEscapesExponentHexAndNestedTrailingCommas() {
		var value=QuestJson5.parse("{name:'Quest\\'s',num:+.5e2,nested:{abc_1:true,},items:[0x10,],}");
		assertEquals(50, value.getAsJsonObject().get("num").getAsInt());
		assertEquals(16, value.getAsJsonObject().getAsJsonArray("items").get(0).getAsInt());
		assertTrue(value.getAsJsonObject().getAsJsonObject("nested").get("abc_1").getAsBoolean());
	}

	@Test
	void rejectsInvalidOrNonFiniteAndDeepData() {
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse("{foo:NaN}"));
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse("{x:0x}"));
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse("{x:'unterminated}"));
		assertThrows(IllegalArgumentException.class, () -> QuestJson5.parse("[".repeat(66) + "1" + "]".repeat(66)));
	}
}

