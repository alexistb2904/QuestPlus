package dev.questplus.clientdata;

import dev.questplus.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestVisibilitySettingsTest {
	@TempDir
	Path root;

	@Test
	void restrictedDetailsAndTextAreRemovedOnTheServerAndOptionalQuestIsInertMetadata() throws Exception {
		Files.writeString(root.resolve("a.json"),
				"""
						{"schemaVersion":1,"id":"test:a","category":"test:cat","icon":"minecraft:stone","title":{"en_us":"A"},"progression":"once",
						 "objectives":[{"id":"one","event":"manual","target":"test:a","count":1}],"rewards":[]}
						""");
		Files.writeString(root.resolve("b.json"),
				"""
						{"schemaVersion":1,"id":"test:b","category":"test:cat","icon":"minecraft:diamond","title":{"en_us":"B"},"description":{"en_us":"Secret prose"},"progression":"once",
						 "requires":{"quest":"test:a"},"objectives":[{"id":"one","event":"block_break","target":"minecraft:diamond_ore","count":1}],"rewards":[{"type":"experience","amount":4}],
						 "presentation":{"subtitle":{"en_us":"Subtitle"},"tags":["mining"],"shape":"hexagon","iconScale":1.2,"optional":true,"disableCompletionToast":true,"hideDetailsUntilUnlocked":true,"hideTextUntilCompleted":true}}
						""");
		var registry = QuestRegistry.load(root, (kind, id) -> {
		});
		var state = new PlayerProgress();
		var initialRedaction = ClientProjection.redactionFlags(registry.all(), state);
		assertEquals(3, initialRedaction.get("test:b"));
		String locked = ClientProjection.quests(registry.all(), state).toString();
		assertFalse(locked.contains("Secret prose"));
		assertFalse(locked.contains("minecraft:diamond_ore"));
		var engine = new QuestEngine(registry);
		engine.grant(state, "test:a");
		assertEquals(1, ClientProjection.redactionFlags(registry.all(), state).get("test:b"));
		String unlocked = ClientProjection.quests(registry.all(), state).toString();
		assertTrue(unlocked.contains("minecraft:diamond_ore"));
		assertFalse(unlocked.contains("Secret prose"));
		engine.grant(state, "test:b");
		assertEquals(0, ClientProjection.redactionFlags(registry.all(), state).get("test:b"));
		assertTrue(ClientProjection.quests(registry.all(), state).toString().contains("Secret prose"));
		assertTrue(registry.get("test:b").presentation().optional());
	}
}
