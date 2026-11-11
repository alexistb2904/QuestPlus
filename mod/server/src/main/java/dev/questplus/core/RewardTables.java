package dev.questplus.core;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves authored weighted rewards once, at quest completion. Entitlements
 * store the selected concrete rewards, so claiming never rolls again on retry
 * or reconnect. Commands and nested pools are forbidden. Trusted extensions
 * must explicitly opt in.
 */
public final class RewardTables {
	private RewardTables() {
	}

	public static void validate(JsonObject data) {
		if (!Set.of("options", "rolls", "description").containsAll(data.keySet()))
			throw new IllegalArgumentException("Random rewards require options, optional rolls/description");
		description(data);
		JsonArray options = data.getAsJsonArray("options");
		if (options == null || options.isEmpty() || options.size() > 32)
			throw new IllegalArgumentException("Random reward table requires 1..32 options");
		if (data.has("rolls"))
			integer(data, "rolls", 0, 16);
		long total = 0;
		for (var element : options) {
			if (!element.isJsonObject())
				throw new IllegalArgumentException("Invalid weighted reward entry");
			var option = element.getAsJsonObject();
			total += integer(option, "weight", 0, 1_000_000);
			validateOption(option, true);
		}
		if (total > 32_000_000L)
			throw new IllegalArgumentException("Random table weights exceed limit");
	}

	private static void description(JsonObject data) {
		if (data.has("description"))
			translations(data.get("description"), 1024);
	}

	private static void translations(JsonElement value, int limit) {
		if (!value.isJsonObject() || !value.getAsJsonObject().has("en_us") || value.getAsJsonObject().size() > 128)
			throw new IllegalArgumentException("Reward translation requires English");
		for (var entry : value.getAsJsonObject().entrySet())
			if (!entry.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || !entry.getValue().isJsonPrimitive()
					|| !entry.getValue().getAsJsonPrimitive().isString() || entry.getValue().getAsString().isBlank()
					|| entry.getValue().getAsString().length() > limit)
				throw new IllegalArgumentException("Invalid reward translation");
	}

	private static void validateOption(JsonObject option, boolean weighted) {
		var plain = option.deepCopy();
		if (weighted)
			plain.remove("weight");
		if (plain.has("text")) {
			translations(plain.get("text"), 256);
			plain.remove("text");
		}
		String type = plain.has("type") ? plain.get("type").getAsString() : "";
		if (type.equals("item")) {
			if (!Set.of("type", "item", "count", "components", "onlyOne", "randomBonus").containsAll(plain.keySet())
					|| !plain.keySet().containsAll(Set.of("type", "item", "count"))
					|| !plain.get("item").isJsonPrimitive() || !plain.get("item").getAsJsonPrimitive().isString()
					|| !plain.get("item").getAsString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
					|| plain.get("item").getAsString().equals("minecraft:air"))
				throw new IllegalArgumentException("Invalid item option");
			integer(plain, "count", 1, 2304);
			var data = new JsonObject();
			for (String key : List.of("components", "onlyOne", "randomBonus"))
				if (plain.has(key))
					data.add(key, plain.get(key));
			ItemComponents.validateReward(data);
			if (plain.has("randomBonus") && plain.get("count").getAsInt() + plain.get("randomBonus").getAsInt() > 2304)
				throw new IllegalArgumentException("Item bonus exceeds reward limit");
		} else if (Set.of("experience", "experience_levels").contains(type)) {
			if (!plain.keySet().equals(Set.of("type", "amount")))
				throw new IllegalArgumentException("Invalid XP option");
			integer(plain, "amount", 1, 1_000_000);
		} else if (Set.of("questplus:advancement", "questplus:message", "questplus:stage", "questplus:toast")
				.contains(type)) {
			if (!plain.keySet().equals(Set.of("type", "data")))
				throw new IllegalArgumentException("Invalid reward option");
			QuestExtensions.validateReward(type, plain.getAsJsonObject("data"));
		} else if (QuestExtensions.allowsPoolReward(type)) {
			if (!plain.keySet().equals(Set.of("type", "data")))
				throw new IllegalArgumentException("Invalid extension reward option");
			QuestExtensions.validateReward(type, plain.getAsJsonObject("data"));
		} else
			throw new IllegalArgumentException("Unsupported reward option; commands and nested pools are forbidden");
	}

