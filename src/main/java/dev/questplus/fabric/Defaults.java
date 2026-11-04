package dev.questplus.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.questplus.core.QuestRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Defaults {
    private Defaults() {}

    public static void install(Path directory) throws IOException {
        // Never overwrite existing worlds or intentionally deleted definitions.
        if (Files.exists(directory.resolve("config.json"))) return;
        Files.createDirectories(directory);
        Files.createDirectories(directory.resolve("quests"));
        Files.createDirectories(directory.resolve("categories"));
        Files.createDirectories(directory.resolve("npcs"));

        // Keep historical sample IDs stable for saves and existing GameTests.
        // The full Vanilla+ challenge catalogue is installed alongside them.
        try (var manifest = resource("index.txt");
             var reader = new BufferedReader(new InputStreamReader(manifest, StandardCharsets.UTF_8))) {
            for (String file : reader.lines().filter(s -> !s.isBlank() && !s.equals("config.json")).toList()) {
                Path target = directory.resolve(file).normalize();
                if (!target.startsWith(directory.normalize())) throw new IOException("Invalid bundled resource path " + file);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    try (var input = resource(file)) { Files.copy(input, target); }
                }
            }
        }

        // Shared source of truth with Quest Studio. Never regenerate player data.
        installCatalogue(directory,"catalogue/challenge-catalogue.json");
        installCatalogue(directory,"catalogue/equipment-catalogue.json");
        Path config=directory.resolve("config.json");
        if (!Files.exists(config)) try (var input=resource("config.json")) { Files.copy(input,config); }
    }

    private static void installCatalogue(Path root,String bundled) throws IOException {
        JsonObject catalogue;
        try (var input=resource(bundled); var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            catalogue=QuestRegistry.parseStrict(reader).getAsJsonObject();
        }
        for(JsonElement element:catalogue.getAsJsonArray("categories")) {
            JsonObject category=element.getAsJsonObject();
            String name=relativeName(category.get("id").getAsString());
            Path target=root.resolve("categories").resolve(name+".json").normalize();
            writeNew(root,target,category);
        }
        for(JsonElement element:catalogue.getAsJsonArray("quests")) {
            JsonObject quest=element.getAsJsonObject();
            String name=relativeName(quest.get("id").getAsString());
            Path target=root.resolve("quests").resolve(name+".json").normalize();
            writeNew(root,target,quest);
        }
    }

    private static String relativeName(String id) throws IOException {
        if(!id.matches("questplus:[a-z0-9_./-]+"))throw new IOException("Unsafe default quest ID "+id);
        return id.substring("questplus:".length());
    }

    private static void writeNew(Path root,Path target,JsonObject contents) throws IOException {
        if(!target.startsWith(root.normalize()))throw new IOException("Unsafe default quest path "+target);
        if(!Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.writeString(target,contents.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }
    }

    private static InputStream resource(String file) throws IOException {
        var input = Defaults.class.getResourceAsStream("/defaults/" + file);
        if (input == null) throw new IOException("Missing bundled default " + file);
        return input;
    }

    static JsonObject readObject(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return QuestRegistry.parseStrict(reader).getAsJsonObject();
        } catch (RuntimeException ex) { throw new IOException("Invalid JSON " + path + ": " + ex.getMessage(), ex); }
    }
}
