package dev.questplus.fabric;

import com.mojang.serialization.Codec;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * NeoForge side of the loader-specific GameTest helpers ({@code mod/fabric} has the Fabric one): a test
 * battery using NeoForge's item energy capability and transactional access to station capabilities.
 * Fluid amounts are droplets (81 per millibucket) like on Fabric, so shared tests assert the same numbers.
 */
public final class LoaderTestSupport {
	static Item battery;
	private static DataComponentType<Integer> energy;

	private LoaderTestSupport() {}

	/** Called during NeoForge's registry window by the test mod. */
	public static void register() {
		energy = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Identifier.parse("questplus-test:energy"),
				DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		var key = ResourceKey.create(Registries.ITEM, Identifier.parse("questplus-test:battery"));
		battery = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
	}

	public static void registerCapabilities(RegisterCapabilitiesEvent event) {
		event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, energy, 10000), battery);
	}

	static ItemStack battery(long amount) {
		var stack = new ItemStack(battery);
		stack.set(energy, (int) amount);
		return stack;
	}

	static long energy(ItemStack stack) {
		return stack.getOrDefault(energy, 0);
	}

	static long insertItems(QuestUtilityEntity be, Item item, int count) {
		return Probe.run(true, tx -> QuestStationStorage.items(be).insert(0, ItemResource.of(item), count, tx));
	}

	static long insertFluid(QuestUtilityEntity be, Fluid fluid, long droplets) {
		return 81L * Probe.run(true, tx -> QuestStationStorage.fluids(be).insert(0, FluidResource.of(fluid), (int) (droplets / 81), tx));
	}

	/** The capabilities a station face exposes now. Each call runs in its own transaction. Fluids in droplets. */
	static Probe probe(Level level, BlockPos pos, Direction side) {
		return new Probe(level.getCapability(Capabilities.Item.BLOCK, pos, side), level.getCapability(Capabilities.Fluid.BLOCK, pos, side),
				level.getCapability(Capabilities.Energy.BLOCK, pos, side));
	}

	record Probe(ResourceHandler<ItemResource> items, ResourceHandler<FluidResource> fluids, EnergyHandler energy) {
		boolean hasItems() { return items != null; }
		boolean hasEnergy() { return energy != null; }
		long insertItems(Item item, long count, boolean commit) { return run(commit, tx -> items.insert(ItemResource.of(item), (int) count, tx)); }
		long extractItems(Item item, long count, boolean commit) { return run(commit, tx -> items.extract(ItemResource.of(item), (int) count, tx)); }
		long insertFluid(Fluid fluid, long droplets, boolean commit) { return 81L * run(commit, tx -> fluids.insert(FluidResource.of(fluid), (int) (droplets / 81), tx)); }
		long extractFluid(Fluid fluid, long droplets, boolean commit) { return 81L * run(commit, tx -> fluids.extract(FluidResource.of(fluid), (int) (droplets / 81), tx)); }
		long insertEnergy(long amount, boolean commit) { return run(commit, tx -> energy.insert((int) amount, tx)); }
		long extractEnergy(long amount, boolean commit) { return run(commit, tx -> energy.extract((int) amount, tx)); }

		private static int run(boolean commit, ToIntFunction<Transaction> action) {
			try (var tx = Transaction.openRoot()) {
				int result = action.applyAsInt(tx);
				if (commit) tx.commit();
				return result;
			}
		}
	}
}
