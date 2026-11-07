package dev.questplus.fabric.api;

import dev.questplus.core.QuestEvent;
import dev.questplus.core.QuestExtensions;
import dev.questplus.core.QuestDefinition;
import dev.questplus.fabric.QuestRewardExtensions;
import com.google.gson.*;
import java.util.Map;
import java.util.function.*;
import dev.questplus.fabric.QuestPlusMod;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread entry point for trusted mods that supply normalized events. */
public final class QuestPlusApi {
	private QuestPlusApi() {
	}

	public static void submit(ServerPlayer responsible, QuestEvent event) {
		QuestPlusMod.runtime().event(responsible, event);
	}

	/**
	 * Report an actual successful fluid transfer performed by a trusted server mod.
	 * Amount is millibuckets (mB). Never call for attempted/failed transfers or
	 * client claims.
	 */
	public static void reportFluidTransferred(ServerPlayer responsible, String fluidId, long millibuckets) {
		if (responsible == null || fluidId == null || !fluidId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
				|| millibuckets <= 0 || millibuckets > 1_000_000_000L)
			throw new IllegalArgumentException("Invalid successful fluid transfer");
		submit(responsible, new QuestEvent("fluid_transfer", fluidId, java.util.Set.of(), millibuckets,
				QuestPlusMod.context(responsible)));
	}

	/**
	 * Report successfully transferred FE from a trusted power integration. The
	 * amount is FE, not stored energy capacity, and source consumption must have
	 * completed before reporting to avoid crediting failed operations.
	 */
	public static void reportEnergyTransferred(ServerPlayer responsible, long energy) {
		if (responsible == null || energy <= 0 || energy > 1_000_000_000L)
			throw new IllegalArgumentException("Invalid successful energy transfer");
		submit(responsible, new QuestEvent("energy_transfer", "questplus:fe", java.util.Set.of(), energy,
				QuestPlusMod.context(responsible)));
	}

	public static void registerEvent(String id, String registry, boolean state) {
		QuestExtensions.registerEvent(id, registry, state);
	}

	public static void registerObjectiveData(String event, Consumer<JsonObject> validator,
			BiPredicate<JsonObject, QuestEvent> matcher) {
		QuestExtensions.registerObjective(event, validator, matcher);
	}

	public static void registerStageProvider(dev.questplus.fabric.QuestStages.Provider provider) {
		dev.questplus.fabric.QuestStages.register(provider);
	}

	public static java.util.Set<String> stages(ServerPlayer player) {
		return dev.questplus.fabric.QuestStages.stages(player);
	}

	public static void setStage(ServerPlayer player, String stage, boolean present) {
		dev.questplus.fabric.QuestStages.set(player, stage, present);
	}

	public static void registerCondition(String id, Consumer<JsonElement> validator,
			BiPredicate<JsonElement, Map<String, Object>> predicate) {
		QuestExtensions.registerCondition(id, validator, predicate);
	}

	/**
	 * Add a validated no-code editor form for a previously registered custom event,
	 * condition or reward.
	 */
	public static void registerEditorDescriptor(String kind, String id, String label, JsonArray fields) {
		QuestStudioDescriptors.register(kind, id, label, fields);
	}

	public static void registerReward(String id, Consumer<JsonObject> validator,
			BiPredicate<ServerPlayer, QuestDefinition.Reward> fits,
			BiConsumer<ServerPlayer, QuestDefinition.Reward> deliver) {
		QuestRewardExtensions.register(id, validator, fits, deliver);
	}

	/**
	 * Explicitly opt a trusted handler into immutable pools; public previews
	 * contain only its type and authored label.
	 */
	public static void registerPoolReward(String id, Consumer<JsonObject> validator,
			BiPredicate<ServerPlayer, QuestDefinition.Reward> fits,
			BiConsumer<ServerPlayer, QuestDefinition.Reward> deliver) {
		registerReward(id, validator, fits, deliver);
		QuestExtensions.allowPoolReward(id);
	}
}
