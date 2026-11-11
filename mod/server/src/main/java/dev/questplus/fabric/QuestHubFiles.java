package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.QuestRegistry;
import dev.questplus.core.QuestJson5;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Server-thread only, rollback-safe import/export for Quest Studio documents.
 * Player progress lives under world/data/questplus and is NEVER replaced.
 */
final class QuestHubFiles {
	private QuestHubFiles() {
	}

	private static final int MAX_FILES = 2000, MAX_TOTAL = 12 * 1024 * 1024;

	static boolean autoSyncEnabled(Path config) throws IOException {
		if (!Files.exists(config)) {
			Files.createDirectories(config.getParent());
			Files.writeString(config, "{\n  \"autoSyncDefaults\": false\n}\n", StandardOpenOption.CREATE_NEW);
		}
		JsonObject settings = Defaults.readObject(config);
		if (!settings.keySet().equals(Set.of("autoSyncDefaults")) || !settings.get("autoSyncDefaults").isJsonPrimitive()
				|| !settings.getAsJsonPrimitive("autoSyncDefaults").isBoolean())
			throw new IOException("Invalid autoSyncDefaults setting in " + config);
		return settings.get("autoSyncDefaults").getAsBoolean();
	}

	static void syncDefaults(Path defaults, Path world, QuestRuntime runtime) throws IOException {
		JsonArray files = snapshot(defaults);
		String signature;
		try {
			signature = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
					.digest(files.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
		Path marker = world.resolve("data/questplus/default-pack.sha256");
		if (Files.exists(marker) && Files.readString(marker).equals(signature))
			return;
		if (!files.equals(snapshot(world.resolve("questplus"))))
			applyTo(world.resolve("questplus"), world.resolve("questplus-hub-backups"), files, runtime, true, false);
		Files.createDirectories(marker.getParent());
		Files.writeString(marker, signature);
	}

	private static boolean allowed(String name) {
		return name.equals("config.json") || name.equals("queststudio-compatibility.json")
				|| (name.matches("quests/[a-z0-9_./-]+\\.json5?") || name.matches("categories/[a-z0-9_./-]+\\.json5?"))
						&& !Arrays.asList(name.split("/")).contains("..");
	}

	static JsonArray snapshot(Path definitions) throws IOException {
		JsonArray files = new JsonArray();
		for (String folder : List.of("config.json", "queststudio-compatibility.json", "quests", "categories")) {
			Path root = definitions.resolve(folder);
			if (!Files.exists(root))
				continue;
			try (var walk = Files.isDirectory(root) ? Files.walk(root) : java.util.stream.Stream.of(root)) {
				for (Path entry : walk.filter(Files::isRegularFile).sorted().toList()) {
					if (Files.isSymbolicLink(entry))
						throw new IOException("Symlink not allowed in quest definitions");
					String name = definitions.relativize(entry).toString().replace('\\', '/');
					if (!allowed(name))
						continue;
					JsonObject doc = new JsonObject();
					doc.addProperty("name", name);
					doc.addProperty("content", Files.readString(entry, StandardCharsets.UTF_8));
					files.add(doc);
				}
			}
		}
		validate(files);
		return files;
	}

	static void validate(JsonArray documents) throws IOException {
		if (documents == null || documents.size() > MAX_FILES)
			throw new IOException("Invalid file count");
		int size = 0;
		boolean hasConfig = false, hasQuest = false, hasCategory = false;
		Set<String> seen = new HashSet<>();
		for (var element : documents) {
			if (!element.isJsonObject())
				throw new IOException("Invalid document");
			JsonObject doc = element.getAsJsonObject();
			if (!doc.has("name") || !doc.has("content"))
				throw new IOException("Missing document fields");
			String name = doc.get("name").getAsString(), content = doc.get("content").getAsString();
			if (!allowed(name) || !seen.add(name))
				throw new IOException("Unsafe or duplicate file: " + name);
			size += content.getBytes(StandardCharsets.UTF_8).length;
			if (size > MAX_TOTAL)
				throw new IOException("Quest pack exceeds 12 MiB");
			if (name.equals("config.json"))
				hasConfig = true;
			if (name.startsWith("quests/"))
				hasQuest = true;
			if (name.startsWith("categories/"))
				hasCategory = true;
			try {
				if (name.endsWith(".json5"))
					QuestJson5.parse(content);
				else
					QuestRegistry.parseStrict(new StringReader(content));
			} catch (RuntimeException ex) {
				throw new IOException("Invalid JSON in " + name, ex);
			}
		}
		if (!hasConfig || !hasQuest || !hasCategory)
			throw new IOException("Pack requires config, categories and quests");
	}

	static void apply(Path world, JsonArray documents, QuestRuntime runtime) throws IOException {
		applyTo(world.resolve("questplus"), world.resolve("questplus-hub-backups"), documents, runtime, true, true);
	}

	static void applyTo(Path definitions, Path backups, JsonArray documents, QuestRuntime runtime, boolean activate,
			boolean remote) throws IOException {
		definitions = definitions.toAbsolutePath().normalize();
		backups = backups.toAbsolutePath().normalize();
		validate(documents);
		if (remote)
			rejectRemoteCommands(documents);
		Path parent = definitions.getParent();
		Files.createDirectories(backups);
		Path stage = Files.createTempDirectory(parent, ".questplus-hub-stage-");
		boolean moved = false;
		Path backup = backups.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID());
		try {
			copyTree(definitions, stage);
			deleteTree(stage.resolve("quests"));
			deleteTree(stage.resolve("categories"));
			Files.deleteIfExists(stage.resolve("queststudio-compatibility.json"));
			Files.createDirectories(stage.resolve("quests"));
			Files.createDirectories(stage.resolve("categories"));
			for (var el : documents) {
				JsonObject f = el.getAsJsonObject();
				String name = f.get("name").getAsString();
				Path target = stage.resolve(name).normalize();
				if (!target.startsWith(stage))
					throw new IOException("Invalid destination");
				Files.createDirectories(target.getParent());
				Files.writeString(target, f.get("content").getAsString(), StandardCharsets.UTF_8,
						StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			}
			runtime.validateDefinitions(stage);
			// Save a complete backup before activating the new definitions.
			move(definitions, backup);
			moved = true;
			try {
				move(stage, definitions);
				if (activate)
					runtime.reload();
			} catch (Exception rejected) {
				Path bad = backups.resolve("rejected-" + UUID.randomUUID());
				if (Files.exists(definitions))
					move(definitions, bad);
				move(backup, definitions);
				if (activate)
					try {
						runtime.reload();
					} catch (Exception restore) {
						rejected.addSuppressed(restore);
					}
				throw new IOException(
						"Publication rejected and previous definitions restored: " + rejected.getMessage(), rejected);
			}
		} finally {
			if (Files.exists(stage))
				deleteTree(stage);
			if (!moved && Files.exists(backup) && !Files.exists(definitions))
				move(backup, definitions);
		}
	}

	static void rejectRemoteCommands(JsonArray documents) throws IOException {
		for (var document : documents) {
			var file = document.getAsJsonObject();
			String content = file.get("content").getAsString();
			JsonElement value = file.get("name").getAsString().endsWith(".json5") ? QuestJson5.parse(content)
					: QuestRegistry.parseStrict(new StringReader(content));
			if (hasCommand(value))
				throw new IOException(
						"Command rewards can only be installed from trusted local files, not published from a browser or pack link");
			if (file.get("name").getAsString().equals("config.json")
					&& value.getAsJsonObject().has("enableCommandRewards")
					&& value.getAsJsonObject().get("enableCommandRewards").getAsBoolean())
				throw new IOException("Remote packs cannot enable server command rewards");
		}
	}

	private static boolean hasCommand(JsonElement value) {
		if (value.isJsonArray()) {
			for (var child : value.getAsJsonArray())
				if (hasCommand(child))
					return true;
		}
		if (value.isJsonObject()) {
			var object = value.getAsJsonObject();
			if (object.has("type") && object.get("type").isJsonPrimitive()
					&& object.get("type").getAsString().equals("questplus:command"))
				return true;
			for (var child : object.entrySet())
				if (hasCommand(child.getValue()))
					return true;
		}
		return false;
	}

	static void rollback(Path world, QuestRuntime runtime) throws IOException {
		Path root = world.resolve("questplus-hub-backups");
		if (!Files.isDirectory(root))
			throw new IOException("No published snapshots to restore");
		Path latest;
		try (var files = Files.list(root)) {
			latest = files.filter(Files::isDirectory)
					.filter(p -> p.getFileName().toString().matches("[0-9]+-[a-f0-9-]+"))
					.max(Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
		}
		if (latest == null)
			throw new IOException("No valid backup found");
		apply(world, snapshot(latest), runtime);
	}

	static void move(Path from, Path to) throws IOException {
		for (int attempt = 0; ; attempt++) {
			try {
				try {
					Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
				} catch (AtomicMoveNotSupportedException e) {
					Files.move(from, to);
				}
				return;
			} catch (AccessDeniedException locked) {
				// Windows scanners briefly hold fresh pack files. Never replace a failed move with a copy.
				if (attempt >= 9)
					throw locked;
				try {
					Thread.sleep(50);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while waiting for quest pack filesystem lock", interrupted);
				}
			}
		}
	}

	private static void copyTree(Path from, Path to) throws IOException {
		if (!Files.exists(from))
			throw new IOException("Missing definitions");
		try (var walk = Files.walk(from)) {
			for (Path entry : walk.toList()) {
				if (Files.isSymbolicLink(entry))
					throw new IOException("Symlinks are not allowed");
				Path dest = to.resolve(from.relativize(entry));
				if (Files.isDirectory(entry))
					Files.createDirectories(dest);
				else
					Files.copy(entry, dest);
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root))
			return;
		try (var stream = Files.walk(root)) {
			for (Path entry : stream.sorted(Comparator.reverseOrder()).toList())
				Files.delete(entry);
		}
	}
}
