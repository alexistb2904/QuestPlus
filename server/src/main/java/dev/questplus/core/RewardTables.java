package dev.questplus.core;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Resolves authored weighted rewards once, at quest completion. Entitlements store
 * the selected concrete rewards, so claiming never rolls again on retry or reconnect.
 * No executable commands or arbitrary extension payloads are permitted in pools.
 */
public final class RewardTables {
    private RewardTables() {}
    public static void validate(JsonObject data) {
        if(!data.keySet().equals(Set.of("options")) && !data.keySet().equals(Set.of("options","rolls")))
            throw new IllegalArgumentException("Random rewards require options, optional rolls");
        JsonArray options=data.getAsJsonArray("options");
        if(options==null||options.isEmpty()||options.size()>32)
            throw new IllegalArgumentException("Random reward table requires 1..32 options");
        int rolls=data.has("rolls")?integer(data,"rolls",1,16):1;
        long total=0;
        for(var element:options){
            if(!element.isJsonObject())throw new IllegalArgumentException("Invalid weighted reward entry");
            JsonObject choice=element.getAsJsonObject();
            int weight=integer(choice,"weight",1,1_000_000);
            total+=weight;
            if(total>32_000_000L)throw new IllegalArgumentException("Random table weights exceed limit");
            String type=choice.has("type")?choice.get("type").getAsString():"";
            if(type.equals("item")){
                if(!choice.keySet().equals(Set.of("weight","type","item","count")))throw new IllegalArgumentException("Invalid item option");
                String id=choice.get("item").getAsString();
                if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||id.equals("minecraft:air"))
                    throw new IllegalArgumentException("Invalid option item");
                integer(choice,"count",1,2304);
            } else if(Set.of("experience","experience_levels").contains(type)){
                if(!choice.keySet().equals(Set.of("weight","type","amount")))throw new IllegalArgumentException("Invalid XP option");
                integer(choice,"amount",1,1_000_000);
            } else throw new IllegalArgumentException("Random reward option must be an item or XP");
        }
    }
    /** Loot pools may intentionally award nothing; random pools always award an option. */
    public static void validateLoot(JsonObject data) {
        if(!data.has("emptyWeight"))throw new IllegalArgumentException("Loot requires an explicit emptyWeight");
        var normalized=data.deepCopy();normalized.remove("emptyWeight");
        validate(normalized);
        integer(data,"emptyWeight",0,1_000_000);
    }
    /** All reward pools grant each entry once, ignoring weights. */
    public static void validateAll(JsonObject data) {
        if(!data.keySet().equals(Set.of("options")))throw new IllegalArgumentException("All-table rewards require options");
        validate(data);
    }
    private static QuestDefinition.Reward concrete(JsonObject option,Map<String,String> text) {
        String type=option.get("type").getAsString();
        return type.equals("item")
            ? new QuestDefinition.Reward(type,option.get("item").getAsString(),option.get("count").getAsInt(),0,"{}",text)
            : new QuestDefinition.Reward(type,null,0,option.get("amount").getAsInt(),"{}",text);
    }
    private static int integer(JsonObject object,String key,int min,int max){
        if(!object.has(key)||!object.get(key).isJsonPrimitive()||!object.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Missing integer "+key);
        int result;
        try{result=object.get(key).getAsBigDecimal().intValueExact();}
        catch(ArithmeticException e){throw new IllegalArgumentException("Invalid integer "+key,e);}
        if(result<min||result>max)throw new IllegalArgumentException("Integer out of range "+key);
        return result;
    }
    public static List<QuestDefinition.Reward> resolve(List<QuestDefinition.Reward> authored){
        List<QuestDefinition.Reward> selected=new ArrayList<>();
        for(var reward:authored){
            if(!Set.of("questplus:random","questplus:loot","questplus:all").contains(reward.type())) { selected.add(reward); continue; }
            JsonObject data=JsonParser.parseString(reward.data()).getAsJsonObject();
            if(reward.type().equals("questplus:loot"))validateLoot(data);
            else if(reward.type().equals("questplus:all"))validateAll(data);
            else validate(data);
            JsonArray options=data.getAsJsonArray("options");
            if(reward.type().equals("questplus:all")){
                for(var option:options)selected.add(concrete(option.getAsJsonObject(),reward.text()));
                continue;
            }
            long total=0;
            for(var choice:options)total+=choice.getAsJsonObject().get("weight").getAsLong();
            long weightedTotal=total+(reward.type().equals("questplus:loot")?data.get("emptyWeight").getAsInt():0);
            int rolls=data.has("rolls")?data.get("rolls").getAsInt():1;
            for(int i=0;i<rolls;i++){
                long ticket=ThreadLocalRandom.current().nextLong(weightedTotal);
                if(ticket>=total)continue; // Explicitly empty loot roll.
                for(var entry:options){
                    JsonObject option=entry.getAsJsonObject();
                    ticket-=option.get("weight").getAsLong();
                    if(ticket>=0)continue;
                    selected.add(concrete(option,reward.text()));
                    break;
                }
            }
        }
        if(selected.size()>64)throw new IllegalArgumentException("Random rewards exceed entitlement limit");
        return List.copyOf(selected);
    }
}
