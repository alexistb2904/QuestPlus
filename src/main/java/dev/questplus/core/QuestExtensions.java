package dev.questplus.core;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/** Registrations are trusted server-mod code, installed before definition loading. */
public final class QuestExtensions {
    public record EventType(String registry, boolean state) {}
    public record ConditionType(Consumer<JsonElement> validator,BiPredicate<JsonElement,Map<String,Object>> predicate) {}
    private static final Map<String,EventType> EVENTS = new ConcurrentHashMap<>();
    private static final Map<String,ConditionType> CONDITIONS = new ConcurrentHashMap<>();
    private static final Map<String,Consumer<JsonObject>> REWARDS = new ConcurrentHashMap<>();
    static {
        for (String type : List.of("block_break","block_place","block_interact")) EVENTS.put(type,new EventType("block",false));
        for (String type : List.of("item_obtained","item_craft","item_smelt","item_use","item_consume","fish_item","trade")) EVENTS.put(type,new EventType("item",false));
        for (String type : List.of("entity_kill","fish_entity","tame_animal","breed_animal","npc_interact")) EVENTS.put(type,new EventType("entity",false));
        EVENTS.put("biome_visit",new EventType("biome",false));
        EVENTS.put("structure_visit",new EventType("structure",false));
        EVENTS.put("dimension_enter",new EventType("dimension",false));
        EVENTS.put("inventory_count",new EventType("item",true));
        // Evaluated from authoritative server equipment snapshots, not player events.
        EVENTS.put("equipment_check",new EventType("marker",true));
        EVENTS.put("questplus:fish_any",new EventType("marker",false));
        for (String type : List.of("travel_distance","location","player_death","manual")) EVENTS.put(type,new EventType("marker",false));
        CONDITIONS.put("questplus:gear",new ConditionType(GearPredicate::validate,GearPredicate::test));
        CONDITIONS.put("equipment",new ConditionType(value -> {
            JsonObject o=value.getAsJsonObject();
            if (!o.keySet().equals(Set.of("slot","item")) || !Set.of("head","chest","legs","feet","mainhand","offhand").contains(o.get("slot").getAsString())
                    || !o.get("item").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid equipment condition");
        },(value,context) -> {
            var o=value.getAsJsonObject();
            return context.get("equipment") instanceof Map<?,?> equipment && Objects.equals(equipment.get(o.get("slot").getAsString()),o.get("item").getAsString());
        }));
        CONDITIONS.put("position",new ConditionType(value -> {
            var o=value.getAsJsonObject();
            if (o.isEmpty()) throw new IllegalArgumentException("Position bounds cannot be empty");
            for (var e:o.entrySet()) if (!Set.of("xMin","xMax","yMin","yMax","zMin","zMax").contains(e.getKey()) || !e.getValue().isJsonPrimitive()
                    || !e.getValue().getAsJsonPrimitive().isNumber() || !Double.isFinite(e.getValue().getAsDouble())) throw new IllegalArgumentException("Invalid position bounds");
            for (String axis:List.of("x","y","z")) if (o.has(axis+"Min") && o.has(axis+"Max") && o.get(axis+"Min").getAsDouble()>o.get(axis+"Max").getAsDouble()) throw new IllegalArgumentException("Inverted position bounds");
        },(value,context) -> {
            if (!(context.get("position") instanceof Map<?,?> position)) return false;
            for (var e:value.getAsJsonObject().entrySet()) {
                if (!(position.get(e.getKey().substring(0,1)) instanceof Number n)) return false;
                if (e.getKey().endsWith("Min") ? n.doubleValue()<e.getValue().getAsDouble() : n.doubleValue()>e.getValue().getAsDouble()) return false;
            }
            return true;
        }));
        CONDITIONS.put("context",new ConditionType(value -> {
            var o=value.getAsJsonObject();
            if (!o.keySet().equals(Set.of("key","equals")) || !o.get("key").isJsonPrimitive() || !o.get("key").getAsJsonPrimitive().isString()
                    || o.get("key").getAsString().isBlank() || !o.get("equals").isJsonPrimitive()) throw new IllegalArgumentException("Invalid context condition");
        },(value,context) -> {
            var o=value.getAsJsonObject(); var expected=o.getAsJsonPrimitive("equals"); Object actual=context.get(o.get("key").getAsString());
            if (expected.isNumber()) return actual instanceof Number n && Double.compare(n.doubleValue(),expected.getAsDouble())==0;
            return expected.isBoolean() ? Objects.equals(actual,expected.getAsBoolean()) : Objects.equals(actual,expected.getAsString());
        }));
    }
    private QuestExtensions() {}
    public static void registerEvent(String id,String registry,boolean state) {
        namespaced(id);
        if (!Set.of("block","item","entity","biome","structure","dimension","marker").contains(registry)
                || EVENTS.putIfAbsent(id,new EventType(registry,state))!=null) throw new IllegalArgumentException("Invalid/duplicate event " + id);
    }
    public static EventType event(String id) { var type=EVENTS.get(id); if (type==null) throw new IllegalArgumentException("Unknown event " + id); return type; }
    public static void registerCondition(String id,Consumer<JsonElement> validator,BiPredicate<JsonElement,Map<String,Object>> predicate) {
        namespaced(id); if (CONDITIONS.putIfAbsent(id,new ConditionType(Objects.requireNonNull(validator),Objects.requireNonNull(predicate)))!=null) throw new IllegalArgumentException("Duplicate condition " + id);
    }
    public static boolean hasCondition(String id) { return CONDITIONS.containsKey(id); }
    public static void validateCondition(String id,JsonElement data) { var type=CONDITIONS.get(id); if (type==null) throw new IllegalArgumentException("Unknown condition " + id); type.validator().accept(data); }
    public static boolean testCondition(String id,String json,Map<String,Object> context) { return CONDITIONS.get(id).predicate().test(JsonParser.parseString(json),context); }
    public static void registerReward(String id,Consumer<JsonObject> validator) { namespaced(id); if (REWARDS.putIfAbsent(id,Objects.requireNonNull(validator))!=null) throw new IllegalArgumentException("Duplicate reward " + id); }
    public static void validateReward(String id,JsonObject data) { var validator=REWARDS.get(id); if (validator==null) throw new IllegalArgumentException("Unknown reward " + id); validator.accept(data); }
    public static void validateSavedReward(QuestDefinition.Reward r) {
        if (r==null || r.type()==null) throw new IllegalArgumentException("Invalid reward");
        if (r.type().equals("item")) {
            if (r.item()==null || !r.item().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || r.item().equals("minecraft:air") || r.count()<1 || r.count()>2304) throw new IllegalArgumentException("Invalid item reward");
        } else if (r.type().equals("experience")) {
            if (r.amount()<1 || r.amount()>1_000_000) throw new IllegalArgumentException("Invalid experience reward");
        } else validateReward(r.type(),JsonParser.parseString(r.data()).getAsJsonObject());
    }
    private static void namespaced(String id) { if (id==null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Extension IDs must be namespaced"); }
}
