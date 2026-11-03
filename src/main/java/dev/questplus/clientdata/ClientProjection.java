package dev.questplus.clientdata;

import com.google.gson.*;
import dev.questplus.core.*;
import java.util.*;

/** Explicit read-only projection; persistence records and reward extension data stay on the server. */
public final class ClientProjection {
    private static final Gson JSON = new Gson();
    private ClientProjection() {}

    public record Category(String id, Map<String,String> title, Map<String,String> description, String icon) {}

    public static List<QuestDefinition> visible(Collection<QuestDefinition> quests,PlayerProgress state) {
        Set<String> completed=new HashSet<>();
        state.quests.forEach((id,p) -> { if (p.completed) completed.add(id); });
        return quests.stream().filter(q -> !q.hidden() || state.quests.containsKey(q.id())
            || q.requires()==null || q.requires().matches(Map.of(),completed)).toList();
    }

    public static JsonArray quests(Collection<QuestDefinition> quests) {
        JsonArray out = new JsonArray();
        Set<String> visibleIds=new HashSet<>(); quests.forEach(q -> visibleIds.add(q.id()));
        quests.stream().sorted(Comparator.comparing(QuestDefinition::id)).forEach(q -> {
            JsonObject item=JSON.toJsonTree(q).getAsJsonObject();
            if (item.has("requires")) redact(item.getAsJsonObject("requires"),visibleIds);
            stripRewards(item.getAsJsonArray("rewards"));
            for (JsonElement tier:item.getAsJsonArray("tiers")) stripRewards(tier.getAsJsonObject().getAsJsonArray("rewards"));
            out.add(item);
        });
        return out;
    }

    private static void redact(JsonObject condition,Set<String> visibleIds) {
        if (condition.get("operator").getAsString().equals("quest") && !visibleIds.contains(condition.get("value").getAsString())) {
            condition.addProperty("operator","hidden"); condition.addProperty("value","undiscovered");
        }
        for (JsonElement child:condition.getAsJsonArray("children")) redact(child.getAsJsonObject(),visibleIds);
    }

    private static void stripRewards(JsonArray rewards) {
        if (rewards!=null) for (JsonElement reward:rewards) reward.getAsJsonObject().remove("data");
    }

    public static JsonObject progress(Collection<QuestDefinition> quests, QuestEngine engine, PlayerProgress state, PlayerProgress personal) {
        JsonObject result=new JsonObject();
        Set<String> completed=new HashSet<>();
        state.quests.forEach((id,p) -> { if (p.completed) completed.add(id); });
        Set<String> claimable=new HashSet<>();
        personal.entitlements.forEach((key,grant) -> { if (!personal.claims.containsKey(key)) claimable.add(grant.questId()); });
        for (QuestDefinition quest:quests) {
            PlayerProgress.QuestProgress p=state.quests.get(quest.id());
            JsonObject value=new JsonObject();
            value.add("counters",JSON.toJsonTree(p==null ? Map.of() : p.counters));
            value.add("cumulative",JSON.toJsonTree(p==null ? Map.of() : p.cumulative));
            value.addProperty("completed",p!=null && p.completed);
            value.addProperty("cycles",p==null ? 0 : p.cycles);
            value.addProperty("tier",p==null ? 0 : p.tier);
            value.addProperty("waitingRestart",p!=null && p.waitingRestart);
            value.addProperty("terminal",p!=null && p.terminal);
            value.addProperty("claimable",claimable.contains(quest.id()));
            value.addProperty("unlocked",quest.requires()==null || quest.requires().matches(Map.of(),completed));
            engine.diagnostic(state,quest.id()).ifPresent(d -> value.addProperty("diagnostic",d));
            result.add(quest.id(),value);
        }
        return result;
    }
}
