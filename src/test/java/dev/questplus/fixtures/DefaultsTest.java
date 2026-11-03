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
    @TempDir Path tempDir;

    @Test void bundledDefaultsLoadAndReferencesResolve() throws Exception {
        var loader = DefaultsTest.class.getClassLoader();
        Set<String> categoryIds = new HashSet<>();
        List<String> manifest;
        try (var input = resource(loader, "defaults/index.txt")) {
            manifest = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                .lines().filter(line -> !line.isBlank()).toList();
        }
        assertEquals(18, manifest.size());
        assertEquals(manifest.size(), new HashSet<>(manifest).size());

        for (String path : manifest) {
            try (var input = resource(loader, "defaults/" + path)) {
                byte[] bytes = input.readAllBytes();
                Path destination = tempDir.resolve(path);
                Files.createDirectories(destination.getParent());
                Files.write(destination, bytes);
                if (path.startsWith("categories/")) {
                    JsonObject category = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                    categoryIds.add(category.get("id").getAsString());
                    for (String field : List.of("title", "description"))
                        assertFalse(category.getAsJsonObject(field).get("en_us").getAsString().isBlank());
                }
            }
        }

        assertEquals(4, categoryIds.size());
        QuestRegistry registry = QuestRegistry.load(tempDir.resolve("quests"), (kind, id) -> {});
        assertEquals(13, registry.all().size());
        for (QuestDefinition quest : registry.all()) {
            assertTrue(categoryIds.contains(quest.category()), "Unknown category: " + quest.category());
            Set<String> references = new HashSet<>();
            if (quest.requires() != null) quest.requires().questReferences(references);
            for (String reference : references)
                assertTrue(registry.find(reference).isPresent(), "Unknown quest prerequisite: " + reference);
        }
    }

    @Test void installingDefaultsPreservesExistingDefinitions() throws Exception {
        Path definitions = tempDir.resolve("world/questplus");
        Path existingQuest = definitions.resolve("quests/survival/logs.json");
        Files.createDirectories(existingQuest.getParent());
        byte[] userDefinition = "user-owned quest data".getBytes(StandardCharsets.UTF_8);
        Files.write(existingQuest, userDefinition);

        Defaults.install(definitions);

        assertArrayEquals(userDefinition, Files.readAllBytes(existingQuest));
        assertTrue(Files.isRegularFile(definitions.resolve("config.json")));
        assertTrue(Files.isRegularFile(definitions.resolve("quests/mining/stone.json")));
        Files.delete(definitions.resolve("quests/mining/stone.json"));
        Defaults.install(definitions);
        assertFalse(Files.exists(definitions.resolve("quests/mining/stone.json")),"Deleted quest must stay deleted after initialization");
    }

    private static InputStream resource(ClassLoader loader, String path) {
        InputStream input = loader.getResourceAsStream(path);
        assertNotNull(input, "Missing bundled resource: " + path);
        return input;
    }
}
