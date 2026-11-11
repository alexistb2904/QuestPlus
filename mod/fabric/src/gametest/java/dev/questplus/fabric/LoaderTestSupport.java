package dev.questplus.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyItem;

/**
 * Fabric side of the loader-specific GameTest helpers ({@code mod/neoforge} has the NeoForge one):
 * the test battery item and transactional access to station capabilities.
 */
public final class LoaderTestSupport implements ModInitializer {
	static Item battery;

	private static final class Battery extends Item implements SimpleEnergyItem {
		Battery(Properties properties) {
			super(properties);
		}

		public long getEnergyCapacity(ItemStack stack) {
			return 10000;
		}

		public long getEnergyMaxInput(ItemStack stack) {
			return 10000;
		}

		public long getEnergyMaxOutput(ItemStack stack) {
			return 10000;
		}
	}

	public void onInitialize() {
		var key = ResourceKey.create(Registries.ITEM, Identifier.parse("questplus-test:battery"));
		battery = Registry.register(BuiltInRegistries.ITEM, key,
				new Battery(new Item.Properties().setId(key).stacksTo(1)));
	}

	static ItemStack battery(long energy) {
		var stack = new ItemStack(battery);
		stack.set(EnergyStorage.ENERGY_COMPONENT, energy);
		return stack;
	}

	static long energy(ItemStack stack) {
		return stack.getOrDefault(EnergyStorage.ENERGY_COMPONENT, 0L);
	}

	static long insertItems(QuestUtilityEntity be, Item item, int count) {
		try (var tx = Transaction.openOuter()) {
			long inserted = QuestStationStorage.items(be).insert(ItemVariant.of(item), count, tx);
			tx.commit();
			return inserted;
		}
	}

	static long insertFluid(QuestUtilityEntity be, Fluid fluid, long droplets) {
		try (var tx = Transaction.openOuter()) {
			long inserted = QuestStationStorage.fluids(be).insert(FluidVariant.of(fluid), droplets, tx);
			tx.commit();
			return inserted;
		}
	}

	/** The capabilities a station face exposes now. Each call runs in its own transaction. Fluids in droplets. */
	static Probe probe(Level level, BlockPos pos, Direction side) {
		return new Probe(ItemStorage.SIDED.find(level, pos, side), FluidStorage.SIDED.find(level, pos, side),
				EnergyStorage.SIDED.find(level, pos, side));
	}

	record Probe(Storage<ItemVariant> items, Storage<FluidVariant> fluids, EnergyStorage energy) {
		boolean hasItems() { return items != null; }
		boolean hasEnergy() { return energy != null; }
		long insertItems(Item item, long count, boolean commit) { return run(commit, tx -> items.insert(ItemVariant.of(item), count, tx)); }
		long extractItems(Item item, long count, boolean commit) { return run(commit, tx -> items.extract(ItemVariant.of(item), count, tx)); }
		long insertFluid(Fluid fluid, long droplets, boolean commit) { return run(commit, tx -> fluids.insert(FluidVariant.of(fluid), droplets, tx)); }
		long extractFluid(Fluid fluid, long droplets, boolean commit) { return run(commit, tx -> fluids.extract(FluidVariant.of(fluid), droplets, tx)); }
		long insertEnergy(long amount, boolean commit) { return run(commit, tx -> energy.insert(amount, tx)); }
		long extractEnergy(long amount, boolean commit) { return run(commit, tx -> energy.extract(amount, tx)); }

		private static long run(boolean commit, java.util.function.ToLongFunction<Transaction> action) {
			try (var tx = Transaction.openOuter()) {
				long result = action.applyAsLong(tx);
				if (commit) tx.commit();
				return result;
			}
		}
	}
}
