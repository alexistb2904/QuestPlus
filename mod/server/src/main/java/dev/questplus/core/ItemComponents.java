package dev.questplus.core;

import com.google.gson.*;
import java.util.Set;

/**
 * Bounded authored component predicates. Minecraft validates the component
 * codecs at reload.
 */
public final class ItemComponents {
	private static final Set<String> ITEM_EVENTS = Set.of("inventory_count", "item_submit", "item_turn_in",
			"item_craft", "item_obtained", "item_smelt", "item_use", "item_consume", "fish_item", "trade");

	private ItemComponents() {
	}

	public static boolean supports(String event) {
		return ITEM_EVENTS.contains(event);
	}

	public static void validate(JsonObject data) {
		if (!Set.of("components", "matchComponents", "targets").containsAll(data.keySet())
				|| data.toString().length() > 16384)
			throw new IllegalArgumentException("Invalid item component data");
		if (data.has("components")) {
			if (!data.get("components").isJsonObject())
				throw new IllegalArgumentException("Expected item components");
			for (String key : data.getAsJsonObject("components").keySet())
				if (!key.matches("!?[a-z0-9_.-]+:[a-z0-9_./-]+"))
					throw new IllegalArgumentException("Invalid item component ID");
		}
		if (data.has("matchComponents") && (!data.get("matchComponents").isJsonPrimitive()
				|| !data.getAsJsonPrimitive("matchComponents").isString()
				|| !Set.of("none", "exact", "partial").contains(data.get("matchComponents").getAsString())))
			throw new IllegalArgumentException("Invalid component matching mode");
		if (data.has("targets")) {
			var targets = data.getAsJsonArray("targets");
			if (targets.isEmpty() || targets.size() > 32)
				throw new IllegalArgumentException("Invalid item filter size");
			for (var value : targets)
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
						|| !value.getAsString().matches("#?[a-z0-9_.-]+:[a-z0-9_./-]+"))
					throw new IllegalArgumentException("Invalid item filter target");
		}
	}

	public static void validateReward(JsonObject data) {
		var components = data.deepCopy();
		if (components.has("onlyOne")) {
			var value = components.remove("onlyOne");
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
				throw new IllegalArgumentException("Invalid unique-item policy");
		}
		if (components.has("randomBonus")) {
			var value = components.remove("randomBonus");
			if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
					|| value.getAsBigDecimal().intValueExact() < 0 || value.getAsInt() > 2303)
				throw new IllegalArgumentException("Invalid item random bonus");
		}
		if (components.has("matchComponents") || components.has("targets"))
			throw new IllegalArgumentException("Rewards do not accept component predicates");
		validate(components);
	}

	public static boolean matches(JsonObject data, JsonObject actual) {
		String mode = data.has("matchComponents") ? data.get("matchComponents").getAsString() : "none";
		if (mode.equals("none"))
			return true;
		var expected = data.has("components") ? data.getAsJsonObject("components") : new JsonObject();
		return mode.equals("exact") ? expected.equals(actual) : contains(expected, actual);
	}

	public static long inventoryCount(QuestDefinition.Objective objective, java.util.Map<String, Object> context,
			long fallback) {
		if (objective.data().equals("{}"))
			return fallback;
		if (!(context.get("itemStacks") instanceof java.util.List<?>stacks))
			return 0;
		var data = QuestExtensions.parsed(objective.data()).getAsJsonObject();
		long result = 0;
		var targets = targets(objective);
		for (Object entry : stacks)
			if (entry instanceof java.util.Map<?, ?>stack
					&& targets.stream().anyMatch(target -> target.equals(stack.get("item")) || target.startsWith("#")
							&& stack.get("tags") instanceof java.util.Set<?>tags && tags.contains(target.substring(1)))
					&& stack.get("components") instanceof JsonObject components && matches(data, components)
					&& stack.get("count") instanceof Number count)
				result += count.longValue();
		return result;
	}

	public static java.util.Set<String> targets(QuestDefinition.Objective objective) {
		if (!supports(objective.event()))
			return Set.of(objective.target());
		var data = QuestExtensions.parsed(objective.data()).getAsJsonObject();
		if (!data.has("targets"))
			return Set.of(objective.target());
		var result = new java.util.LinkedHashSet<String>();
		for (var value : data.getAsJsonArray("targets"))
			result.add(value.getAsString());
		return java.util.Collections.unmodifiableSet(result);
	}

	private static boolean contains(JsonElement expected, JsonElement actual) {
		if (expected.isJsonObject()) {
			if (actual == null || !actual.isJsonObject())
				return false;
			for (var entry : expected.getAsJsonObject().entrySet())
				if (!contains(entry.getValue(), actual.getAsJsonObject().get(entry.getKey())))
					return false;
			return true;
		}
		return expected.equals(actual);
	}
}
