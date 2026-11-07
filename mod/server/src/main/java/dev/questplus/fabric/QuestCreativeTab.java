package dev.questplus.fabric;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class QuestCreativeTab {
	private QuestCreativeTab() {
	}

	static void register() {
		var tablet = BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:tablet"));
		var crate = BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:loot_crate"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.parse("questplus:quest"),
				FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.questplus.quest"))
						.icon(tablet::getDefaultInstance).displayItems((parameters, output) -> {
							output.accept(tablet);
							output.accept(crate);
						}).build());
	}
}
