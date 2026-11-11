package dev.questplus.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

final class QuestResourceContainers {
	record Plan(List<ItemStack> stacks, int amount) {
	}

	private QuestResourceContainers() {
	}

	static Plan extract(List<ItemStack> inventory, String fluid, long requested, int energyRatio) {
		var slots = new ItemStacksResourceHandler(NonNullList.of(ItemStack.EMPTY, inventory.stream().map(ItemStack::copy).toArray(ItemStack[]::new)));
		long target = fluid == null ? requested / energyRatio : requested, extracted = 0;
		try (var transaction = Transaction.openRoot()) {
			for (int i = 0; i < slots.size() && extracted < target; i++) {
				var access = ItemAccess.forHandlerIndex(slots, i);
				int wanted = (int) Math.min(Integer.MAX_VALUE, target - extracted);
				if (fluid == null) {
					var energy = access.getCapability(Capabilities.Energy.ITEM);
					if (energy != null) extracted += energy.extract(wanted, transaction);
				} else {
					var storage = access.getCapability(Capabilities.Fluid.ITEM);
					if (storage == null)
						continue;
					for (int tank = 0; tank < storage.size() && extracted < target; tank++) {
						var resource = storage.getResource(tank);
						if (!resource.isEmpty() && BuiltInRegistries.FLUID.getKey(resource.getFluid()).toString().equals(fluid))
							extracted += storage.extract(tank, resource, (int) Math.min(Integer.MAX_VALUE, target - extracted), transaction);
					}
				}
			}
			if (extracted <= 0)
				throw new IllegalArgumentException("No extractable whole resource units in inventory containers");
			transaction.commit();
		}
		long amount = fluid == null ? Math.multiplyExact(extracted, energyRatio) : extracted;
		if (amount > requested || amount > 1_000_000_000L)
			throw new IllegalStateException("Resource provider exceeded requested amount");
		var after = new ArrayList<ItemStack>(slots.size());
		for (int i = 0; i < slots.size(); i++)
			after.add(slots.getResource(i).toStack(slots.getAmountAsInt(i)));
		return new Plan(List.copyOf(after), Math.toIntExact(amount));
	}
}
