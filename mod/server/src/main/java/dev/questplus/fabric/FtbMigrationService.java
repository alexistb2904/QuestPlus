package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.*;
import dev.questplus.persistence.JsonProgressStore;
import dev.questplus.team.*;
import net.minecraft.server.MinecraftServer;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Private world files and OP commands only. No player or reward data enters the
 * web protocol.
 */
final class FtbMigrationService {
	private static final Gson JSON = new GsonBuilder().serializeNulls().create();

	private record Pending(UUID operation, long expires, long generation, Path source, String sourceHash,
			TeamManager.Change before, TeamManager.Change after, int quests) {
	}

	private final QuestRuntime runtime;
	private final MinecraftServer server;
	private final Path world;
	private final Map<String, Pending> pending = new HashMap<>();

	FtbMigrationService(QuestRuntime runtime, MinecraftServer server, Path world) {
		this.runtime = runtime;
		this.server = server;
		this.world = world;
	}

	String preview(String operator, UUID sourceId, UUID target) throws IOException {
		assertThread();
		pending.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
		if (pending.size() >= 64 && !pending.containsKey(operator))
			throw new IllegalStateException("Too many pending migration reviews");
		Path source = source(sourceId);
		String content = read(source);
		var data = QuestJson5.parse(content).getAsJsonObject();
		Path metadata = world.resolve("questplus/queststudio-compatibility.json");
		var authored = QuestRegistry.parseStrict(new StringReader(read(metadata))).getAsJsonObject();
		if (!authored.has("ftbMappings"))
			throw new IllegalArgumentException(
					"Import the FTB definitions in Studio first; their source-ID mappings are required");
		var mappings = authored.getAsJsonArray("ftbMappings");
		var targetPlayer = runtime.player(target);
		UUID teamId = runtime.progressScope(target) == ProgressScope.TEAM ? targetPlayer.teamId : null;
		var members = teamId == null ? Set.of(target) : Set.copyOf(runtime.teams().get(teamId).members);
		Map<UUID, PlayerProgress> beforePlayers = new HashMap<>(), afterPlayers = new HashMap<>();
		Map<UUID, TeamData> beforeTeams = new HashMap<>(), afterTeams = new HashMap<>();
		var rolls = new HashMap<String, List<QuestDefinition.Reward>>();
		int quests = 0;
		PlayerProgress collective = null;
		var sharedClaims = new HashMap<String, String>();
		for (UUID member : members) {
			if (runtime.isBusy(member))
				throw new IllegalStateException("A player/team write is still saving");
			var personal = runtime.player(member);
			beforePlayers.put(member, JsonProgressStore.detachedCopy(personal));
			var result = FtbProgressMigration.prepare(runtime.registry(), data, mappings, runtime.state(member),
					personal, member, teamId, rolls, System.currentTimeMillis());
			afterPlayers.put(member, result.personal());
			collective = result.state();
			sharedClaims.putAll(result.sharedClaims());
			quests = result.quests();
		}
		if (quests == 0)
			throw new IllegalArgumentException("No mapped FTB progress found in this source save");
		if (teamId != null) {
			var team = runtime.teams().get(teamId);
			beforeTeams.put(teamId, copy(team));
			var after = copy(team);
			after.progress = collective;
			after.rewardClaims.putAll(sharedClaims);
			after.validate();
			afterTeams.put(teamId, after);
		}
		var plan = new Pending(UUID.randomUUID(), System.currentTimeMillis() + 120000, runtime.catalogGeneration(),
				source, hashText(content), new TeamManager.Change(beforePlayers, beforeTeams),
				new TeamManager.Change(afterPlayers, afterTeams), quests);
		pending.put(operator, plan);
		return "FTB migration " + plan.operation() + ": " + quests + " quests for " + members.size()
				+ " player(s). Current server fingerprints and source reward claims were checked. Confirm within two minutes with /questplus migrate-ftb confirm; source files are preserved.";
	}

