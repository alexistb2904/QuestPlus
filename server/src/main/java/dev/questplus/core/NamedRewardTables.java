package dev.questplus.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Authored references are expanded per candidate registry, before activating a reload. */
public final class NamedRewardTables {
    private final Map<String,JsonObject> authored=new LinkedHashMap<>(),resolved=new HashMap<>();
    private final Set<String> crates=new HashSet<>();
    private final Map<String,JsonObject> crateSettings=new LinkedHashMap<>();
    public NamedRewardTables(JsonArray tables) {
        if(tables.size()>128)throw new IllegalArgumentException("128 named reward tables maximum");
        for(var entry:tables) {
            var table=entry.getAsJsonObject();
            if(!Set.of("name","reward","crate","crateName","crateColor","crateGlow","crateDrops").containsAll(table.keySet())||!table.keySet().containsAll(Set.of("name","reward")))throw new IllegalArgumentException("Invalid named table fields");
            String name=table.get("name").getAsString();
            if(name.isBlank()||name.length()>80||authored.putIfAbsent(name,table.getAsJsonObject("reward"))!=null)throw new IllegalArgumentException("Invalid/duplicate named reward table");
            if(table.has("crate")){if(!table.get("crate").isJsonPrimitive()||!table.getAsJsonPrimitive("crate").isBoolean())throw new IllegalArgumentException("Invalid crate flag");if(table.get("crate").getAsBoolean())crates.add(name);}
            if(table.has("crateName")&&(!table.get("crateName").isJsonPrimitive()||!table.getAsJsonPrimitive("crateName").isString()||table.get("crateName").getAsString().isBlank()||table.get("crateName").getAsString().length()>256))throw new IllegalArgumentException("Invalid crate name");
            if(table.has("crateColor")&&!table.get("crateColor").getAsString().matches("#[a-fA-F0-9]{6}"))throw new IllegalArgumentException("Invalid crate color");
            if(table.has("crateGlow")&&(!table.get("crateGlow").isJsonPrimitive()||!table.getAsJsonPrimitive("crateGlow").isBoolean()))throw new IllegalArgumentException("Invalid crate glow");
            if(table.has("crateDrops"))validateDropWeights(table.getAsJsonObject("crateDrops"));
            if(crates.contains(name))crateSettings.put(name,table.deepCopy());
        }
        for(String name:authored.keySet())resolve(name,new HashSet<>());
        for(String name:crates)if(resolved.get(name).get("type").getAsString().equals("questplus:choice"))throw new IllegalArgumentException("Crates require a loot, random or all table");
    }
    public Map<String,JsonObject> all() {var result=new LinkedHashMap<String,JsonObject>();resolved.forEach((name,value)->result.put(name,value.deepCopy()));return Collections.unmodifiableMap(result);}
    public JsonObject crate(String name) {if(!crates.contains(name))throw new IllegalArgumentException("Unknown loot crate: "+name);return resolved.get(name).deepCopy();}
    public static void validateDropWeights(JsonObject weights) {
        if(!weights.keySet().equals(Set.of("passive","monster","boss")))throw new IllegalArgumentException("Crate weights require passive, monster and boss");
        for(var entry:weights.entrySet())if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isNumber()||entry.getValue().getAsBigDecimal().intValueExact()<0||entry.getValue().getAsInt()>1_000_000)throw new IllegalArgumentException("Invalid crate drop weight");
    }
    public JsonObject drop(String kind,int emptyWeight,java.util.random.RandomGenerator random) {
        if(!Set.of("passive","monster","boss").contains(kind)||emptyWeight<0||emptyWeight>1_000_000)throw new IllegalArgumentException("Invalid crate drop category/weight");
        long total=emptyWeight;for(var table:crateSettings.values())if(table.has("crateDrops"))total+=table.getAsJsonObject("crateDrops").get(kind).getAsInt();
        if(total==0)return null;long ticket=random.nextLong(total)-emptyWeight;if(ticket<0)return null;
        for(var entry:crateSettings.entrySet()) {
            var settings=entry.getValue();ticket-=settings.has("crateDrops")?settings.getAsJsonObject("crateDrops").get(kind).getAsInt():0;if(ticket>=0)continue;
            var item=new JsonObject();item.addProperty("id","questplus:loot_crate");var components=new JsonObject();var data=new JsonObject();data.addProperty("questplus_table",entry.getKey());components.add("minecraft:custom_data",data);
            var title=new JsonObject();title.addProperty("text",settings.has("crateName")?settings.get("crateName").getAsString():entry.getKey());if(settings.has("crateColor"))title.add("color",settings.get("crateColor"));components.add("minecraft:custom_name",title);
            if(settings.has("crateGlow"))components.add("minecraft:enchantment_glint_override",settings.get("crateGlow"));item.add("components",components);return item;
        }
        throw new IllegalStateException("Invalid crate weights");
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
        if(!Set.of("type","data","text","teamReward","autoClaim","excludeFromClaimAll").containsAll(reward.keySet())||!reward.getAsJsonObject("data").keySet().equals(Set.of("name")))throw new IllegalArgumentException("Invalid table reference");
        var result=resolve(reward.getAsJsonObject("data").get("name").getAsString(),chain).deepCopy();
        for(String key:List.of("text","teamReward","autoClaim","excludeFromClaimAll"))if(reward.has(key))result.add(key,reward.get(key).deepCopy());return result;
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
