package dev.questplus.persistence;

import com.google.gson.JsonArray;
import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class InventoryRecoveryTest {
	@TempDir
	Path root;

	private JsonArray inventory() {
		var array = new JsonArray();
		for (int i = 0; i < 37; i++)
			array.add(new com.google.gson.JsonObject());
		return array;
	}

	private PlayerProgress progress(String value) {
		var p = new PlayerProgress();
		p.claims.put("questplus:test", value);
		return p;
	}

	private JsonProgressStore.SavedMarker marker(String value) {
		return new JsonProgressStore.SavedMarker() {
			public String read(UUID player) {
				return value;
			}

			public void verifyInventory(UUID player, JsonArray expected) throws java.io.IOException {
				if (!inventory().equals(expected))
					throw new java.io.IOException("Inventory mismatch");
			}
		};
	}

	@Test
	void experienceMustMatchBeforeCommitAndAfterRestart() throws Exception {
		UUID player = UUID.randomUUID(), old = UUID.randomUUID(), operation = UUID.randomUUID();
		var before = com.google.gson.JsonParser.parseString("{level:10,progress:0.5,total:150}").getAsJsonObject();
		var after = com.google.gson.JsonParser.parseString("{level:8,progress:0.5,total:150}").getAsJsonObject();
		class Evidence implements JsonProgressStore.SavedMarker {
			String saved = old.toString();
			com.google.gson.JsonObject xp = before.deepCopy();

			public String read(UUID id) {
				return saved;
			}

			public void verifyInventory(UUID id, JsonArray expected) {
			}

			public void verifyExperience(UUID id, com.google.gson.JsonObject expected) throws java.io.IOException {
				if (!xp.equals(expected))
					throw new java.io.IOException("XP mismatch");
			}
		}
		var evidence = new Evidence();
		try (var store = JsonProgressStore.withSavedMarker(root, evidence)) {
			store.prepareInventory(operation, player, old.toString(), inventory(), inventory(),
					Map.of(player, progress("delivered")), Map.of(), before, after).join();
			evidence.saved = operation.toString();
			assertThrows(CompletionException.class, () -> store.commitInventory(operation).join());
		}
		assertThrows(java.io.IOException.class, () -> JsonProgressStore.withSavedMarker(root, evidence));
		evidence.xp = after.deepCopy();
		try (var recovered = JsonProgressStore.withSavedMarker(root, evidence)) {
			assertEquals("delivered", recovered.load(player).claims.get("questplus:test"));
		}
	}

	@Test
	void markerOnlyEvidenceCannotAuthorizeACommitOrRecovery() throws Exception {
		UUID player = UUID.randomUUID(), old = UUID.randomUUID(), operation = UUID.randomUUID();
		try (var store = JsonProgressStore.withSavedMarker(root, id -> operation.toString())) {
			store.prepareInventory(operation, player, old.toString(), inventory(), inventory(),
					Map.of(player, progress("delivered")), Map.of()).join();
			assertThrows(CompletionException.class, () -> store.commitInventory(operation).join());
		}
		assertThrows(java.io.IOException.class,
				() -> JsonProgressStore.withSavedMarker(root, id -> operation.toString()));
	}

	@Test
	void matchingMarkerWithMismatchedInventoryFreezesCommitAndRecovery() throws Exception {
		UUID player = UUID.randomUUID(), baseline = UUID.randomUUID(), operation = UUID.randomUUID();
		var inventory = inventory();
		var componentStack = com.google.gson.JsonParser.parseString(
				"{\"id\":\"minecraft:stone\",\"count\":1,\"components\":{\"minecraft:custom_name\":{\"text\":\"Preserve\"}}}");
		inventory.set(0, componentStack);
		class Evidence implements JsonProgressStore.SavedMarker {
			String marker = baseline.toString();
			JsonArray actual = inventory.deepCopy();

			public String read(UUID id) {
				return marker;
			}

			public void verifyInventory(UUID id, JsonArray expected) throws java.io.IOException {
				if (!actual.equals(expected))
					throw new java.io.IOException("Component mismatch");
			}
		}
		var evidence = new Evidence();
		try (var store = JsonProgressStore.withSavedMarker(root, evidence)) {
			store.prepareInventory(operation, player, baseline.toString(), inventory, inventory,
					Map.of(player, progress("delivered")), Map.of()).join();
			evidence.marker = operation.toString();
			evidence.actual = inventory.deepCopy();
			evidence.actual.get(0).getAsJsonObject().remove("components");
			assertThrows(CompletionException.class, () -> store.commitInventory(operation).join());
		}
		assertThrows(java.io.IOException.class, () -> JsonProgressStore.withSavedMarker(root, evidence));
		assertTrue(Files.exists(root.resolve("transactions/inventory.json")));
		evidence.marker = baseline.toString();
		assertThrows(java.io.IOException.class, () -> JsonProgressStore.withSavedMarker(root, evidence));
		evidence.marker = operation.toString();
		evidence.actual = inventory.deepCopy();
		try (var store = JsonProgressStore.withSavedMarker(root, evidence)) {
			assertEquals("delivered", store.load(player).claims.get("questplus:test"));
		}
	}

	@Test
	void firstImageInterruptedBeforeRenameRecoversItsTemporaryFile() throws Exception {
		UUID player = UUID.randomUUID(), old = UUID.randomUUID(), operation = UUID.randomUUID();
		try (var store = JsonProgressStore.withSavedMarker(root, marker(old.toString()))) {
			store.prepareInventory(operation, player, old.toString(), inventory(), inventory(),
					Map.of(player, progress("delivered")), Map.of()).join();
		}
		Path temporary = root.resolve("players/" + player + ".json.tmp");
		Files.createDirectories(temporary.getParent());
		Files.writeString(temporary, "partially written image");
		try (var recovered = JsonProgressStore.withSavedMarker(root, marker(operation.toString()))) {
			assertEquals("delivered", recovered.load(player).claims.get("questplus:test"));
			assertFalse(Files.exists(temporary));
		}
	}

	@Test
	void markerDecidesCommitOrAbort() throws Exception {
		for (boolean consumed : List.of(false, true)) {
			Path path = root.resolve(Boolean.toString(consumed));
			UUID player = UUID.randomUUID(), old = UUID.randomUUID(), operation = UUID.randomUUID();
			try (var store = JsonProgressStore.withSavedMarker(path, marker(old.toString()))) {
				store.save(player, progress("reserved")).join();
				store.prepareInventory(operation, player, old.toString(), inventory(), inventory(),
						Map.of(player, progress("delivered")), Map.of()).join();
				assertThrows(CompletionException.class, () -> store.commitInventory(operation).join());
			}
			try (var store = JsonProgressStore.withSavedMarker(path, marker((consumed ? operation : old).toString()))) {
				assertEquals(consumed ? "delivered" : "reserved", store.load(player).claims.get("questplus:test"));
			}
		}
	}

	@Test
	void unknownMarkerAndChangedImageFreezeRecovery() throws Exception {
		UUID player = UUID.randomUUID(), old = UUID.randomUUID(), operation = UUID.randomUUID();
		try (var store = JsonProgressStore.withSavedMarker(root, marker(old.toString()))) {
			store.save(player, progress("reserved")).join();
			store.prepareInventory(operation, player, old.toString(), inventory(), inventory(),
					Map.of(player, progress("delivered")), Map.of()).join();
		}
		assertThrows(java.io.IOException.class,
				() -> JsonProgressStore.withSavedMarker(root, marker(UUID.randomUUID().toString())));
		Files.writeString(root.resolve("players/" + player + ".json"), "{}");
		assertThrows(java.io.IOException.class,
				() -> JsonProgressStore.withSavedMarker(root, marker(operation.toString())));
		assertTrue(Files.exists(root.resolve("transactions/inventory.json")));
	}

	@Test
	void resolutionWritesAuditWithClaim() throws Exception {
		UUID player = UUID.randomUUID(), auditId = UUID.randomUUID();
		try (var store = new JsonProgressStore(root)) {
			store.save(player, progress("reserved")).join();
			var audit = new JsonProgressStore.ClaimAudit(auditId, player, "questplus:test", "operator", "discard",
					"reviewed", 1);
			store.resolveClaim(player, progress("delivered"), audit).join();
			assertEquals("delivered", store.load(player).claims.get("questplus:test"));
			assertTrue(Files.readString(root.resolve("audit/" + auditId + ".json")).contains("discard"));
			assertThrows(CompletionException.class,
					() -> store.resolveClaim(player, progress("delivered"), audit).join());
		}
	}

	@Test
	void consumedRecoveryFinishesInterruptedPlayerAndTeamWrites() throws Exception {
		UUID player = UUID.randomUUID(), teamId = UUID.randomUUID(), old = UUID.randomUUID(),
				operation = UUID.randomUUID();
		var team = new dev.questplus.team.TeamData();
		team.id = teamId;
		team.owner = player;
		team.name = "test";
		team.members.add(player);
		var after = progress("delivered");
		after.teamId = teamId;
		try (var store = new JsonProgressStore(root, point -> {
			if (point.startsWith("inventory:file:"))
				throw new java.io.IOException("crash");
		}, marker(operation.toString()))) {
			store.save(player, progress("reserved")).join();
			store.prepareInventory(operation, player, old.toString(), inventory(), inventory(), Map.of(player, after),
					Map.of(teamId, team)).join();
			assertThrows(CompletionException.class, () -> store.commitInventory(operation).join());
		}
		try (var store = JsonProgressStore.withSavedMarker(root, marker(operation.toString()))) {
			assertEquals(teamId, store.load(player).teamId);
			assertEquals(player, store.loadTeams().get(teamId).owner);
			assertFalse(Files.exists(root.resolve("transactions/inventory.json")));
		}
	}
}
