package dev.questplus.fabric;

import dev.questplus.core.QuestEvent;
import dev.questplus.fabric.api.QuestPlusAdapters;
import dev.questplus.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.*;
import java.util.*;
import java.util.stream.Collectors;

public final class QuestPlusMod {
	public static final Logger LOGGER = LoggerFactory.getLogger("questplus");
	private static QuestRuntime runtime;
	private static volatile String startupError;

	public static String unavailableReason() {
		return startupError == null ? "Quest Plus has not loaded quest definitions" : startupError;
	}

	private static int networkTicks;
	private static QuestHubService hub;

	static QuestHubService hub() {
		if (hub == null)
			throw new IllegalStateException("QuestPlus Hub not initialized");
		return hub;
	}

	private record BreakTool(BlockPos position, String tool) {
	}

	private static final Map<UUID, BreakTool> BREAK_TOOLS = new HashMap<>();

	public static QuestRuntime runtime() {
		if (runtime == null)
			throw new IllegalStateException("Quest Plus unavailable: " + unavailableReason());
		return runtime;
	}

	private QuestPlusMod() {
	}

	/** Registers content, payloads and listeners once, while the loader accepts registry entries. */
	public static void initialize() {
		QuestCrateItem.register();
		QuestLootChest.register();
		QuestTabletItem.register();
		QuestWorldBlocks.register();
		QuestGiver.register();
		QuestCreativeTab.register();
		QuestNetwork.register();
		CommandRewards.install();
		TabletPoseNetwork.register();
		AdvancedEvents.register();
		ObservationEvents.register();
		ParityRewards.install();
		Platform.onRegisterCommands(QuestCommands::register);
		Platform.onServerStarted(server -> {
			try {
				runtime = new QuestRuntime(server);
				hub = new QuestHubService(server);
				QuestPlusAdapters.serverStarted();
				startupError = null;
				LOGGER.info("Quest Plus ready: {} validated quests", runtime.registry().all().size());
			} catch (Exception e) {
				startupError = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
				LOGGER.error("Quest Plus disabled: {}", startupError, e);
				server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(
						net.minecraft.network.chat.Component.literal("Quest Plus: " + startupError)));
			}
		});
		Platform.onServerStopping(server -> {
			QuestPlusAdapters.serverStopped();
			if (hub != null) {
				hub.close();
				hub = null;
			}
			if (runtime != null) {
				try {
					runtime.close();
				} catch (Exception e) {
					LOGGER.error("Quest Plus shutdown save failed", e);
				}
				runtime = null;
			}
			startupError = null;
			BREAK_TOOLS.clear();
			QuestNetwork.clear();
			TabletPoseNetwork.clear();
		});
		Platform.onServerTick(server -> {
			if (runtime != null) {
				runtime.tick();
				AdvancedEvents.tick(server);
				ObservationEvents.tick(server);
				QuestNetwork.tick(server, ++networkTicks);
				TabletPoseNetwork.tick(server);
				if (hub != null)
					hub.tick();
			}
		});
		Platform.onPlayerDisconnect((player, server) -> {
			if (runtime != null)
				runtime.disconnect(player.getUUID());
			QuestNetwork.disconnect(player.getUUID(), server);
			if (hub != null)
				hub.disconnect(player.getUUID());
			TabletPoseNetwork.disconnect(player);
			BREAK_TOOLS.remove(player.getUUID());
		});
		Platform.onBeforeBlockBreak((player, pos, state) -> {
			if (runtime != null && runtime.registry().hasEvent("block_break"))
				BREAK_TOOLS.put(player.getUUID(), new BreakTool(pos.immutable(),
						BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()));
		});
		Platform.onAfterBlockBreak((serverPlayer, pos, state) -> {
			var tool = BREAK_TOOLS.remove(serverPlayer.getUUID());
			if (runtime != null && runtime.registry().hasEvent("block_break")) {
				String target = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
				var tags = state.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet());
				if (runtime.registry().hasCandidate("block_break", target, tags)) {
					var context = context(serverPlayer);
					if (tool != null && tool.position().equals(pos))
						context.put("tool", tool.tool());
					runtime.event(serverPlayer, new QuestEvent("block_break", target, tags, 1, context));
				}
			}
		});
		Platform.onLivingDeath((entity, source) -> {
			if (runtime == null)
				return;
			runtime.dropCrate(entity);
			if (entity instanceof ServerPlayer dead)
				runtime.event(dead, new QuestEvent("player_death", "questplus:death", Set.of(), 1, context(dead)));
			var responsible = source.getEntity() instanceof ServerPlayer player ? player
					: entity.getKillCredit() instanceof ServerPlayer player ? player : null;
			if (responsible != null && responsible != entity && runtime.registry().hasEvent("entity_kill")) {
				String target = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				var tags = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).tags()
						.map(t -> t.location().toString()).collect(Collectors.toSet());
				if (runtime.registry().hasCandidate("entity_kill", target, tags))
					runtime.event(responsible, new QuestEvent("entity_kill", target, tags, 1, context(responsible)));
			}
		});
	}

	public static void pickedUp(ServerPlayer player, ItemStack item, int count) {
		if (runtime != null && count > 0)
			AdvancedEvents.item(player, "item_obtained", item, count);
	}

	public static Map<String, Object> context(ServerPlayer player) {
		var context = new HashMap<String, Object>(
				Map.of("dimension", player.level().dimension().identifier().toString(), "health", player.getHealth(),
						"time", System.currentTimeMillis(), "sneaking", player.isShiftKeyDown(), "tool",
						BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()));
		context.putAll(AdvancedEvents.context(player));
		context.put("gear", AdvancedEvents.gearSnapshot(player));
		context.put("stages", QuestStages.stages(player));
		return context;
	}
}
