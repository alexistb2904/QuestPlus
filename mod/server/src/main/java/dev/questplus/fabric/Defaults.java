package dev.questplus.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.questplus.core.QuestRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

public final class Defaults {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("questplus");

	private Defaults() {
	}

	public static void installWorld(Path defaults, Path world) throws IOException {
		installWorld(defaults, world, structure -> true);
	}

	/** {@code knownStructure} filters bundled NPC spawns for structures this Minecraft version lacks. */
	public static void installWorld(Path defaults, Path world, Predicate<String> knownStructure) throws IOException {
		if (!Files.exists(defaults))
			install(defaults, knownStructure);
		if (Files.exists(world))
			return;
		Files.createDirectories(world.getParent());
		Path stage = Files.createTempDirectory(world.getParent(), ".questplus-default-");
		try {
			try (var paths = Files.walk(defaults)) {
				for (Path source : paths.toList()) {
					if (Files.isSymbolicLink(source))
						throw new IOException("Symlink in default quest pack");
					Path target = stage.resolve(defaults.relativize(source));
					if (Files.isDirectory(source))
						Files.createDirectories(target);
					else
						Files.copy(source, target);
				}
			}
			QuestHubFiles.move(stage, world);
		} finally {
			if (Files.exists(stage))
				try (var paths = Files.walk(stage)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
						Files.delete(path);
				}
		}
	}

	public static void install(Path directory) throws IOException {
		install(directory, structure -> true);
	}

