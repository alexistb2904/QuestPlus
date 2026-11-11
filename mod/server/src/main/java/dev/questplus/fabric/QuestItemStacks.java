package dev.questplus.fabric;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.questplus.core.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.*;

final class QuestItemStacks {
	private QuestItemStacks() {
	}

	static ItemStack decode(JsonObject value, net.minecraft.core.HolderLookup.Provider registries) {
		var stack = value.deepCopy();
		stack.remove("type");
		stack.remove("text");
		stack.remove("onlyOne");
		stack.remove("randomBonus");
		stack.remove("weight");
		if (stack.has("item")) {
			stack.add("id", stack.remove("item"));
		}
		stack.addProperty("count", 1);
		var result = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), stack).getOrThrow();
		if (result.isEmpty())
			throw new IllegalArgumentException("Empty item reward");
		return result;
	}

	static JsonObject components(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
		var encoded = ItemStack.CODEC
				.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack.copyWithCount(1)).getOrThrow()
				.getAsJsonObject();
		return encoded.has("components") ? encoded.getAsJsonObject("components") : new JsonObject();
	}

	static boolean matches(QuestDefinition.Objective objective, ItemStack stack, ServerPlayer player) {
		return ItemComponents.matches(QuestExtensions.parsed(objective.data()).getAsJsonObject(),
				components(stack, player.registryAccess()));
	}

	static List<ItemStack> rewards(QuestDefinition.Reward reward, net.minecraft.core.HolderLookup.Provider registries) {
		var json = new JsonObject();
		json.addProperty("id", reward.item());
		var data = JsonParser.parseString(reward.data()).getAsJsonObject();
		if (data.has("components"))
			json.add("components", data.get("components"));
		var template = decode(json, registries);
		var result = new ArrayList<ItemStack>();
		int left = reward.count();
		while (left > 0) {
			int count = Math.min(left, template.getMaxStackSize());
			result.add(template.copyWithCount(count));
			left -= count;
		}
		return result;
	}

	static List<Map<String, Object>> snapshot(ServerPlayer player) {
		var result = new ArrayList<Map<String, Object>>();
		for (int slot = 0; slot < 37; slot++) {
			var stack = slot == 36 ? player.getOffhandItem() : player.getInventory().getItem(slot);
			if (stack.isEmpty())
				continue;
			result.add(Map.of("item",
					net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count",
					stack.getCount(), "tags",
					stack.typeHolder().tags().map(t -> t.location().toString())
							.collect(java.util.stream.Collectors.toSet()),
					"components", components(stack, player.registryAccess())));
		}
		return result;
	}
}
