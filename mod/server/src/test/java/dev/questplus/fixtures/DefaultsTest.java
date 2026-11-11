package dev.questplus.fixtures;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.questplus.core.QuestDefinition;
import dev.questplus.core.QuestRegistry;
import dev.questplus.fabric.Defaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultsTest {
	@TempDir
	Path tempDir;

	@Test
	void bundledNpcsSkipOnlyStructuresMissingFromTheRunningMinecraft() throws Exception {
		Path root = tempDir.resolve("older-minecraft");
		Defaults.install(root, structure -> !structure.equals("#minecraft:abandoned_camp"));
		var config = JsonParser.parseString(Files.readString(root.resolve("config.json"))).getAsJsonObject();
		var oswin = config.getAsJsonArray("npcs").asList().stream().map(n -> n.getAsJsonObject())
				.filter(n -> n.get("id").getAsString().equals("oswin_wanderer")).findFirst().orElseThrow();
		var spawns = oswin.getAsJsonArray("spawns").toString();
		assertFalse(spawns.contains("abandoned_camp"), spawns);
		assertTrue(spawns.contains("\"random\""), "Other spawn rules stay: " + spawns);
		Path current = tempDir.resolve("current-minecraft");
		Defaults.install(current, structure -> true);
		assertTrue(Files.readString(current.resolve("config.json")).contains("#minecraft:abandoned_camp"));
	}

	@Test
	void newWorldCopiesGlobalDefaultsOnlyOnce() throws Exception {
		Path defaults = tempDir.resolve("config/default"), world = tempDir.resolve("world/questplus");
		Defaults.install(defaults);
		Files.writeString(defaults.resolve("marker.txt"), "original");
		Defaults.installWorld(defaults, world);
		assertEquals("original", Files.readString(world.resolve("marker.txt")));
		Files.writeString(defaults.resolve("marker.txt"), "changed");
		Defaults.installWorld(defaults, world);
		assertEquals("original", Files.readString(world.resolve("marker.txt")));
	}

	@Test
	void bundledDefaultsLoadAndReferencesResolve() throws Exception {
		var loader = DefaultsTest.class.getClassLoader();
		Set<String> categoryIds = new HashSet<>();
		List<String> manifest;
		try (var input = resource(loader, "defaults/index.txt")) {
			manifest = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
					.filter(line -> !line.isBlank()).toList();
		}
		assertEquals(List.of("config.json"), manifest, "Legacy sample categories must no longer ship by default");
		assertEquals(manifest.size(), new HashSet<>(manifest).size());

		for (String path : manifest) {
			try (var input = resource(loader, "defaults/" + path)) {
				byte[] bytes = input.readAllBytes();
				Path destination = tempDir.resolve(path);
				Files.createDirectories(destination.getParent());
				Files.write(destination, bytes);
				if (path.startsWith("categories/")) {
					JsonObject category = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
							.getAsJsonObject();
					categoryIds.add(category.get("id").getAsString());
					for (String field : List.of("title", "description"))
						assertFalse(category.getAsJsonObject(field).get("en_us").getAsString().isBlank());
				}
			}
		}

		assertTrue(categoryIds.isEmpty(), "Legacy demo chapters are not installed from index.txt");
		Defaults.install(tempDir.resolve("catalogue-world"));
		assertTrue(Files.isRegularFile(tempDir.resolve("catalogue-world/categories/homestead.json")));
		assertFalse(Files.exists(tempDir.resolve("catalogue-world/categories/combat.json")));
	}

	@Test
	void fullChallengeCatalogueIsInstalledAndParsesWithoutMissingPrerequisites() throws Exception {
		Path definitions = tempDir.resolve("full-catalogue");
		Defaults.install(definitions);
		QuestRegistry registry = QuestRegistry.load(definitions.resolve("quests"), (kind, id) -> {
		});
		assertEquals(219, registry.all().size(), "Only maintained challenge, equipment and explorer quests");

		Set<String> categories = new HashSet<>();
		try (var files = Files.walk(definitions.resolve("categories"))) {
			files.filter(Files::isRegularFile).forEach(path -> {
				try {
					categories.add(
							JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("id").getAsString());
				} catch (IOException failure) {
					throw new UncheckedIOException(failure);
				}
			});
		}
		assertEquals(17, categories.size());
		for (QuestDefinition quest : registry.all()) {
			assertTrue(categories.contains(quest.category()), "Missing category for " + quest.id());
			Set<String> references = new HashSet<>();
			if (quest.requires() != null)
				quest.requires().questReferences(references);
			for (String reference : references)
				assertTrue(registry.find(reference).isPresent(),
						"Unresolved prerequisite " + reference + " from " + quest.id());
		}
		var config = JsonParser.parseString(Files.readString(definitions.resolve("config.json"))).getAsJsonObject();
		Set<String> ids = new HashSet<>();
		registry.all().forEach(q -> ids.add(q.id()));
		var npcs = dev.questplus.core.QuestNpcs.parse(config.get("npcs"), ids, (kind, target) -> {
		});
		assertEquals(10, npcs.values().stream().mapToInt(npc -> npc.quests().size()).sum(),
				"Every explorer quest is offered by an NPC");
		assertTrue(registry.all().stream().filter(q -> q.category().equals("questplus:explorers"))
				.allMatch(q -> npcs.values().stream().anyMatch(npc -> npc.quests().contains(q.id()))));
		assertEquals("tiered", registry.get("questplus:quarries/stone_mountain").progression());
		assertEquals("repeatable", registry.get("questplus:contracts/skeleton_bounty").progression());
		assertEquals("infinite", registry.get("questplus:contracts/eternal_quarry").progression());
		assertTrue(registry.get("questplus:trials/three_giants").noDeath());
		assertEquals("equipment_check",
				registry.get("questplus:equipment/equipment_master").objectives().getFirst().event());
	}

	@Test
	void installingDefaultsPreservesExistingDefinitions() throws Exception {
		Path definitions = tempDir.resolve("world/questplus");
		Path existingQuest = definitions.resolve("quests/survival/logs.json");
		Files.createDirectories(existingQuest.getParent());
		byte[] userDefinition = "user-owned quest data".getBytes(StandardCharsets.UTF_8);
		Files.write(existingQuest, userDefinition);

		Defaults.install(definitions);

		assertArrayEquals(userDefinition, Files.readAllBytes(existingQuest));
		assertTrue(Files.isRegularFile(definitions.resolve("config.json")));
		assertTrue(Files.isRegularFile(definitions.resolve("quests/quarries/stone_mountain.json")));
		Files.delete(definitions.resolve("quests/quarries/stone_mountain.json"));
		Defaults.install(definitions);
		assertFalse(Files.exists(definitions.resolve("quests/quarries/stone_mountain.json")),
				"Deleted quest must stay deleted after initialization");
	}

	@Test
	void removesOnlyUnmodifiedLegacySamplesInExistingWorlds() throws Exception {
		Path root = tempDir.resolve("existing-world");
		Defaults.install(root);
		for (String p : List.of("categories/combat.json", "quests/combat/skeletons.json")) {
			Path target = root.resolve(p);
			Files.createDirectories(target.getParent());
			try (var source = resource(getClass().getClassLoader(), "defaults/" + p)) {
				Files.copy(source, target);
			}
		}
		Defaults.install(root);
		assertFalse(Files.exists(root.resolve("categories/combat.json")));
		assertFalse(Files.exists(root.resolve("quests/combat/skeletons.json")));

		Path changed = root.resolve("categories/exploration.json");
		Path quest = root.resolve("quests/exploration/diamond.json");
		Files.createDirectories(changed.getParent());
		Files.createDirectories(quest.getParent());
		Files.writeString(changed, "User-customized category");
		try (var source = resource(getClass().getClassLoader(), "defaults/quests/exploration/diamond.json")) {
			Files.copy(source, quest);
		}
		Defaults.install(root);
		assertTrue(Files.exists(changed), "Never delete user-edited category");
		assertTrue(Files.exists(quest), "Never orphan its quests");

		// An extra author-defined quest in an otherwise untouched legacy group
		// must prevent deleting the category on the next startup.
		Path combat = root.resolve("categories/combat.json");
		Path oldQuest = root.resolve("quests/combat/skeletons.json");
		Path custom = root.resolve("quests/combat/my_adventure.json");
		Files.createDirectories(combat.getParent());
		Files.createDirectories(custom.getParent());
		try (var source = resource(getClass().getClassLoader(), "defaults/categories/combat.json")) {
			Files.copy(source, combat);
		}
		try (var source = resource(getClass().getClassLoader(), "defaults/quests/combat/skeletons.json")) {
			Files.copy(source, oldQuest);
		}
		Files.writeString(custom, "{\"id\":\"questplus:combat/my_adventure\"}");
		Defaults.install(root);
		assertTrue(Files.exists(combat), "Custom quest requires preserving category");
		assertTrue(Files.exists(oldQuest), "Preserve entire legacy group if edited");
		assertTrue(Files.exists(custom));
	}

	private static InputStream resource(ClassLoader loader, String path) {
		InputStream input = loader.getResourceAsStream(path);
		assertNotNull(input, "Missing bundled resource: " + path);
		return input;
	}
}
