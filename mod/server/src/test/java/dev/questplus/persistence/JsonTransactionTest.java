package dev.questplus.persistence;

import dev.questplus.core.PlayerProgress;
import dev.questplus.team.TeamData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JsonTransactionTest {
	@TempDir
	Path root;

	private PlayerProgress progress(String claim) {
		var player = new PlayerProgress();
		player.claims.put("questplus:first", claim);
		return player;
	}

	private TeamData team(UUID owner, UUID member) {
		var team = new TeamData();
		team.id = UUID.randomUUID();
		team.owner = owner;
		team.name = "Builders";
		team.members.addAll(Set.of(owner, member));
		return team;
	}

	@Test
	void everyInterruptedWriteReplaysEntireTransactionAndReplayIsIdempotent() throws Exception {
		for (int failAfter = 1; failAfter <= 5; failAfter++) {
			Path directory = root.resolve("step-" + failAfter);
			UUID a = UUID.randomUUID(), b = UUID.randomUUID();
			TeamData team = team(a, b);
			int stop = failAfter;
			AtomicInteger points = new AtomicInteger();
			try (var store = new JsonProgressStore(directory, point -> {
				if (points.incrementAndGet() == stop)
					throw new IOException("Injected interruption: " + point);
			})) {
				store.save(a, progress("reserved")).join();
				store.save(b, progress("reserved")).join();
				var nextA = progress("delivered");
				nextA.teamId = team.id;
				var nextB = progress("delivered");
				nextB.teamId = team.id;
				assertThrows(CompletionException.class,
						() -> store.transaction(Map.of(a, nextA, b, nextB), Map.of(team.id, team)).join());
				assertTrue(Files.exists(directory.resolve("transactions/pending.json")));
				assertThrows(CompletionException.class, () -> store.save(a, progress("reserved")).join());
			}
			for (int reopen = 0; reopen < 2; reopen++)
				try (var store = new JsonProgressStore(directory)) {
					assertEquals("delivered", store.load(a).claims.get("questplus:first"));
					assertEquals(team.id, store.load(a).teamId);
					assertEquals("delivered", store.load(b).claims.get("questplus:first"));
					assertEquals(team.id, store.load(b).teamId);
					assertEquals(Set.of(a, b), store.loadTeams().get(team.id).members);
					assertFalse(Files.exists(directory.resolve("transactions/pending.json")));
				}
		}
	}

	@Test
	void recoveryConflictDoesNotOverwriteAnyOtherFile() throws Exception {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		try (var store = new JsonProgressStore(root, point -> {
			throw new IOException("Stop after journal");
		})) {
			store.save(a, progress("reserved")).join();
			store.save(b, progress("reserved")).join();
			assertThrows(CompletionException.class, () -> store
					.transaction(Map.of(a, progress("delivered"), b, progress("delivered")), Map.of()).join());
		}
		Path bFile = root.resolve("players/" + b + ".json");
		Files.writeString(bFile, "corrupt");
		Path aFile = root.resolve("players/" + a + ".json");
		String originalA = Files.readString(aFile);
		assertThrows(IOException.class, () -> new JsonProgressStore(root));
		assertEquals(originalA, Files.readString(aFile));
		assertEquals("corrupt", Files.readString(bFile));
		assertTrue(Files.exists(root.resolve("transactions/pending.json")));
	}

	@Test
	void snapshotsRemainDetachedAndOrdinarySavesKeepTheirQueueOrder() throws Exception {
		UUID id = UUID.randomUUID();
		try (var store = new JsonProgressStore(root)) {
			var player = progress("reserved");
			var transaction = store.transaction(Map.of(id, player), Map.of());
			player.claims.put("questplus:first", "delivered");
			var last = store.save(id, player);
			transaction.join();
			last.join();
			assertEquals("delivered", store.load(id).claims.get("questplus:first"));
			assertTrue(Files.readString(root.resolve("players/" + id + ".json.bak")).contains("reserved"));
		}
	}

	@Test
	void migratesV1WithoutChangingClaimsOrInventingHistoricalRewards() throws Exception {
		UUID id = UUID.randomUUID();
		Path file = root.resolve("players/" + id + ".json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, """
				{"schemaVersion":1,"quests":{
				  "questplus:unclaimed":{"counters":{"one":2},"completed":true,"fingerprint":"old"},
				  "questplus:reserved":{"counters":{"one":2},"completed":true,"fingerprint":"old"},
				  "questplus:delivered":{"counters":{"one":2},"completed":true,"fingerprint":"old"}},
				 "claims":{"questplus:reserved":"reserved","questplus:delivered":"delivered"}}
				""");
		try (var store = new JsonProgressStore(root)) {
			PlayerProgress player = store.load(id);
			assertEquals(2, player.schemaVersion);
			assertTrue(player.migratedFromV1);
			assertEquals(Set.of("questplus:unclaimed"), player.legacyUnclaimed);
			assertTrue(player.entitlements.isEmpty());
			assertEquals(Map.of("questplus:reserved", "reserved", "questplus:delivered", "delivered"), player.claims);
			assertTrue(player.quests.get("questplus:unclaimed").terminal);
			store.save(id, player).join();
			assertEquals(Set.of("questplus:unclaimed"), store.load(id).legacyUnclaimed);
		}
	}

	@Test
	void unknownOrIncompleteJournalIsNeverDiscarded() throws Exception {
		Path directory = root.resolve("transactions");
		Files.createDirectories(directory);
		Path journal = directory.resolve("pending.json");
		String invalid = "{\"schemaVersion\":99,\"id\":\"" + UUID.randomUUID()
				+ "\",\"committed\":false,\"images\":[]}";
		Files.writeString(journal, invalid);
		assertThrows(IOException.class, () -> new JsonProgressStore(root));
		assertEquals(invalid, Files.readString(journal));
	}

	@Test
	void teamVersionAndMissingPrimaryAreRejected() throws Exception {
		UUID a = UUID.randomUUID(), b = UUID.randomUUID();
		TeamData team = team(a, b);
		try (var store = new JsonProgressStore(root)) {
			store.transaction(Map.of(), Map.of(team.id, team)).join();
		}
		Path file = root.resolve("teams/" + team.id + ".json");
		Files.writeString(file, Files.readString(file).replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":99"));
		try (var store = new JsonProgressStore(root)) {
			assertThrows(IOException.class, store::loadTeams);
		}
		Files.move(file, file.resolveSibling(file.getFileName() + ".bak"));
		try (var store = new JsonProgressStore(root)) {
			assertThrows(IOException.class, store::loadTeams);
		}
	}
}
