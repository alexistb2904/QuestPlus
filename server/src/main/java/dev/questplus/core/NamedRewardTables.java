package dev.questplus.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Authored references are expanded per candidate registry, before activating a reload. */
public final class NamedRewardTables {
    // TODO(Codex P1): implement durable inventory crate opening and nested crate references before claiming FTB table parity.
    private final Map<String,JsonObject> authored=new LinkedHashMap<>(),resolved=new HashMap<>();
    public NamedRewardTables(JsonArray tables) {
        if(tables.size()>128)throw new IllegalArgumentException("128 named reward tables maximum");
        for(var entry:tables) {
            var table=entry.getAsJsonObject();
            if(!table.keySet().equals(Set.of("name","reward")))throw new IllegalArgumentException("Invalid named table fields");
            String name=table.get("name").getAsString();
            if(name.isBlank()||name.length()>80||authored.putIfAbsent(name,table.getAsJsonObject("reward"))!=null)throw new IllegalArgumentException("Invalid/duplicate named reward table");
        }
        for(String name:authored.keySet())resolve(name,new HashSet<>());
    }
    public static NamedRewardTables load(Path quests) throws IOException {
        Path file=quests.resolveSibling("queststudio-compatibility.json");
        if(!Files.exists(file))return new NamedRewardTables(new JsonArray());
        try(var reader=Files.newBufferedReader(file)) {
            var metadata=QuestRegistry.parseStrict(reader).getAsJsonObject();
            return new NamedRewardTables(metadata.has("rewardTables")?metadata.getAsJsonArray("rewardTables"):new JsonArray());
        }
    }
    private JsonObject resolve(String name,Set<String> chain) {
        if(resolved.containsKey(name))return resolved.get(name);
        if(!chain.add(name)||chain.size()>16)throw new IllegalArgumentException("Named reward table cycle/depth: "+name);
        var value=authored.get(name);if(value==null)throw new IllegalArgumentException("Unknown named reward table: "+name);
        var result=expand(value,chain);chain.remove(name);
        String type=result.get("type").getAsString();
        if(!Set.of("questplus:choice","questplus:all","questplus:random","questplus:loot").contains(type))throw new IllegalArgumentException("Named table must resolve to a reward pool");
        QuestExtensions.validateReward(type,result.getAsJsonObject("data"));resolved.put(name,result);return result;
    }
    public JsonObject expand(JsonObject reward) {return expand(reward,new HashSet<>());}
    private JsonObject expand(JsonObject reward,Set<String> chain) {
        if(!reward.has("type")||!reward.get("type").getAsString().equals("questplus:table"))return reward.deepCopy();
        if(!Set.of("type","data","text").containsAll(reward.keySet())||!reward.getAsJsonObject("data").keySet().equals(Set.of("name")))throw new IllegalArgumentException("Invalid table reference");
        var result=resolve(reward.getAsJsonObject("data").get("name").getAsString(),chain).deepCopy();
        if(reward.has("text"))result.add("text",reward.get("text").deepCopy());return result;
    }
    public JsonObject quest(JsonObject source) {
        var copy=source.deepCopy();expandList(copy,"rewards");
        if(copy.has("tiers"))for(var tier:copy.getAsJsonArray("tiers"))expandList(tier.getAsJsonObject(),"rewards");
        return copy;
    }
    private void expandList(JsonObject owner,String field) {
        if(owner.has(field)){var list=new JsonArray();for(var value:owner.getAsJsonArray(field))list.add(expand(value.getAsJsonObject()));owner.add(field,list);}
    }
}
