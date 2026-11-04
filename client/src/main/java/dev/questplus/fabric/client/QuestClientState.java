package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.core.*;
import java.util.*;

/** Read-only server projection, replaced on the client thread after complete transfers. */
public final class QuestClientState {
    public final Map<String,QuestDefinition> quests = new LinkedHashMap<>();
    public final Map<String,JsonObject> categories = new LinkedHashMap<>(), progress = new HashMap<>();
    public JsonObject team;
    public JsonArray invitations = new JsonArray();
    public long generation = -1, sequence = -1, catalogueRevision;
    public String error, errorDetail;
    public boolean ready;
    private static final Gson JSON = new Gson();
    public String text(Map<String,String> text) { return Localization.text(text, net.minecraft.client.Minecraft.getInstance().options.languageCode, Map.of()); }
    public JsonObject progress(String id) { return progress.getOrDefault(id,new JsonObject()); }
    public static boolean flag(JsonObject object,String name) { return object.has(name) && object.get(name).getAsBoolean(); }
    public static long count(JsonObject object,String name) { return object.has(name) ? object.get(name).getAsLong() : 0; }
    public boolean apply(JsonObject message) {
        if (message.has("error")) {
            error=message.get("error").getAsString();
            errorDetail=message.has("detail")?message.get("detail").getAsString():null;
            return true;
        }
        long nextGeneration=message.get("generation").getAsLong(), nextSequence=message.get("sequence").getAsLong();
        boolean snapshot=message.get("type").getAsString().equals("snapshot");
        if (!snapshot && (!ready || nextGeneration!=generation || nextSequence!=sequence+1)) return false;
        if (snapshot) {
            Map<String,QuestDefinition> definitions=new LinkedHashMap<>();
            for (var element:message.getAsJsonArray("quests")) {
                var quest=JSON.fromJson(element,QuestDefinition.class);
                definitions.put(quest.id(),quest);
            }
            Map<String,JsonObject> groups=new LinkedHashMap<>();
            for (var element:message.getAsJsonArray("categories")) { var category=element.getAsJsonObject(); groups.put(category.get("id").getAsString(),category); }
            quests.clear(); quests.putAll(definitions); categories.clear(); categories.putAll(groups); progress.clear(); catalogueRevision++;
        }
        if (message.has("progress")) for (var entry:message.getAsJsonObject("progress").entrySet()) progress.put(entry.getKey(),entry.getValue().getAsJsonObject());
        if (message.has("team")) team=message.get("team").isJsonNull() ? null : message.getAsJsonObject("team");
        if (message.has("invitations")) invitations=message.getAsJsonArray("invitations");
        generation=nextGeneration; sequence=nextSequence; ready=true; error=null; errorDetail=null; return true;
    }
    public void clear() { quests.clear(); categories.clear(); progress.clear(); team=null; invitations=new JsonArray(); generation=-1; sequence=-1; ready=false; error=null; errorDetail=null; catalogueRevision++; }
}
