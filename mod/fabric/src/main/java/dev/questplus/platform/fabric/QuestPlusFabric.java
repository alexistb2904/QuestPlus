package dev.questplus.platform.fabric;

import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.api.ModInitializer;

public final class QuestPlusFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		QuestPlusMod.initialize();
	}
}
