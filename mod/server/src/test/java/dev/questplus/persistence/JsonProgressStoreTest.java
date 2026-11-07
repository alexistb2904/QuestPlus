package dev.questplus.persistence;

import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonProgressStoreTest {
	@TempDir
	Path root;
	private final UUID id = UUID.randomUUID();

	private Path file() {
		return root.resolve("players").resolve(id + ".json");
	}

	private PlayerProgress progress(String claim) {
		var p = new PlayerProgress();
		p.claims.put("questplus:first", claim);
		return p;
	}

	@Test
	void roundTripAndBackupPrevious() throws Exception {
		try (var store = new JsonProgressStore(root)) {
			store.save(id, progress("reserved")).join();
			store.save(id, progress("delivered")).join();
			assertEquals("delivered", store.load(id).claims.get("questplus:first"));
			assertTrue(Files.readString(file().resolveSibling(id + ".json.bak")).contains("reserved"));
		}
	}

	@Test
	void snapshotsAreDetachedAndOrdered() throws Exception {
		try (var store = new JsonProgressStore(root)) {
			var p = progress("reserved");
			var first = store.save(id, p);
			p.claims.put("questplus:first", "delivered");
			var second = store.save(id, p);
			first.join();
			second.join();
			assertEquals("delivered", store.load(id).claims.get("questplus:first"));
			assertTrue(Files.readString(file().resolveSibling(id + ".json.bak")).contains("reserved"));
		}
	}

	@Test
	void deferredAutosaveUsesDetachedMutableState() throws Exception {
		var source = progress("reserved");
		var quest = new PlayerProgress.QuestProgress();
		quest.fingerprint = "stable";
		quest.counters.put("one", 3L);
		quest.cumulative.put("one", 10L);
		quest.reachedTiers.add("first");
		source.quests.put("questplus:first", quest);
		source.entitlements.put("questplus:first", new PlayerProgress.RewardGrant("questplus:first",
				List.of(new dev.questplus.core.QuestDefinition.Reward("experience", null, 0, 4))));
		try (var store = new JsonProgressStore(root)) {
			var autosave = store.saveAutosaveBatch(Map.of(id, source));
			// Mutate every nested structure immediately after queueing the write.
			source.claims.put("questplus:first", "delivered");
			quest.counters.put("one", 99L);
			quest.cumulative.clear();
			quest.reachedTiers.clear();
			source.entitlements.clear();
			autosave.join();
			var restored = store.load(id);
			assertEquals("reserved", restored.claims.get("questplus:first"));
			assertEquals(3L, restored.quests.get("questplus:first").counters.get("one"));
			assertEquals(10L, restored.quests.get("questplus:first").cumulative.get("one"));
			assertTrue(restored.quests.get("questplus:first").reachedTiers.contains("first"));
			assertFalse(restored.entitlements.isEmpty());
		}
	}

	@Test
	void rejectsInvalidFilesWithoutOverwrite() throws Exception {
		Files.createDirectories(file().getParent());
		try (var store = new JsonProgressStore(root)) {
			for (String invalid : List.of("{", "{\"schemaVersion\":2,\"quests\":{},\"claims\":{}}",
					"{\"quests\":{},\"claims\":{}}",
					"{\"schemaVersion\":1,\"schemaVersion\":1,\"quests\":{},\"claims\":{}}",
					"{\"schemaVersion\":1,\"quests\":{},\"claims\":{},\"future\":true}")) {
				Files.writeString(file(), invalid);
				assertThrows(java.io.IOException.class, () -> store.load(id));
				assertThrows(java.util.concurrent.CompletionException.class,
						() -> store.save(id, progress("reserved")).join());
				assertEquals(invalid, Files.readString(file()));
			}
		}
	}

	@Test
	void refusesAbsentPrimaryWithLeftovers() throws Exception {
		Files.createDirectories(file().getParent());
		Files.writeString(file().resolveSibling(id + ".json.tmp"), "{}");
		try (var store = new JsonProgressStore(root)) {
			assertThrows(java.io.IOException.class, () -> store.load(id));
			assertThrows(java.util.concurrent.CompletionException.class,
					() -> store.save(id, progress("reserved")).join());
		}
	}

	@Test
	void closeDrainsPendingWrites() throws Exception {
		var store = new JsonProgressStore(root);
		var pending = store.save(id, progress("reserved"));
		store.close();
		assertTrue(pending.isDone());
		assertFalse(pending.isCompletedExceptionally());
		try (var reopened = new JsonProgressStore(root)) {
			assertEquals("reserved", reopened.load(id).claims.get("questplus:first"));
		}
	}

	@Test
	void finalSavesContinueAfterCorruptPlayerAndExceedQueueCapacity() throws Exception {
		var store = new JsonProgressStore(root);
		store.save(id, progress("reserved")).join();
		Files.writeString(file(), "{corrupt");
		Map<UUID, PlayerProgress> states = new LinkedHashMap<>();
		states.put(id, progress("delivered"));
		for (int i = 0; i < 140; i++)
			states.put(UUID.randomUUID(), progress("delivered"));
		assertThrows(java.io.IOException.class, () -> store.saveAndClose(states));
		assertEquals("{corrupt", Files.readString(file()));
		try (var reopened = new JsonProgressStore(root)) {
			for (UUID uuid : states.keySet())
				if (!uuid.equals(id))
					assertEquals("delivered", reopened.load(uuid).claims.get("questplus:first"));
		}
	}

	@Test
	void autosaveBatchDoesNotExhaustQueueForRewardReservation() throws Exception {
		Map<UUID, PlayerProgress> batch = new LinkedHashMap<>();
		for (int i = 0; i < 140; i++)
			batch.put(UUID.randomUUID(), progress("delivered"));
		try (var store = new JsonProgressStore(root)) {
			var autosave = store.saveBatch(batch);
			var reservation = store.save(id, progress("reserved"));
			autosave.join();
			reservation.join();
			assertEquals("reserved", store.load(id).claims.get("questplus:first"));
			for (UUID uuid : batch.keySet())
				assertEquals("delivered", store.load(uuid).claims.get("questplus:first"));
		}
	}
}
