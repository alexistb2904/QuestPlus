package dev.questplus.team;

import dev.questplus.core.*;
import dev.questplus.persistence.JsonProgressStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TeamManagerTest {
	@TempDir
	Path root;
	private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
	private static final String QUEST = "questplus:work";

	private QuestRegistry registry() throws Exception {
		Path definitions = root.resolve("definitions");
		Files.createDirectories(definitions);
		Files.writeString(definitions.resolve("work.json"), """
				{"schemaVersion":1,"id":"questplus:work","category":"questplus:general","icon":"minecraft:stone",
				 "title":{"en_us":"Work"},"description":{"en_us":"Work"},"progression":"once","rewards":[],
				 "objectives":[{"id":"stone","event":"block_break","target":"minecraft:stone","count":2},
				               {"id":"logs","event":"block_break","target":"minecraft:oak_log","count":2}]}
				""");
		return QuestRegistry.load(definitions, (kind, id) -> {
		});
	}

	private PlayerProgress progress(QuestRegistry registry, long stone, long logs) {
		PlayerProgress player = new PlayerProgress();
		var q = new PlayerProgress.QuestProgress();
		q.fingerprint = registry.get(QUEST).fingerprint();
		q.counters.put("stone", stone);
		q.counters.put("logs", logs);
		q.completed = stone == 2 && logs == 2;
		q.terminal = q.completed;
		player.quests.put(QUEST, q);
		return player;
	}

	private void commit(JsonProgressStore store, TeamManager manager, TeamManager.Change change) {
		store.transaction(change.players(), change.teams()).join();
		manager.apply(change);
	}

	private UUID create(JsonProgressStore store, TeamManager manager) throws Exception {
		var change = manager.create(a, "Builders");
		UUID id = change.teams().keySet().iterator().next();
		commit(store, manager, change);
		return id;
	}

	private void join(JsonProgressStore store, TeamManager manager, UUID team, UUID player) throws Exception {
		commit(store, manager, manager.invite(a, player, 1_000));
		commit(store, manager, manager.join(player, team, 1_001));
	}

	@Test
	void managersCanInviteAndKickMembersButNotManagersOrOwner() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var manager = new TeamManager(store, registry(), new HashMap<>());
			UUID team = create(store, manager);
			join(store, manager, team, b);
			commit(store, manager, manager.setManager(a, b, true));
			commit(store, manager, manager.invite(b, c, 1_000));
			commit(store, manager, manager.join(c, team, 1_001));
			assertThrows(IllegalArgumentException.class, () -> manager.kick(b, a));
			assertThrows(IllegalArgumentException.class, () -> manager.transfer(b, c));
			assertThrows(IllegalArgumentException.class, () -> manager.disband(b));
			assertThrows(IllegalArgumentException.class, () -> manager.setManager(b, c, true));
			commit(store, manager, manager.kick(b, c));
			assertTrue(manager.teamOf(c).isEmpty());
		}
	}

	@Test
	void managerRoleSurvivesRestartAndIsRemovedWhenMemberLeaves() throws Exception {
		Path data = root.resolve("data");
		var definitions = registry();
		try (var store = new JsonProgressStore(data)) {
			var manager = new TeamManager(store, definitions, new HashMap<>());
			UUID team = create(store, manager);
			join(store, manager, team, b);
			commit(store, manager, manager.setManager(a, b, true));
		}
		try (var store = new JsonProgressStore(data)) {
			var manager = new TeamManager(store, definitions, new HashMap<>());
			commit(store, manager, manager.invite(b, c, 5_000));
			commit(store, manager, manager.leave(b));
			assertFalse(manager.teamOf(a).orElseThrow().managers.contains(b));
		}
	}

	@Test
	void duplicateNamesRespectPolicyAndIgnoreDisbandedTeams() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var manager = new TeamManager(store, registry(), new HashMap<>());
			create(store, manager);
			manager.allowDuplicateNames(false);
			assertThrows(IllegalArgumentException.class, () -> manager.create(b, "builders"));
			manager.allowDuplicateNames(true);
			commit(store, manager, manager.create(b, "BUILDERS"));
			commit(store, manager, manager.disband(a));
			commit(store, manager, manager.disband(b));
			manager.allowDuplicateNames(false);
			assertDoesNotThrow(() -> manager.create(c, "Builders"));
		}
	}

	@Test
	void changesAreDetachedAndMembershipUnique() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var registry = registry();
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, progress(registry, 1, 0));
			var manager = new TeamManager(store, registry, cache);
			var change = manager.create(a, "Builders");
			assertNull(cache.get(a).teamId);
			assertTrue(manager.allTeams().isEmpty());
			change.teams().values().iterator().next().progress.quests.get(QUEST).counters.put("stone", 2L);
			assertEquals(1L, cache.get(a).quests.get(QUEST).counters.get("stone"));
			commit(store, manager, change);
			assertThrows(IllegalArgumentException.class, () -> manager.create(a, "Again"));
			assertThrows(IllegalArgumentException.class, () -> manager.leave(a));
		}
	}

	@Test
	void invitationsCheckOwnerExpiryAndCannotBeReplayed() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var manager = new TeamManager(store, registry(), new HashMap<>());
			UUID team = create(store, manager);
			assertThrows(IllegalArgumentException.class, () -> manager.invite(b, c, 1_000));
			commit(store, manager, manager.invite(a, b, 1_000));
			assertThrows(IllegalArgumentException.class, () -> manager.join(b, team, 301_000));
			commit(store, manager, manager.join(b, team, 300_999));
			assertThrows(IllegalArgumentException.class, () -> manager.join(b, team, 301_000));
			commit(store, manager, manager.leave(b));
			assertThrows(IllegalArgumentException.class, () -> manager.join(b, team, 301_001));
			commit(store, manager, manager.invite(a, b, 302_000));
			commit(store, manager, manager.decline(b, team));
			assertThrows(IllegalArgumentException.class, () -> manager.decline(b, team));
		}
	}

	@Test
	void invitationDiscoveryIsPrivateExpiresAndDisappearsAfterJoining() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var manager = new TeamManager(store, registry(), new HashMap<>());
			UUID team = create(store, manager);
			assertTrue(manager.invitationsFor(b, 1_000).isEmpty());
			commit(store, manager, manager.invite(a, b, 1_000));
			assertEquals(List.of(team), manager.invitationsFor(b, 1_001).stream().map(t -> t.id).toList());
			assertTrue(manager.invitationsFor(c, 1_001).isEmpty());
			assertTrue(manager.invitationsFor(b, 301_000).isEmpty());
			commit(store, manager, manager.join(b, team, 300_999));
			assertTrue(manager.invitationsFor(b, 300_999).isEmpty());
		}
	}

	@Test
	void mergeUsesMaximumRecognizesHistoricalCompletionWithoutRewards() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var registry = registry();
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, progress(registry, 2, 1));
			cache.put(b, progress(registry, 1, 2));
			cache.get(b).claims.put(QUEST, "delivered");
			var manager = new TeamManager(store, registry, cache);
			UUID team = create(store, manager);
			join(store, manager, team, b);
			var shared = manager.get(team).progress;
			assertEquals(Map.of("stone", 2L, "logs", 2L), shared.quests.get(QUEST).counters);
			assertTrue(shared.quests.get(QUEST).completed);
			assertTrue(shared.entitlements.isEmpty());
			assertTrue(cache.get(a).entitlements.isEmpty());
			assertTrue(cache.get(b).entitlements.isEmpty());
			assertEquals("delivered", cache.get(b).claims.get(QUEST));
			commit(store, manager, manager.leave(b));
			join(store, manager, team, b);
			assertEquals(2L, manager.get(team).progress.quests.get(QUEST).counters.get("stone"));
			assertEquals("delivered", cache.get(b).claims.get(QUEST));
		}
	}

	@Test
	void incompatibleMergeDoesNotAlterEitherSource() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var registry = registry();
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, progress(registry, 1, 0));
			cache.put(b, progress(registry, 0, 1));
			cache.get(b).quests.get(QUEST).fingerprint = "old";
			var manager = new TeamManager(store, registry, cache);
			UUID team = create(store, manager);
			commit(store, manager, manager.invite(a, b, 1_000));
			assertThrows(IllegalArgumentException.class, () -> manager.join(b, team, 1_001));
			assertNull(cache.get(b).teamId);
			assertEquals("old", cache.get(b).quests.get(QUEST).fingerprint);
			assertEquals(Set.of(a), manager.get(team).members);
			assertEquals(0L, manager.get(team).progress.quests.get(QUEST).counters.get("logs"));
		}
	}

	@Test
	void offlineDisbandCreatesIndependentSnapshotsAndKeepsPersonalLedger() throws Exception {
		Path data = root.resolve("data");
		var registry = registry();
		UUID team;
		try (var store = new JsonProgressStore(data)) {
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, progress(registry, 1, 0));
			cache.put(b, progress(registry, 0, 1));
			cache.get(b).claims.put(QUEST, "reserved");
			var manager = new TeamManager(store, registry, cache);
			team = create(store, manager);
			join(store, manager, team, b);
		}
		try (var store = new JsonProgressStore(data)) {
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			var manager = new TeamManager(store, registry, cache);
			commit(store, manager, manager.disband(a));
			assertTrue(manager.allTeams().isEmpty());
			assertNull(cache.get(a).teamId);
			assertNull(cache.get(b).teamId);
			assertEquals("reserved", cache.get(b).claims.get(QUEST));
			cache.get(b).quests.get(QUEST).counters.put("stone", 2L);
			assertEquals(1L, cache.get(a).quests.get(QUEST).counters.get("stone"));
			assertEquals(1L, store.loadTeams().get(team).progress.quests.get(QUEST).counters.get("stone"));
			assertTrue(store.loadTeams().get(team).disbanded);
		}
	}

	@Test
	void transferAndKickEnforceMembershipAndOwnerPermissions() throws Exception {
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			var manager = new TeamManager(store, registry(), new HashMap<>());
			UUID team = create(store, manager);
			join(store, manager, team, b);
			assertThrows(IllegalArgumentException.class, () -> manager.kick(b, a));
			assertThrows(IllegalArgumentException.class, () -> manager.transfer(a, c));
			assertThrows(IllegalArgumentException.class, () -> manager.kick(a, a));
			commit(store, manager, manager.transfer(a, b));
			assertThrows(IllegalArgumentException.class, () -> manager.disband(a));
			commit(store, manager, manager.kick(b, a));
			assertTrue(manager.teamOf(a).isEmpty());
			assertEquals(Set.of(b), manager.get(team).members);
		}
	}

	@Test
	void refusesMembershipMismatchAfterExternalFileRestore() throws Exception {
		Path data = root.resolve("data");
		var registry = registry();
		try (var store = new JsonProgressStore(data)) {
			var manager = new TeamManager(store, registry, new HashMap<>());
			create(store, manager);
			store.save(a, new PlayerProgress()).join();
		}
		try (var store = new JsonProgressStore(data)) {
			assertThrows(java.io.IOException.class, () -> new TeamManager(store, registry, new HashMap<>()));
		}
	}

	@Test
	void sequenceMergeKeepsOneAttemptAndUnionsOnlyHistory() throws Exception {
		registry();
		Path definition = root.resolve("definitions/work.json");
		Files.writeString(definition, Files.readString(definition).replace("\"schemaVersion\":1",
				"\"schemaVersion\":2,\"objectiveMode\":\"sequence\""));
		QuestRegistry registry = QuestRegistry.load(definition.getParent(), (kind, id) -> {
		});
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, progress(registry, 1, 0));
			cache.put(b, progress(registry, 2, 1));
			String attempt = UUID.randomUUID().toString();
			cache.get(a).quests.get(QUEST).attemptId = attempt;
			cache.get(b).quests.get(QUEST).attemptId = UUID.randomUUID().toString();
			var manager = new TeamManager(store, registry, cache);
			UUID team = create(store, manager);
			join(store, manager, team, b);
			assertEquals(Map.of("stone", 1L, "logs", 0L), manager.get(team).progress.quests.get(QUEST).counters);
			assertEquals(attempt, manager.get(team).progress.quests.get(QUEST).attemptId);
			assertFalse(manager.get(team).progress.quests.get(QUEST).completed);
		}
	}

	@Test
	void repeatHistoryDoesNotImportAnActivePersonalAttemptIntoANewCollectiveQuest() throws Exception {
		registry();
		Path definition = root.resolve("definitions/work.json");
		Files.writeString(definition, Files.readString(definition).replace("\"schemaVersion\":1", "\"schemaVersion\":2")
				.replace("\"once\"", "\"repeatable\""));
		QuestRegistry registry = QuestRegistry.load(definition.getParent(), (kind, id) -> {
		});
		try (var store = new JsonProgressStore(root.resolve("data"))) {
			Map<UUID, PlayerProgress> cache = new HashMap<>();
			cache.put(a, new PlayerProgress());
			cache.put(b, progress(registry, 1, 0));
			var previous = cache.get(b).quests.get(QUEST);
			previous.completed = true;
			previous.cycles = 3;
			previous.attemptId = UUID.randomUUID().toString();
			previous.cumulative.put("stone", 7L);
			cache.get(b).entitlements.put(QUEST + "|old", new PlayerProgress.RewardGrant(QUEST,
					List.of(new QuestDefinition.Reward("experience", null, 0, 10))));
			var manager = new TeamManager(store, registry, cache);
			UUID team = create(store, manager);
			join(store, manager, team, b);
			var shared = manager.get(team).progress.quests.get(QUEST);
			assertTrue(shared.completed);
			assertEquals(3, shared.cycles);
			assertEquals(7L, shared.cumulative.get("stone"));
			assertNull(shared.attemptId);
			assertTrue(shared.counters.isEmpty());
			assertTrue(manager.get(team).progress.entitlements.isEmpty());
			commit(store, manager, manager.leave(b));
			assertTrue(cache.get(b).entitlements.containsKey(QUEST + "|old"));
		}
	}
}
