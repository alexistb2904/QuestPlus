package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.team.ProgressScope;
import java.util.*;

/** Bounded, server-validated configuration. Resource balances never come from a packet. */
public record QuestBlockConfig(ProgressScope scope, List<String> quests, String category, String objective,
        String boardMode, String leaderboard, String condition, String output, int threshold,
        boolean all, int faces, boolean automation, boolean advanceTiers) {
    public static final Gson JSON = new Gson();
    public static final QuestBlockConfig DEFAULT = new QuestBlockConfig(ProgressScope.PERSONAL, List.of(), "", "",
            "mixed", "players", "completed", "binary", 1, true, 63, false, false);
    public QuestBlockConfig {
        if (scope == null || quests == null || quests.size() > 32 || new HashSet<>(quests).size() != quests.size()
                || quests.stream().anyMatch(q -> q == null || !q.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                || category == null || category.length() > 128 || objective == null || objective.length() > 128
                || !Set.of("pinned", "automatic", "mixed", "leaderboard").contains(boardMode)
                || !Set.of("players", "teams", "contributions").contains(leaderboard)
                || !Set.of("unlocked", "started", "completed", "claimed", "tier", "stage").contains(condition)
                || !Set.of("binary", "pulse", "analog").contains(output)
                || threshold < 1 || threshold > 1000000 || faces < 0 || faces > 63)
            throw new IllegalArgumentException("Invalid quest block configuration");
        quests = List.copyOf(quests);
    }
    public static QuestBlockConfig parse(String text) {
        if (text.length() > 8192) throw new IllegalArgumentException("Configuration too large");
        JsonObject value = JsonParser.parseString(text).getAsJsonObject();
        if (!value.keySet().equals(JSON.toJsonTree(DEFAULT).getAsJsonObject().keySet()))
            throw new IllegalArgumentException("Unexpected configuration fields");
        for (String field : List.of("threshold", "faces")) value.get(field).getAsBigDecimal().intValueExact();
        for (String field : List.of("all", "automation", "advanceTiers"))
            if (!value.get(field).isJsonPrimitive() || !value.get(field).getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Expected boolean: " + field);
        for (String field : List.of("scope", "category", "objective", "boardMode", "leaderboard", "condition", "output"))
            if (!value.get(field).isJsonPrimitive() || !value.get(field).getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Expected string: " + field);
        var quests = new ArrayList<String>();
        for (var id : value.getAsJsonArray("quests")) {
            if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid quest ID");
            quests.add(id.getAsString());
        }
        return new QuestBlockConfig(ProgressScope.valueOf(value.get("scope").getAsString()), quests,
                value.get("category").getAsString(), value.get("objective").getAsString(), value.get("boardMode").getAsString(),
                value.get("leaderboard").getAsString(), value.get("condition").getAsString(), value.get("output").getAsString(),
                value.get("threshold").getAsInt(), value.get("all").getAsBoolean(), value.get("faces").getAsInt(),
                value.get("automation").getAsBoolean(), value.get("advanceTiers").getAsBoolean());
    }
    public String encode() { return JSON.toJson(this); }
}
