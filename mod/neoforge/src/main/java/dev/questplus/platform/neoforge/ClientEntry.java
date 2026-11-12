package dev.questplus.platform.neoforge;

import dev.questplus.fabric.client.QuestPlusClient;
import net.neoforged.bus.api.IEventBus;

/** Client-only half of {@link QuestPlusNeoForge}, in its own class so dedicated servers never load it. */
final class ClientEntry {
	private ClientEntry() {}

	static void attach(IEventBus modBus) {
		ClientModBus.attach(modBus);
	}

	static void initialize() {
		QuestPlusClient.initialize();
	}
}