	/**
	 * Loot pools may intentionally award nothing. Random pools always award an
	 * option.
	 */
	public static void validateLoot(JsonObject data) {
		if (!data.has("emptyWeight"))
			throw new IllegalArgumentException("Loot requires an explicit emptyWeight");
		var normalized = data.deepCopy();
		normalized.remove("emptyWeight");
		validate(normalized);
		integer(data, "emptyWeight", 0, 1_000_000);
	}

	/** All reward pools grant each entry once, ignoring weights. */
	public static void validateAll(JsonObject data) {
		if (!Set.of("options", "description").containsAll(data.keySet()))
			throw new IllegalArgumentException("All-table rewards require options");
		if (data.has("options") && data.get("options").isJsonArray() && data.getAsJsonArray("options").isEmpty()) {
			description(data);
			return;
		}
		validate(data);
	}

	/**
	 * Player-facing choice reward: no randomness, no commands, one server-selected
	 * option.
	 */
	public static void validateChoice(JsonObject data) {
		if (!Set.of("options", "description").containsAll(data.keySet()))
			throw new IllegalArgumentException("Choice reward requires options");
		description(data);
		var options = data.getAsJsonArray("options");
		if (options == null || options.isEmpty() || options.size() > 32)
			throw new IllegalArgumentException("Choice rewards require 1..32 options");
		for (var value : options) {
			if (!value.isJsonObject())
				throw new IllegalArgumentException("Invalid choice option");
			validateOption(value.getAsJsonObject(), false);
		}
	}

	/** Choice is converted into a concrete immutable entitlement before saving. */
	public static PlayerProgress.RewardGrant choose(PlayerProgress.RewardGrant grant, int slot, int option) {
		if (slot < 0 || slot >= grant.rewards().size())
			throw new IllegalArgumentException("Invalid choice slot");
		var rewards = new ArrayList<>(grant.rewards());
		rewards.set(slot, choose(rewards.get(slot), option));
		return new PlayerProgress.RewardGrant(grant.questId(), List.copyOf(rewards));
	}

	public static QuestDefinition.Reward choose(QuestDefinition.Reward authored, int index) {
		if (!authored.type().equals("questplus:choice"))
			throw new IllegalArgumentException("Not a choice reward");
		var data = JsonParser.parseString(authored.data()).getAsJsonObject();
		validateChoice(data);
		var options = data.getAsJsonArray("options");
		if (index < 0 || index >= options.size())
			throw new IllegalArgumentException("Choice index outside 0.." + (options.size() - 1));
		return resolveItem(
				concrete(options.get(index).getAsJsonObject(), authored.text()).withPolicy(authored.policy()));
	}

	private static QuestDefinition.Reward concrete(JsonObject option, Map<String, String> text) {
		String type = option.get("type").getAsString();
		if (option.has("text")) {
			var translated = new HashMap<String, String>();
			option.getAsJsonObject("text").entrySet()
					.forEach(e -> translated.put(e.getKey(), e.getValue().getAsString()));
			text = Map.copyOf(translated);
		}
		return switch (type) {
		case "item" -> {
			var data = new JsonObject();
			for (String key : List.of("components", "onlyOne", "randomBonus"))
				if (option.has(key))
					data.add(key, option.get(key));
			yield new QuestDefinition.Reward(type, option.get("item").getAsString(), option.get("count").getAsInt(), 0,
					data.toString(), text);
		}
		case "experience", "experience_levels" -> new QuestDefinition.Reward(type, null, 0,
				option.get("amount").getAsInt(), "{}", text);
		default -> new QuestDefinition.Reward(type, null, 0, 0, option.getAsJsonObject("data").toString(), text);
		};
	}

