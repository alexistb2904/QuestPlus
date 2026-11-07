package dev.questplus.fabric;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import team.reborn.energy.api.base.SimpleEnergyItem;

public final class FtbTestItems implements ModInitializer {
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
}