	CompletableFuture<Void> confirm(String operator) throws IOException {
		assertThread();
		var plan = pending.get(operator);
		if (plan == null || plan.expires() < System.currentTimeMillis())
			throw new IllegalArgumentException("Migration review expired; preview again");
		if (plan.generation() != runtime.catalogGeneration()
				|| !plan.sourceHash().equals(hashText(read(plan.source()))))
			throw new IllegalStateException("Definitions/source save changed; preview again");
		verifyCurrent(plan.before());
		var backup = new JsonObject();
		backup.addProperty("schemaVersion", 1);
		backup.addProperty("operator", operator);
		backup.addProperty("operation", plan.operation().toString());
		backup.addProperty("sourceHash", plan.sourceHash());
		backup.add("before", JSON.toJsonTree(plan.before()));
		backup.add("after", JSON.toJsonTree(plan.after()));
		writeBackup(plan.operation(), backup);
		pending.remove(operator);
		return runtime.changeTeam(ignored -> plan.after());
	}

	CompletableFuture<Void> rollback(UUID operation) throws IOException {
		assertThread();
		Path path = world.resolve("data/questplus/migrations/" + operation + ".json");
		var backup = QuestRegistry.parseStrict(new StringReader(read(path))).getAsJsonObject();
		if (backup.get("schemaVersion").getAsInt() != 1
				|| !operation.toString().equals(backup.get("operation").getAsString()))
			throw new IllegalArgumentException("Invalid migration backup");
		var after = JSON.fromJson(backup.get("after"), TeamManager.Change.class);
		var before = JSON.fromJson(backup.get("before"), TeamManager.Change.class);
		verifyCurrent(after);
		before.players().values().forEach(PlayerProgress::validate);
		before.teams().values().forEach(TeamData::validate);
		return runtime.changeTeam(ignored -> before);
	}

	private void verifyCurrent(TeamManager.Change expected) throws IOException {
		for (var entry : expected.players().entrySet()) {
			if (runtime.isBusy(entry.getKey()))
				throw new IllegalStateException("A player/team write is still saving");
			if (!hash(JSON.toJsonTree(entry.getValue())).equals(hash(JSON.toJsonTree(runtime.player(entry.getKey())))))
				throw new IllegalStateException(
						"Player progress/rewards changed since review; rollback cannot undo delivered items or newer progress");
		}
		for (var entry : expected.teams().entrySet())
			if (!hash(JSON.toJsonTree(entry.getValue()))
					.equals(hash(JSON.toJsonTree(runtime.teams().rewardScope(entry.getKey())))))
				throw new IllegalStateException("Team progress or membership changed since review");
	}

	private Path source(UUID id) throws IOException {
		for (String name : List.of(id.toString(), id.toString().replace("-", "")))
			for (String suffix : List.of(".json5", ".json")) {
				Path file = world.resolve("ftbquests/" + name + suffix);
				if (Files.isRegularFile(file) && !Files.isSymbolicLink(file))
					return file;
			}
		throw new IOException(
				"Place the original FTB JSON5 team save in world/ftbquests/<source-uuid>.json5 (legacy SNBT saves must first be converted locally)");
	}

	private static String read(Path file) throws IOException {
		if (Files.isSymbolicLink(file) || Files.size(file) > 64 * 1024 * 1024)
			throw new IOException("Migration file is unsafe or too large");
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private void writeBackup(UUID id, JsonObject value) throws IOException {
		Path folder = world.resolve("data/questplus/migrations");
		Files.createDirectories(folder);
		byte[] bytes = JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 64 * 1024 * 1024)
			throw new IOException("Migration backup exceeds 64 MiB");
		Path temporary = folder.resolve(id + ".tmp"), file = folder.resolve(id + ".json");
		try (var channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			var buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining())
				channel.write(buffer);
			channel.force(true);
		}
		Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
	}

	private static TeamData copy(TeamData team) {
		return JSON.fromJson(JSON.toJson(team), TeamData.class);
	}

	private static String hash(JsonElement value) {
		return hashText(canonical(value));
	}

	private static String canonical(JsonElement value) {
		if (value.isJsonObject()) {
			var keys = new TreeMap<String, String>();
			value.getAsJsonObject().entrySet().forEach(e -> keys.put(e.getKey(), canonical(e.getValue())));
			return keys.entrySet().stream().map(e -> JSON.toJson(e.getKey()) + ":" + e.getValue())
					.collect(java.util.stream.Collectors.joining(",", "{", "}"));
		}
		if (value.isJsonArray())
			return value.getAsJsonArray().asList().stream().map(FtbMigrationService::canonical)
					.collect(java.util.stream.Collectors.joining(",", "[", "]"));
		return value.toString();
	}

	private static String hashText(String value) {
		try {
			return HexFormat.of()
					.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private void assertThread() {
		if (!server.isSameThread())
			throw new IllegalStateException("Migration requires the server thread");
	}
}