	private static int integer(JsonObject object, String key, int min, int max) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber())
			throw new IllegalArgumentException("Missing integer " + key);
		int result;
		try {
			result = object.get(key).getAsBigDecimal().intValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("Invalid integer " + key, e);
		}
		if (result < min || result > max)
			throw new IllegalArgumentException("Integer out of range " + key);
		return result;
	}

	public static List<QuestDefinition.Reward> resolve(List<QuestDefinition.Reward> authored) {
		return resolve(authored, new double[] { 1 });
	}

	/**
	 * Same roll. {@code rarest[0]} is lowered to the smallest chance of any option
	 * actually drawn from weights, so rarity is decided on the server with the roll.
	 */
	public static List<QuestDefinition.Reward> resolve(List<QuestDefinition.Reward> authored, double[] rarest) {
		List<QuestDefinition.Reward> selected = new ArrayList<>();
		for (var reward : authored) {
			if (!Set.of("questplus:random", "questplus:loot", "questplus:all").contains(reward.type())) {
				selected.add(reward);
				continue;
			}
			JsonObject data = JsonParser.parseString(reward.data()).getAsJsonObject();
			if (reward.type().equals("questplus:loot"))
				validateLoot(data);
			else if (reward.type().equals("questplus:all"))
				validateAll(data);
			else
				validate(data);
			JsonArray options = data.getAsJsonArray("options");
			if (reward.type().equals("questplus:all")) {
				for (var option : options)
					selected.add(concrete(option.getAsJsonObject(), reward.text()).withPolicy(reward.policy()));
				continue;
			}
			long total = 0;
			for (var choice : options)
				total += choice.getAsJsonObject().get("weight").getAsLong();
			long weightedTotal = total
					+ (reward.type().equals("questplus:loot") ? data.get("emptyWeight").getAsInt() : 0);
			if (weightedTotal == 0)
				continue;
			for (var entry : options)
				if (entry.getAsJsonObject().get("weight").getAsLong() == 0)
					selected.add(concrete(entry.getAsJsonObject(), reward.text()).withPolicy(reward.policy()));
			int rolls = data.has("rolls") ? data.get("rolls").getAsInt() : 1;
			for (int i = 0; i < rolls; i++) {
				long ticket = ThreadLocalRandom.current().nextLong(weightedTotal);
				if (ticket >= total)
					continue; // Explicitly empty loot roll.
				for (var entry : options) {
					JsonObject option = entry.getAsJsonObject();
					ticket -= option.get("weight").getAsLong();
					if (ticket >= 0)
						continue;
					selected.add(concrete(option, reward.text()).withPolicy(reward.policy()));
					rarest[0] = Math.min(rarest[0], option.get("weight").getAsDouble() / weightedTotal);
					break;
				}
			}
		}
		if (selected.size() > 64)
			throw new IllegalArgumentException("Random rewards exceed entitlement limit");
		return selected.stream().map(RewardTables::resolveItem).toList();
	}

	/** Loot tier 0 common, 1 uncommon, 2 rare, 3 epic, 4 legendary from a roll's chance. */
	public static int rarity(double chance) {
		return chance <= .01 ? 4 : chance <= .05 ? 3 : chance <= .15 ? 2 : chance <= .4 ? 1 : 0;
	}

	private static QuestDefinition.Reward resolveItem(QuestDefinition.Reward reward) {
		if (!reward.type().equals("item"))
			return reward;
		var data = JsonParser.parseString(reward.data()).getAsJsonObject();
		if (!data.has("randomBonus"))
			return reward;
		int bonus = data.remove("randomBonus").getAsInt();
		return new QuestDefinition.Reward("item", reward.item(),
				reward.count() + ThreadLocalRandom.current().nextInt(bonus + 1), 0, data.toString(), reward.text(),
				reward.policy());
	}
}
