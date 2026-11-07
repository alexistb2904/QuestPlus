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
			"dimension", "enchantment", "fluid", "structure", "statistic", "advancement", "effect");
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
			if (!field.keySet().equals(Set.of("key", "type", "label")))
				throw new IllegalArgumentException("Unknown descriptor field attributes");
			String key = field.get("key").getAsString(), type = field.get("type").getAsString(),
					name = field.get("label").getAsString();
			if (!key.matches(KEY) || !keys.add(key) || !TYPES.contains(type) || name.isBlank() || name.length() > 80)
				throw new IllegalArgumentException("Invalid extension editor field");
			var row = new JsonObject();
			row.addProperty("key", key);
			row.addProperty("type", type);
			row.addProperty("label", name);
			validated.add(row);
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
