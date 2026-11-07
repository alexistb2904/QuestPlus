package dev.questplus.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;

/**
 * Optional API adapter. The JVM loads this class only when Team Reborn Energy
 * is installed. Fluid and vanilla objectives never load the external API.
 */
final class QuestEnergyBridge {
	private QuestEnergyBridge() {}

	static long extract(ContainerItemContext context, long amount, TransactionContext transaction) {
		var storage = context.find(EnergyStorage.ITEM);
		return storage == null ? 0 : storage.extract(amount, transaction);
	}
}
