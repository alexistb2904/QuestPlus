package dev.questplus.platform.fabric;

import dev.questplus.fabric.client.QuestPlusClient;
import net.fabricmc.api.ClientModInitializer;

public final class QuestPlusFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		QuestPlusClient.initialize();
	}
}
