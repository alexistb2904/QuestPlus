package dev.questplus.core;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/**
 * Registrations are trusted server-mod code, installed before definition
 * loading.
 */
public final class QuestExtensions {
	public record EventType(String registry, boolean state) {
	}

	public record ConditionType(Consumer<JsonElement> validator,
			BiPredicate<JsonElement, Map<String, Object>> predicate) {
	}

	private static final Map<String, EventType> EVENTS = new ConcurrentHashMap<>();
	private static final Map<String, ConditionType> CONDITIONS = new ConcurrentHashMap<>();
	private static final Map<String, Consumer<JsonObject>> REWARDS = new ConcurrentHashMap<>();
	private static final Set<String> POOL_REWARDS = ConcurrentHashMap.newKeySet();

	private record ObjectiveType(Consumer<JsonObject> validator, BiPredicate<JsonObject, QuestEvent> predicate) {
	}

	private static final Map<String, ObjectiveType> OBJECTIVES = new ConcurrentHashMap<>();
	private static final Set<String> COMPONENT_EVENTS = Set.of("item_craft", "item_obtained", "item_smelt",
			"item_use", "item_consume", "fish_item", "trade");
	/** Compiled once: String.matches recompiles its regex on every quest event and save. */
	private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Map<String, JsonElement> PARSED = new ConcurrentHashMap<>();

	public static boolean isId(String value) {
		return value != null && ID.matcher(value).matches();
	}

