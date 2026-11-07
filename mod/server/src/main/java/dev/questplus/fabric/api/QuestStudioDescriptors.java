package dev.questplus.fabric.api;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional, data-only authoring metadata for trusted mod integrations. Register
 * during mod initialization, after registering each custom Quest Plus type. No
 * JavaScript or user-supplied code is sent to Quest Studio.
 */
public final class QuestStudioDescriptors {
	private QuestStudioDescriptors() {
	}

	private static final Map<String, JsonObject> DEFINITIONS = new ConcurrentHashMap<>();
	private static final Set<String> KINDS = Set.of("event", "condition", "reward");
	private static final Set<String> TYPES = Set.of("string", "integer", "boolean", "item", "block", "entity", "biome",
			"dimension", "enchantment", "fluid", "structure", "statistic", "advancement", "effect", "number", "choice");
	private static final String ID = "[a-z0-9_.-]+:[a-z0-9_./-]+";
	private static final String KEY = "[a-z][a-zA-Z0-9_]{0,39}";

	public static void register(String kind, String id, String label, JsonArray fields) {
		if (!KINDS.contains(kind) || id == null || !id.matches(ID))
			throw new IllegalArgumentException("Invalid extension descriptor identity");
		if (label == null || label.isBlank() || label.length() > 80)
			throw new IllegalArgumentException("Invalid extension descriptor label");
		if (fields == null || fields.size() > 20)
			throw new IllegalArgumentException("Too many editor fields");
		JsonArray validated = new JsonArray();
		Set<String> keys = new HashSet<>();
		for (JsonElement value : fields) {
			if (!value.isJsonObject())
				throw new IllegalArgumentException("Invalid field descriptor");
			var field = value.getAsJsonObject();
            if (!Set.of("key", "type", "label", "default", "options", "min", "max").containsAll(field.keySet())
                    || !field.keySet().containsAll(Set.of("key", "type", "label")))
                throw new IllegalArgumentException("Unknown descriptor field attributes");
            String key = field.get("key").getAsString(), type = field.get("type").getAsString(),
                    name = field.get("label").getAsString();
            if (!key.matches(KEY) || !keys.add(key) || !TYPES.contains(type) || name.isBlank() || name.length() > 80)
                throw new IllegalArgumentException("Invalid extension editor field");
            if (field.has("options") != type.equals("choice"))
                throw new IllegalArgumentException("Only choice fields can define options");
            if (type.equals("choice")) {
                if (!field.get("options").isJsonArray())
                    throw new IllegalArgumentException("Choice options must be an array");
                var options = field.getAsJsonArray("options");
                if (options.size() == 0 || options.size() > 30)
                    throw new IllegalArgumentException("Choice requires 1..30 options");
                var values = new HashSet<String>();
                for (JsonElement option : options) {
                    if (!option.isJsonPrimitive() || !option.getAsJsonPrimitive().isString()
                            || option.getAsString().isBlank() || option.getAsString().length() > 120
                            || !values.add(option.getAsString()))
                        throw new IllegalArgumentException("Invalid choice option");
                }
            }
            if ((field.has("min") || field.has("max")) && !Set.of("integer", "number").contains(type))
                throw new IllegalArgumentException("Only numeric fields accept bounds");
            for (String bound : List.of("min", "max")) {
                if (field.has(bound) && (!field.get(bound).isJsonPrimitive()
                        || !field.getAsJsonPrimitive(bound).isNumber()
                        || !Double.isFinite(field.get(bound).getAsDouble())
                        || (type.equals("integer") && field.get(bound).getAsDouble() % 1 != 0)))
                    throw new IllegalArgumentException("Invalid numeric bound");
            }
            if (field.has("min") && field.has("max")
                    && field.get("min").getAsDouble() > field.get("max").getAsDouble())
                throw new IllegalArgumentException("Inverted numeric bounds");
            if (field.has("default")) {
                JsonElement def = field.get("default");
                boolean valid = switch (type) {
                    case "boolean" -> def.isJsonPrimitive() && def.getAsJsonPrimitive().isBoolean();
                    case "integer", "number" -> def.isJsonPrimitive() && def.getAsJsonPrimitive().isNumber()
                            && Double.isFinite(def.getAsDouble()) && (!type.equals("integer")
                            || def.getAsDouble() % 1 == 0)
                            && (!field.has("min") || def.getAsDouble() >= field.get("min").getAsDouble())
                            && (!field.has("max") || def.getAsDouble() <= field.get("max").getAsDouble());
                    case "choice" -> def.isJsonPrimitive() && def.getAsJsonPrimitive().isString()
                            && field.getAsJsonArray("options").asList().contains(def);
                    case "string" -> def.isJsonPrimitive() && def.getAsJsonPrimitive().isString()
                            && def.getAsString().length() <= 256;
                    default -> def.isJsonPrimitive() && def.getAsJsonPrimitive().isString()
                            && def.getAsString().matches(ID);
                };
                if (!valid)
                    throw new IllegalArgumentException("Invalid field default");
            }
            validated.add(field.deepCopy());
		}
		JsonObject descriptor = new JsonObject();
		descriptor.addProperty("kind", kind);
		descriptor.addProperty("id", id);
		descriptor.addProperty("label", label);
		descriptor.add("fields", validated);
		if (kind.equals("reward") && dev.questplus.core.QuestExtensions.allowsPoolReward(id))
			descriptor.addProperty("pool", true);
		if (DEFINITIONS.putIfAbsent(kind + ":" + id, descriptor) != null)
			throw new IllegalArgumentException("Duplicate Quest Studio descriptor " + kind + ":" + id);
	}

	public static JsonArray snapshot() {
		JsonArray result = new JsonArray();
		DEFINITIONS.values().stream()
				.sorted(Comparator.comparing(d -> d.get("kind").getAsString() + ":" + d.get("id").getAsString()))
				.forEach(d -> result.add(d.deepCopy()));
		return result;
	}
}
