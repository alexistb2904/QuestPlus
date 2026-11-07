package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * No Minecraft server required: the stat snapshot is injected as a trusted
 * event context.
 */
class CustomStatObjectiveTest {
	@TempDir
	Path root;

	private QuestEngine engine() throws Exception {
		Files.writeString(root.resolve("stat.json5"), """
				{
				  schemaVersion: 2, id: 'test:stat', category: 'test:category',
				  icon: 'minecraft:book', title: {en_us:'Jump fifty times'},
				  objectives: [{id:'jump',event:'custom_stat',target:'minecraft:jump',count:50}],
				  rewards: [], progression: 'once'
				}
				""");
		return new QuestEngine(QuestRegistry.load(root, (kind, target) -> {
		}));
	}

	private static QuestEvent snapshot(long value) {
		return new QuestEvent("custom_stat", "minecraft:jump", Set.of(), 1,
				Map.of("stateValues", Map.of("minecraft:jump", value)));
	}

	@Test
	void statObjectiveCompletesOnlyOnceAtTarget() throws Exception {
		var engine = engine();
		var player = new PlayerProgress();
		assertTrue(engine.apply(player, snapshot(40)).isEmpty());
		assertEquals(40L, engine.progress(player, "test:stat").counters.get("jump"));
		assertTrue(engine.apply(player, snapshot(49)).isEmpty());
		assertEquals(List.of("test:stat"), engine.apply(player, snapshot(50)));
		assertTrue(engine.apply(player, snapshot(60)).isEmpty());
	}

	@Test
	void statSnapshotIsCurrentValueNotCumulativeDelta() throws Exception {
		var engine = engine();
		var player = new PlayerProgress();
		engine.apply(player, snapshot(30));
		engine.apply(player, snapshot(20));
		assertEquals(20L, engine.progress(player, "test:stat").counters.get("jump"));
	}
}
