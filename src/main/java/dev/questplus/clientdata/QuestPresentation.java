package dev.questplus.clientdata;

import com.google.gson.JsonObject;
import dev.questplus.core.QuestDefinition;

/** Small display rules shared by the GUI and HUD; server projection remains authoritative. */
public final class QuestPresentation {
    private QuestPresentation() {}

    public static String status(QuestDefinition quest, JsonObject progress) {
        if (flag(progress,"terminal") || quest.progression().equals("once") && flag(progress,"completed")
                || flag(progress,"waitingRestart")) return "completed";
        if (!flag(progress,"unlocked")) return "locked";
        if (count(progress,"tier")>0 || progress.has("counters") && progress.getAsJsonObject("counters").entrySet()
                .stream().anyMatch(entry -> entry.getValue().getAsLong()>0)) return "active";
        return "available";
    }

    public static long target(QuestDefinition quest, QuestDefinition.Objective objective, JsonObject progress) {
        if (!quest.progression().equals("tiered") || quest.tiers().isEmpty()) return objective.count();
        long tier=Math.clamp(count(progress,"tier"),0,quest.tiers().size()-1L);
        return objective.count()*quest.tiers().get((int)tier).threshold();
    }

    private static boolean flag(JsonObject json,String key) { return json.has(key) && json.get(key).getAsBoolean(); }
    private static long count(JsonObject json,String key) { return json.has(key) ? json.get(key).getAsLong() : 0; }
}