	public static void install(Path directory, Predicate<String> knownStructure) throws IOException {
		// Never overwrite existing worlds or intentionally deleted definitions.
		if (Files.exists(directory.resolve("config.json"))) {
			removeUnmodifiedLegacySamples(directory);
			return;
		}
		Files.createDirectories(directory);
		Files.createDirectories(directory.resolve("quests"));
		Files.createDirectories(directory.resolve("categories"));
		Files.createDirectories(directory.resolve("npcs"));

		// Install only resources listed in the manifest. Historical samples remain available for migration and tests.
		try (var manifest = resource("index.txt");
				var reader = new BufferedReader(new InputStreamReader(manifest, StandardCharsets.UTF_8))) {
			for (String file : reader.lines().filter(s -> !s.isBlank() && !s.equals("config.json")).toList()) {
				Path target = directory.resolve(file).normalize();
				if (!target.startsWith(directory.normalize()))
					throw new IOException("Invalid bundled resource path " + file);
				if (!Files.exists(target)) {
					Files.createDirectories(target.getParent());
					try (var input = resource(file)) {
						Files.copy(input, target);
					}
				}
			}
		}

		// Shared source of truth with Quest Studio. Never regenerate player data.
		installCatalogue(directory, "catalogue/challenge-catalogue.json");
		installCatalogue(directory, "catalogue/equipment-catalogue.json");
		var explorers = installCatalogue(directory, "catalogue/explorers-catalogue.json");
		Path config = directory.resolve("config.json");
		if (!Files.exists(config)) {
			JsonObject value;
			try (var input = resource("config.json");
					var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				value = QuestRegistry.parseStrict(reader).getAsJsonObject();
			}
			// The Explorers' quests are offered by these NPCs.
			value.add("npcs", availableNpcs(explorers.get("npcs"), knownStructure));
			Files.writeString(config, value.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		}
	}

	/** Bundled NPCs may also spawn in structures added by a newer Minecraft. Drop only those rules. */
	static JsonElement availableNpcs(JsonElement npcs, Predicate<String> knownStructure) {
		var copy = npcs.deepCopy();
		for (var npc : copy.getAsJsonArray()) {
			var spawns = npc.getAsJsonObject().getAsJsonArray("spawns");
			if (spawns != null)
				spawns.asList().removeIf(rule -> {
					var spawn = rule.getAsJsonObject();
					boolean missing = spawn.get("type").getAsString().equals("structure")
							&& !knownStructure.test(spawn.get("target").getAsString());
					if (missing)
						LOGGER.info("Quest Plus: default NPC {} skips structure {} absent from this Minecraft version",
								npc.getAsJsonObject().get("id").getAsString(), spawn.get("target").getAsString());
					return missing;
				});
		}
		return copy;
	}

	/**
	 * Remove only exact untouched copies of the four obsolete demo chapters. Any
	 * edited quest/category remains in place to avoid destroying authored content
	 * or leaving a reference to a missing category.
	 */
	private static void removeUnmodifiedLegacySamples(Path root) throws IOException {
		Map<String, List<String>> old = Map.of("survival", List.of("logs", "welcome", "composite_sequence"), "mining",
				List.of("stone", "stone_infinite", "stone_milestones", "iron_ore"), "combat",
				List.of("skeletons", "skeleton_repeat", "iron_and_zombie", "wither"), "exploration",
				List.of("diamond", "three_realms"));
		for (var group : old.entrySet()) {
			List<String> paths = new ArrayList<>();
			paths.add("categories/" + group.getKey() + ".json");
			for (String id : group.getValue())
				paths.add("quests/" + group.getKey() + "/" + id + ".json");
			boolean safe = true;
			for (String path : paths) {
				Path target = root.resolve(path);
				if (!Files.exists(target))
					continue;
				try (var bundled = resource(path)) {
					if (!Arrays.equals(Files.readAllBytes(target), bundled.readAllBytes()))
						safe = false;
				}
			}
			if (!safe)
				continue;
			// A custom quest may still depend on a sample quest. Keep the
			// complete chapter rather than invalidate user-authored links.
			Path quests = root.resolve("quests");
			if (Files.isDirectory(quests)) {
				try (var files = Files.walk(quests)) {
					for (Path other : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json"))
							.toList()) {
						String relative = quests.relativize(other).toString().replace('\\', '/');
						if (relative.startsWith(group.getKey() + "/") && !paths.contains("quests/" + relative)) {
							// A custom quest in this legacy category must keep its parent.
							safe = false;
							break;
						}
						if (!relative.startsWith(group.getKey() + "/")
								&& Files.readString(other).contains("questplus:" + group.getKey() + "/")) {
							safe = false;
							break;
						}
					}
				}
			}
			if (!safe)
				continue;
			for (String path : paths)
				Files.deleteIfExists(root.resolve(path));
		}
	}

	private static JsonObject installCatalogue(Path root, String bundled) throws IOException {
		JsonObject catalogue;
		try (var input = resource(bundled); var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
			catalogue = QuestRegistry.parseStrict(reader).getAsJsonObject();
		}
		for (JsonElement element : catalogue.getAsJsonArray("categories")) {
			JsonObject category = element.getAsJsonObject();
			String name = relativeName(category.get("id").getAsString());
			Path target = root.resolve("categories").resolve(name + ".json").normalize();
			writeNew(root, target, category);
		}
		for (JsonElement element : catalogue.getAsJsonArray("quests")) {
			JsonObject quest = element.getAsJsonObject();
			String name = relativeName(quest.get("id").getAsString());
			Path target = root.resolve("quests").resolve(name + ".json").normalize();
			writeNew(root, target, quest);
		}
		return catalogue;
	}

	private static String relativeName(String id) throws IOException {
		if (!id.matches("questplus:[a-z0-9_./-]+"))
			throw new IOException("Unsafe default quest ID " + id);
		return id.substring("questplus:".length());
	}

	private static void writeNew(Path root, Path target, JsonObject contents) throws IOException {
		if (!target.startsWith(root.normalize()))
			throw new IOException("Unsafe default quest path " + target);
		if (!Files.exists(target)) {
			Files.createDirectories(target.getParent());
			Files.writeString(target, contents.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		}
	}

	private static InputStream resource(String file) throws IOException {
		var input = Defaults.class.getResourceAsStream("/defaults/" + file);
		if (input == null)
			throw new IOException("Missing bundled default " + file);
		return input;
	}

	static JsonObject readObject(Path path) throws IOException {
		try {
			if (path.toString().endsWith(".json5"))
				return dev.questplus.core.QuestJson5.parse(Files.readString(path, StandardCharsets.UTF_8))
						.getAsJsonObject();
			try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				return QuestRegistry.parseStrict(reader).getAsJsonObject();
			}
		} catch (RuntimeException ex) {
			throw new IOException("Invalid JSON/JSON5 " + path + ": " + ex.getMessage(), ex);
		}
	}
}