	/**
	 * Authored objective/condition JSON never changes after load, so each distinct
	 * string is parsed once instead of on every event. The result is shared: read only.
	 */
	public static JsonElement parsed(String json) {
		var value = PARSED.get(json);
		if (value == null) {
			// Reloads leave old strings behind. A full reset keeps the cache bounded.
			if (PARSED.size() >= 4096)
				PARSED.clear();
			PARSED.put(json, value = JsonParser.parseString(json));
		}
		return value;
	}
	static {
		REWARDS.put("questplus:advancement", data -> {
			if (!Set.of("advancement", "criterion").containsAll(data.keySet()) || !data.has("advancement")
					|| !data.get("advancement").isJsonPrimitive()
					|| !data.get("advancement").getAsJsonPrimitive().isString()
					|| !data.get("advancement").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
				throw new IllegalArgumentException("Advancement reward requires a registered advancement ID");
			if (data.has("criterion") && (!data.get("criterion").isJsonPrimitive()
					|| !data.getAsJsonPrimitive("criterion").isString() || data.get("criterion").getAsString().isBlank()
					|| data.get("criterion").getAsString().length() > 256))
				throw new IllegalArgumentException("Invalid advancement criterion");
		});
		REWARDS.put("questplus:random", RewardTables::validate);
		REWARDS.put("questplus:loot", RewardTables::validateLoot);
		REWARDS.put("questplus:all", RewardTables::validateAll);
		REWARDS.put("questplus:choice", RewardTables::validateChoice);
		REWARDS.put("questplus:message", data -> {
			if (!data.keySet().equals(Set.of("message")) || !data.get("message").isJsonPrimitive()
					|| !data.get("message").getAsJsonPrimitive().isString()
					|| data.get("message").getAsString().isBlank() || data.get("message").getAsString().length() > 256)
				throw new IllegalArgumentException("Message reward requires 1..256 characters");
		});
		for (String type : List.of("block_break", "block_place", "block_interact"))
			EVENTS.put(type, new EventType("block", false));
		for (String type : List.of("item_obtained", "item_craft", "item_smelt", "item_use", "item_consume", "fish_item",
				"trade"))
			EVENTS.put(type, new EventType("item", false));
		for (String type : List.of("entity_kill", "fish_entity", "tame_animal", "breed_animal", "npc_interact"))
			EVENTS.put(type, new EventType("entity", false));
		EVENTS.put("biome_visit", new EventType("biome", false));
		EVENTS.put("structure_visit", new EventType("structure", false));
		EVENTS.put("dimension_enter", new EventType("dimension", false));
		EVENTS.put("inventory_count", new EventType("item", true));
		EVENTS.put("item_submit", new EventType("item", false));
		EVENTS.put("item_turn_in", new EventType("item", false));
		EVENTS.put("xp_submit_levels", new EventType("marker", false));
		EVENTS.put("xp_submit_points", new EventType("marker", false));
		// These events require a trusted server-side mod integration. They do NOT imply
		// automatic extraction from arbitrary modded tanks or power cables.
		EVENTS.put("fluid_transfer", new EventType("fluid", false));
		EVENTS.put("energy_transfer", new EventType("marker", false));
		EVENTS.put("fluid_submit", new EventType("fluid", false));
		EVENTS.put("energy_submit", new EventType("marker", false));
		EVENTS.put("xp_levels", new EventType("marker", true));
		EVENTS.put("xp_points", new EventType("marker", true));
		EVENTS.put("custom_stat", new EventType("marker", true));
		EVENTS.put("advancement_complete", new EventType("marker", true));
		EVENTS.put("observe_block", new EventType("block", true));
		EVENTS.put("observe_entity", new EventType("entity", true));
		EVENTS.put("stage", new EventType("marker", true));
		CONDITIONS.put("questplus:stage", new ConditionType(value -> {
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
				throw new IllegalArgumentException("Expected stage ID");
			namespaced(value.getAsString());
		}, (value, context) -> context.get("stages") instanceof Set<?>stages && stages.contains(value.getAsString())));
		REWARDS.put("questplus:stage", data -> {
			if (!data.keySet().equals(Set.of("stage", "remove")) || !data.get("remove").isJsonPrimitive()
					|| !data.getAsJsonPrimitive("remove").isBoolean())
				throw new IllegalArgumentException("Stage reward requires stage and remove");
			namespaced(data.get("stage").getAsString());
		});
		REWARDS.put("questplus:toast", data -> {
			if (!Set.of("title", "description", "icon").equals(data.keySet()))
				throw new IllegalArgumentException("Toast requires title, description, icon");
			for (String field : List.of("title", "description")) {
				var translated = data.getAsJsonObject(field);
				if (!translated.has("en_us") || translated.size() > 128)
					throw new IllegalArgumentException("Toast requires English translation");
				for (var entry : translated.entrySet())
					if (!entry.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || !entry.getValue().isJsonPrimitive()
							|| !entry.getValue().getAsJsonPrimitive().isString()
							|| entry.getValue().getAsString().isBlank()
							|| entry.getValue().getAsString().length() > 256)
						throw new IllegalArgumentException("Invalid toast translation");
			}
			namespaced(data.get("icon").getAsString());
		});
		// Evaluated from authoritative server equipment snapshots, not player events.
		EVENTS.put("equipment_check", new EventType("marker", false));
		EVENTS.put("questplus:fish_any", new EventType("marker", false));
		for (String type : List.of("travel_distance", "location", "player_death", "manual"))
			EVENTS.put(type, new EventType("marker", false));
		CONDITIONS.put("questplus:gear", new ConditionType(GearPredicate::validate, GearPredicate::test));
		CONDITIONS.put("equipment", new ConditionType(value -> {
			JsonObject o = value.getAsJsonObject();
			if (!o.keySet().equals(Set.of("slot", "item"))
					|| !Set.of("head", "chest", "legs", "feet", "mainhand", "offhand")
							.contains(o.get("slot").getAsString())
					|| !o.get("item").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
				throw new IllegalArgumentException("Invalid equipment condition");
		}, (value, context) -> {
			var o = value.getAsJsonObject();
			return context.get("equipment") instanceof Map<?, ?>equipment
					&& Objects.equals(equipment.get(o.get("slot").getAsString()), o.get("item").getAsString());
		}));
		CONDITIONS.put("position", new ConditionType(value -> {
			var o = value.getAsJsonObject();
			if (o.isEmpty())
				throw new IllegalArgumentException("Position bounds cannot be empty");
			if (o.has("blockCoordinates") && (!o.get("blockCoordinates").isJsonPrimitive()
					|| !o.getAsJsonPrimitive("blockCoordinates").isBoolean()))
				throw new IllegalArgumentException("Invalid block coordinate mode");
			for (var e : o.entrySet())
				if (!e.getKey().equals("blockCoordinates")
						&& (!Set.of("xMin", "xMax", "yMin", "yMax", "zMin", "zMax").contains(e.getKey())
								|| !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()
								|| !Double.isFinite(e.getValue().getAsDouble())))
					throw new IllegalArgumentException("Invalid position bounds");
			for (String axis : List.of("x", "y", "z"))
				if (o.has(axis + "Min") && o.has(axis + "Max")
						&& o.get(axis + "Min").getAsDouble() > o.get(axis + "Max").getAsDouble())
					throw new IllegalArgumentException("Inverted position bounds");
		}, (value, context) -> {
			if (!(context.get("position") instanceof Map<?, ?>position))
				return false;
			for (var e : value.getAsJsonObject().entrySet()) {
				if (e.getKey().equals("blockCoordinates"))
					continue;
				if (!(position.get(e.getKey().substring(0, 1)) instanceof Number n))
					return false;
				double coordinate = value.getAsJsonObject().has("blockCoordinates")
						&& value.getAsJsonObject().get("blockCoordinates").getAsBoolean() ? Math.floor(n.doubleValue())
								: n.doubleValue();
				if (e.getKey().endsWith("Min") ? coordinate < e.getValue().getAsDouble()
						: coordinate > e.getValue().getAsDouble())
					return false;
			}
			return true;
		}));
		CONDITIONS.put("context", new ConditionType(value -> {
			var o = value.getAsJsonObject();
			if (!o.keySet().equals(Set.of("key", "equals")) || !o.get("key").isJsonPrimitive()
					|| !o.get("key").getAsJsonPrimitive().isString() || o.get("key").getAsString().isBlank()
					|| !o.get("equals").isJsonPrimitive())
				throw new IllegalArgumentException("Invalid context condition");
		}, (value, context) -> {
			var o = value.getAsJsonObject();
			var expected = o.getAsJsonPrimitive("equals");
			Object actual = context.get(o.get("key").getAsString());
			if (expected.isNumber())
				return actual instanceof Number n && Double.compare(n.doubleValue(), expected.getAsDouble()) == 0;
			return expected.isBoolean() ? Objects.equals(actual, expected.getAsBoolean())
					: Objects.equals(actual, expected.getAsString());
		}));
	}

	private QuestExtensions() {
	}

	public static void registerEvent(String id, String registry, boolean state) {
		namespaced(id);
		if (!Set.of("block", "item", "entity", "biome", "structure", "dimension", "fluid", "marker").contains(registry)
				|| EVENTS.putIfAbsent(id, new EventType(registry, state)) != null)
			throw new IllegalArgumentException("Invalid/duplicate event " + id);
	}

	public static EventType event(String id) {
		var type = EVENTS.get(id);
		if (type == null)
			throw new IllegalArgumentException("Unknown event " + id);
		return type;
	}

	public static void registerObjective(String event, Consumer<JsonObject> validator,
			BiPredicate<JsonObject, QuestEvent> predicate) {
		namespaced(event);
		event(event);
		if (OBJECTIVES.putIfAbsent(event,
				new ObjectiveType(Objects.requireNonNull(validator), Objects.requireNonNull(predicate))) != null)
			throw new IllegalArgumentException("Duplicate objective handler " + event);
	}

	public static void validateObjective(String event, JsonObject data) {
		if (data.toString().length() > 16384)
			throw new IllegalArgumentException("Objective data exceeds 16 KiB");
		if (event.equals("energy_submit")) {
			if (!Set.of("fePerEnergy").containsAll(data.keySet()))
				throw new IllegalArgumentException("Unknown energy parameter");
			if (data.has("fePerEnergy")
					&& (!data.get("fePerEnergy").isJsonPrimitive() || !data.getAsJsonPrimitive("fePerEnergy").isNumber()
							|| data.get("fePerEnergy").getAsBigDecimal().intValueExact() < 1
							|| data.get("fePerEnergy").getAsInt() > 1_000_000))
				throw new IllegalArgumentException("Energy ratio must be 1..1000000");
			return;
		}
		if (ItemComponents.supports(event)) {
			ItemComponents.validate(data);
			return;
		}
		var type = OBJECTIVES.get(event);
		if (type != null)
			type.validator().accept(data);
		else if (!data.isEmpty())
			throw new IllegalArgumentException("Objective has no registered data validator: " + event);
	}

	public static boolean matchesObjective(QuestDefinition.Objective objective, QuestEvent event) {
		if (!objective.data().equals("{}") && COMPONENT_EVENTS.contains(objective.event())) {
			var data = parsed(objective.data()).getAsJsonObject();
			if (!data.has("matchComponents") || data.get("matchComponents").getAsString().equals("none"))
				return true;
			var actual = event.context().get("itemComponents");
			return actual instanceof JsonObject components && ItemComponents.matches(data, components);
		}
		var type = OBJECTIVES.get(objective.event());
		return type == null
				|| type.predicate().test(parsed(Objects.toString(objective.data(), "{}")).getAsJsonObject(), event);
	}

	public static void registerCondition(String id, Consumer<JsonElement> validator,
			BiPredicate<JsonElement, Map<String, Object>> predicate) {
		namespaced(id);
		if (CONDITIONS.putIfAbsent(id,
				new ConditionType(Objects.requireNonNull(validator), Objects.requireNonNull(predicate))) != null)
			throw new IllegalArgumentException("Duplicate condition " + id);
	}

	public static boolean hasCondition(String id) {
		return CONDITIONS.containsKey(id);
	}

	public static void validateCondition(String id, JsonElement data) {
		var type = CONDITIONS.get(id);
		if (type == null)
			throw new IllegalArgumentException("Unknown condition " + id);
		type.validator().accept(data);
	}

	public static boolean testCondition(String id, String json, Map<String, Object> context) {
		return CONDITIONS.get(id).predicate().test(parsed(json), context);
	}

	public static void registerReward(String id, Consumer<JsonObject> validator) {
		namespaced(id);
		if (REWARDS.putIfAbsent(id, Objects.requireNonNull(validator)) != null)
			throw new IllegalArgumentException("Duplicate reward " + id);
	}

	public static void allowPoolReward(String id) {
		if (!REWARDS.containsKey(id) || id.equals("questplus:command")
				|| Set.of("questplus:choice", "questplus:random", "questplus:loot", "questplus:all").contains(id))
			throw new IllegalArgumentException("Reward cannot be used in pools");
		POOL_REWARDS.add(id);
	}

	public static boolean allowsPoolReward(String id) {
		return POOL_REWARDS.contains(id);
	}

	public static void validateReward(String id, JsonObject data) {
		var validator = REWARDS.get(id);
		if (validator == null)
			throw new IllegalArgumentException("Unknown reward " + id);
		validator.accept(data);
	}

	public static void validateSavedReward(QuestDefinition.Reward r) {
		if (r == null || r.type() == null)
			throw new IllegalArgumentException("Invalid reward");
		if (r.type().equals("item")) {
			if (!isId(r.item()) || r.item().equals("minecraft:air")
					|| r.count() < 1 || r.count() > 2304)
				throw new IllegalArgumentException("Invalid item reward");
			var data = JsonParser.parseString(r.data()).getAsJsonObject();
			ItemComponents.validateReward(data);
			if (data.has("randomBonus") && r.count() + data.get("randomBonus").getAsInt() > 2304)
				throw new IllegalArgumentException("Item bonus exceeds reward limit");
		} else if (r.type().equals("experience") || r.type().equals("experience_levels")) {
			if (r.amount() < 1 || r.amount() > 1_000_000)
				throw new IllegalArgumentException("Invalid experience reward");
		} else
			validateReward(r.type(), JsonParser.parseString(r.data()).getAsJsonObject());
	}

	private static void namespaced(String id) {
		if (!isId(id))
			throw new IllegalArgumentException("Extension IDs must be namespaced");
	}
}
