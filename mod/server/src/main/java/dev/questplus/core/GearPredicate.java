package dev.questplus.core;

import com.google.gson.*;
import java.util.*;

/**
 * Server-supplied item snapshots only. No client-originated equipment state is
 * trusted. A requirement uses one exact inventory stack or one exact equipped
 * slot.
 */
public final class GearPredicate {
	private static final Set<String> SLOTS = Set.of("inventory", "head", "chest", "legs", "feet", "mainhand",
			"offhand");
	private static final String IDENTIFIER = "[a-z0-9_.-]+:[a-z0-9_./-]+";

	private GearPredicate() {
	}

	public static void validate(JsonElement source) {
		if (!source.isJsonObject())
			throw new IllegalArgumentException("questplus:gear needs an object");
		JsonObject data = source.getAsJsonObject();
		if (!data.keySet().equals(Set.of("items")) || !data.get("items").isJsonArray())
			throw new IllegalArgumentException("questplus:gear needs items array");
		JsonArray items = data.getAsJsonArray("items");
		if (items.isEmpty() || items.size() > 40)
			throw new IllegalArgumentException("gear requires 1..40 items");
		for (JsonElement element : items) {
			if (!element.isJsonObject())
				throw new IllegalArgumentException("Invalid gear requirement");
			JsonObject item = element.getAsJsonObject();
			if (!item.keySet().containsAll(Set.of("slot", "item"))
					|| !Set.of("slot", "item", "enchantments").containsAll(item.keySet())
					|| !item.get("slot").isJsonPrimitive() || !SLOTS.contains(item.get("slot").getAsString())
					|| !item.get("item").isJsonPrimitive() || !item.get("item").getAsString().matches(IDENTIFIER))
				throw new IllegalArgumentException("Invalid gear slot or item");
			if (item.has("enchantments")) {
				if (!item.get("enchantments").isJsonObject())
					throw new IllegalArgumentException("Invalid enchantments");
				for (var enchant : item.getAsJsonObject("enchantments").entrySet()) {
					if (!enchant.getKey().matches(IDENTIFIER) || !enchant.getValue().isJsonPrimitive()
							|| !enchant.getValue().getAsJsonPrimitive().isNumber())
						throw new IllegalArgumentException("Invalid enchantment");
					try {
						int level = enchant.getValue().getAsBigDecimal().intValueExact();
						if (level < 1 || level > 10)
							throw new IllegalArgumentException("Invalid enchantment level");
					} catch (ArithmeticException invalid) {
						throw new IllegalArgumentException("Invalid enchantment level", invalid);
					}
				}
			}
		}
	}

	public static boolean test(JsonElement raw, Map<String, Object> context) {
		if (!(context.get("gear") instanceof Map<?, ?>snapshot))
			return false;
		JsonArray requirements = raw.getAsJsonObject().getAsJsonArray("items");
		List<Map<?, ?>> inventory = items(snapshot.get("inventory"));
		// Reserve distinct stacks: two differently enchanted pickaxes cannot be
		// satisfied by one stack, even when both have the same base item ID.
		List<JsonObject> inventoryRequirements = new ArrayList<>();
		for (JsonElement element : requirements) {
			JsonObject requirement = element.getAsJsonObject();
			String slot = requirement.get("slot").getAsString();
			if (slot.equals("inventory"))
				inventoryRequirements.add(requirement);
			else if (items(snapshot.get(slot)).stream().noneMatch(stack -> matches(stack, requirement)))
				return false;
		}
		return matchUnique(inventoryRequirements, 0, inventory, new boolean[inventory.size()]);
	}

	private static boolean matchUnique(List<JsonObject> requirements, int index, List<Map<?, ?>> stacks,
			boolean[] used) {
		if (index >= requirements.size())
			return true;
		for (int i = 0; i < stacks.size(); i++) {
			if (used[i] || !matches(stacks.get(i), requirements.get(index)))
				continue;
			used[i] = true;
			if (matchUnique(requirements, index + 1, stacks, used))
				return true;
			used[i] = false;
		}
		return false;
	}

	private static boolean matches(Map<?, ?> stack, JsonObject requirement) {
		if (!Objects.equals(stack.get("item"), requirement.get("item").getAsString()))
			return false;
		if (!requirement.has("enchantments"))
			return true;
		if (!(stack.get("enchantments") instanceof Map<?, ?>enchantments))
			return false;
		for (var entry : requirement.getAsJsonObject("enchantments").entrySet()) {
			Object level = enchantments.get(entry.getKey());
			if (!(level instanceof Number number) || number.intValue() < entry.getValue().getAsInt())
				return false;
		}
		return true;
	}

	private static List<Map<?, ?>> items(Object value) {
		if (!(value instanceof List<?>raw))
			return List.of();
		List<Map<?, ?>> out = new ArrayList<>();
		for (Object item : raw)
			if (item instanceof Map<?, ?>mapped)
				out.add(mapped);
		return out;
	}
}
