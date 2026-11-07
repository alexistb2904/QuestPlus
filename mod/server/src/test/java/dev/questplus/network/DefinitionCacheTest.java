package dev.questplus.network;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DefinitionCacheTest {
	@Test
	void visibilityRedactionAndReloadCannotReusePrivateDefinitions() {
		var cache = new DefinitionCache();
		var builds = new AtomicInteger();
		var visible = new HashSet<>(Set.of("visible"));
		var redacted = Map.of("visible", 2);
		assertEquals("safe", cache.get(1, visible, redacted, () -> {
			builds.incrementAndGet();
			return "safe";
		}));
		visible.add("hidden");
		assertEquals("safe", cache.get(1, Set.of("visible"), redacted, () -> {
			throw new AssertionError("miss");
		}));
		assertEquals("revealed", cache.get(1, visible, redacted, () -> "revealed"));
		assertEquals("unlocked", cache.get(1, Set.of("visible"), Map.of("visible", 0), () -> "unlocked"));
		assertEquals("reloaded", cache.get(2, Set.of("visible"), redacted, () -> "reloaded"));
		assertEquals(1, builds.get());
	}

	@Test
	void evictedAndOversizedEntriesAreRebuilt() {
		var cache = new DefinitionCache();
		for (int i = 0; i < 9; i++)
			cache.get(1, Set.of("q" + i), Map.of(), () -> "small");
		assertEquals("again", cache.get(1, Set.of("q0"), Map.of(), () -> "again"));
		var large = "x".repeat(8 * 1024 * 1024 + 1);
		cache.get(1, Set.of("big"), Map.of(), () -> large);
		assertEquals("uncached", cache.get(1, Set.of("big"), Map.of(), () -> "uncached"));
	}
}
