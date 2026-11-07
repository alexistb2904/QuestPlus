package dev.questplus.fabric.api;

import dev.questplus.core.QuestEvent;
import dev.questplus.core.QuestExtensions;
import dev.questplus.core.QuestDefinition;
import dev.questplus.core.PlayerProgress;
import dev.questplus.fabric.QuestRewardExtensions;
import com.google.gson.*;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.function.*;
import dev.questplus.fabric.QuestPlusMod;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread entry point for trusted mods that supply normalized events. */
public final class QuestPlusApi {
	private static final Gson GSON = new Gson();

	/** A detached view of collective quest progress; it contains no reward ledger. */
	public record Progress(boolean started, boolean completed, boolean terminal, long cycles, int tier,
			Map<String, Long> objectives) {
		public Progress {
			objectives = Map.copyOf(objectives);
		}
	}

	private QuestPlusApi() {
	}

	private static void requireServerThread(ServerPlayer player) {
		if (player == null || !player.level().getServer().isSameThread())
			throw new IllegalStateException("Quest Plus requires a player on the server thread");
	}

	/** Returns a detached representation of the currently loaded definition. */
	public static JsonObject getQuest(ServerPlayer player, String questId) {
		requireServerThread(player);
		return GSON.toJsonTree(QuestPlusMod.runtime().registry().get(questId)).getAsJsonObject();
	}

	/** Returns the currently loaded quest IDs in stable order. */
	public static List<String> getQuests(ServerPlayer player) {
		requireServerThread(player);
		return QuestPlusMod.runtime().registry().all().stream().map(QuestDefinition::id).sorted().toList();
	}

	/** Returns a snapshot of the player's own or team's progress. */
	public static Progress getProgress(ServerPlayer player, String questId) throws IOException {
		requireServerThread(player);
		var runtime = QuestPlusMod.runtime();
		runtime.registry().get(questId);
		return snapshot(runtime.state(player.getUUID()).quests.get(questId));
	}

	static Progress snapshot(PlayerProgress.QuestProgress progress) {
		if (progress == null)
			return new Progress(false, false, false, 0, 0, Map.of());
		boolean started = progress.completed || progress.attemptId != null || progress.startedAt > 0
				|| progress.counters.values().stream().anyMatch(value -> value > 0);
		return new Progress(started, progress.completed, progress.terminal, progress.cycles, progress.tier,
				progress.counters);
	}

	/** Returns zero for an unstarted objective and rejects unknown objective IDs. */
	public static long getObjectiveProgress(ServerPlayer player, String questId, String objectiveId) throws IOException {
		requireServerThread(player);
		var quest = QuestPlusMod.runtime().registry().get(questId);
		if (quest.objectives().stream().noneMatch(objective -> objective.id().equals(objectiveId)))
			throw new IllegalArgumentException("Unknown objective: " + objectiveId);
		return getProgress(player, questId).objectives().getOrDefault(objectiveId, 0L);
	}

	/** Administrative grant through the same team and reward path as the server command. */
	public static void completeQuest(ServerPlayer player, String questId) throws IOException {
		requireServerThread(player);
		var runtime = QuestPlusMod.runtime();
		var quest = runtime.registry().get(questId);
		validateCompletionType(quest.progression());
		var progress = runtime.state(player.getUUID()).quests.get(questId);
		if (progress != null && (progress.terminal || progress.waitingRestart
				|| (quest.progression().equals("once") && progress.completed)))
			throw new IllegalStateException("Quest is already complete or awaiting restart");
		runtime.mutate(player.getUUID(), questId, "grant");
	}

	static void validateCompletionType(String progression) {
		if (!progression.equals("once") && !progression.equals("repeatable"))
			throw new IllegalArgumentException("Administrative completion does not support this progression type");
	}

	/** Reset shared team progress, preserving the existing reward claim ledger. */
	public static void resetQuest(ServerPlayer player, String questId) throws IOException {
		requireServerThread(player);
		QuestPlusMod.runtime().mutate(player.getUUID(), questId, "reset");
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
