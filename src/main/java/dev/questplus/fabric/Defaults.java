package dev.questplus.fabric;

import com.google.gson.JsonObject;
import dev.questplus.core.QuestRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

public final class Defaults {
    private Defaults() {}

    public static void install(Path directory) throws IOException {
        if (Files.exists(directory.resolve("config.json"))) return;
        Files.createDirectories(directory);
        for (String folder : new String[]{"quests/survival", "quests/mining", "quests/combat", "quests/exploration", "categories", "npcs"})
            Files.createDirectories(directory.resolve(folder));
        try (var manifest = resource("index.txt"); var reader = new BufferedReader(new InputStreamReader(manifest, StandardCharsets.UTF_8))) {
            // Install config last: an interrupted first installation can resume without resurrecting deleted quests later.
            for (String file : reader.lines().filter(s -> !s.isBlank()).sorted(Comparator.comparing(s -> s.equals("config.json"))).toList()) {
                Path target = directory.resolve(file).normalize();
                if (!target.startsWith(directory.normalize())) throw new IOException("Invalid bundled resource path " + file);
                if (!Files.exists(target)) {
                    Files.createDirectories(target.getParent());
                    try (var input = resource(file)) { Files.copy(input, target); }
                }
            }
        }
    }

    private static InputStream resource(String file) throws IOException {
        var input = Defaults.class.getResourceAsStream("/defaults/" + file);
        if (input == null) throw new IOException("Missing bundled default " + file);
        return input;
    }

    static JsonObject readObject(Path path) throws IOException {
        long max=path.toString().replace('\\','/').contains("categories/") ? 4_194_304 : 1_048_576;
        if (Files.size(path) > max) throw new IOException("JSON exceeds " +(max/1_048_576)+" MiB: " + path);
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return QuestRegistry.parseStrict(reader).getAsJsonObject();
        } catch (RuntimeException ex) { throw new IOException("Invalid JSON " + path + ": " + ex.getMessage(), ex); }
    }
}
