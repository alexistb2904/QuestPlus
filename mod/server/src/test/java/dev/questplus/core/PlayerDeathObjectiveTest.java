package dev.questplus.core;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDeathObjectiveTest {
	@TempDir
	Path root;
	private final AtomicLong now = new AtomicLong(1000);

	private QuestEngine engine(String progression, int count) throws Exception {
		Files.writeString(root.resolve("death.json5"), """
				{schemaVersion:2,id:'test:death',category:'test:category',icon:'minecraft:stone',title:{en_us:'Death'},
				 progression:'%s',cooldownSeconds:%d,
				 objectives:[{id:'death',event:'player_death',target:'questplus:death',count:%d}],rewards:[]}
				""".formatted(progression, progression.equals("repeatable") ? 10 : 0, count));
		return new QuestEngine(QuestRegistry.load(root, (kind, id) -> {}), now::get);
	}

	private static QuestEvent death() {
		return new QuestEvent("player_death", "questplus:death", Set.of(), 1, Map.of());
	}

	@Test
	void deathsAccumulateOncePerEventAndCompleteAtTheTarget() throws Exception {
		var engine = engine("once", 2);
		var player = new PlayerProgress();
		var first = death();
		assertTrue(engine.apply(player, first).isEmpty());
		assertTrue(engine.apply(player, first).isEmpty());
		assertEquals(1L, engine.progress(player, "test:death").counters.get("death"));
		assertEquals(List.of("test:death"), engine.apply(player, death()));
		assertTrue(engine.apply(player, death()).isEmpty());
		assertEquals(2L, engine.progress(player, "test:death").counters.get("death"));
	}

	@Test
	void repeatableDeathObjectiveStillRequiresRestartAndCooldown() throws Exception {
		var engine = engine("repeatable", 1);
		var player = new PlayerProgress();
		assertEquals(List.of("test:death"), engine.apply(player, death()));
		assertTrue(engine.apply(player, death()).isEmpty());
		assertThrows(IllegalArgumentException.class, () -> engine.restart(player, "test:death"));
		now.set(11000);
		engine.restart(player, "test:death");
		assertEquals(List.of("test:death"), engine.apply(player, death()));
	}
}
