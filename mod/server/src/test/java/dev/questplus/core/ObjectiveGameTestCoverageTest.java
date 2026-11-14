package dev.questplus.core;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ObjectiveGameTestCoverageTest {
	@Test
	void everyShippedTriggerHasAnExecutableGameTest() throws Exception {
		Path tests = Path.of("server/src/gametest");
		var coverage = JsonParser.parseString(Files.readString(tests.resolve("resources/objective-coverage.json")))
				.getAsJsonObject();
		Set<String> covered = new TreeSet<>();
		for (var entry : coverage.entrySet()) {
			String[] test = entry.getKey().split("\\.");
			String source = Files.readString(tests.resolve("java/dev/questplus/fabric/" + test[0] + ".java"));
			assertTrue(source.contains("void " + test[1] + "(GameTestHelper helper)"), entry.getKey());
			for (var event : entry.getValue().getAsJsonArray()) {
				QuestExtensions.event(event.getAsString());
				assertTrue(covered.add(event.getAsString()), "Duplicate event mapping: " + event);
			}
		}
		var field = QuestExtensions.class.getDeclaredField("EVENTS");
		field.setAccessible(true);
		var events = (Map<?, ?>) field.get(null);
		Set<String> shipped = new TreeSet<>();
		for (Object key : events.keySet()) {
			String id = key.toString();
			if (!id.contains(":"))
				shipped.add(id);
		}
		assertEquals(shipped, covered, "New built-in objectives require an in-game coverage scenario");
	}
}
