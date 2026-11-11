package dev.questplus.platform;

import com.mojang.brigadier.CommandDispatcher;
import dev.questplus.platform.neoforge.ModBus;
import dev.questplus.platform.neoforge.QuestPlusNeoForge;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge implementation of the gameplay hooks. Same public methods as {@code mod/fabric}'s
 * (checked by {@code verifyPlatformApi}).
 */
public final class Platform {
	private Platform() {}

	public static void onServerStarted(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> listener.accept(event.getServer()));
	}

	public static void onServerStopping(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.accept(event.getServer()));
	}

	public static void onServerStopped(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> listener.accept(event.getServer()));
	}

	public static void onServerTick(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.accept(event.getServer()));
	}

	public static void onPlayerJoin(Consumer<ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) listener.accept(player);
		});
	}

	public static void onPlayerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) listener.accept(player, player.level().getServer());
		});
	}

	public static void onChunkGenerated(BiConsumer<ServerLevel, LevelChunk> listener) {
		NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
			if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level) listener.accept(level, event.getChunk());
		});
	}

	public static void onStartTracking(BiConsumer<Entity, ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
			if (event.getEntity() instanceof ServerPlayer viewer) listener.accept(event.getTarget(), viewer);
		});
	}

	public static void onStopTracking(BiConsumer<Entity, ServerPlayer> listener) {
		NeoForge.EVENT_BUS.addListener((PlayerEvent.StopTracking event) -> {
			if (event.getEntity() instanceof ServerPlayer viewer) listener.accept(event.getTarget(), viewer);
		});
	}

	public static void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> listener.accept(event.getDispatcher()));
	}

	/** Observes only. Lowest priority: mods that cancel the break run first. */
	public static void onBeforeBlockBreak(Hooks.BlockBreak listener) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BreakBlockEvent event) -> {
			if (event.getPlayer() instanceof ServerPlayer player) listener.broken(player, event.getPos(), event.getState());
		});
	}

	/** Fired by {@code ServerPlayerGameModeMixin}: NeoForge has no post-break event. */
	public static void onAfterBlockBreak(Hooks.BlockBreak listener) {
		QuestPlusNeoForge.AFTER_BREAK.add(listener);
	}

	/** Lowest priority, and skipped when another mod cancelled the death, like Fabric's AFTER_DEATH. */
	public static void onLivingDeath(BiConsumer<LivingEntity, DamageSource> listener) {
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> listener.accept(event.getEntity(), event.getSource()));
	}

	public static void onUseItem(Hooks.Use listener) {
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) ->
				use(event, listener.use(event.getEntity(), event.getLevel(), event.getHand()), event::setCancellationResult));
	}

	public static void onUseBlock(Hooks.Use listener) {
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) ->
				use(event, listener.use(event.getEntity(), event.getLevel(), event.getHand()), event::setCancellationResult));
	}

	public static void onUseEntity(Hooks.Use listener) {
		NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) ->
				use(event, listener.use(event.getEntity(), event.getLevel(), event.getHand()), event::setCancellationResult));
	}

	// Like Fabric's use callbacks: any result but PASS replaces vanilla handling.
	private static void use(ICancellableEvent event, InteractionResult result, Consumer<InteractionResult> cancellation) {
		if (result == InteractionResult.PASS || event.isCanceled()) return;
		event.setCanceled(true);
		cancellation.accept(result);
	}

	public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory,
			Block... blocks) {
		return new BlockEntityType<>(factory::apply, Set.of(blocks));
	}

	public static void registerAttributes(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder attributes) {
		ModBus.attributes.add(event -> event.put(type, attributes.build()));
	}

	public static CreativeModeTab.Builder creativeTab() {
		return CreativeModeTab.builder();
	}

	/** {@code handler} runs on the server thread. */
	public static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
		ModBus.payloads.add(event -> registrar(event).playToServer(type, codec,
				(payload, context) -> handler.accept((ServerPlayer) context.player(), payload)));
	}

	/** The client handler is registered by {@code ClientPlatform.receive}. */
	public static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		ModBus.payloads.add(event -> registrar(event).playToClient(type, codec));
	}

	// Optional like Fabric channels: vanilla clients may join, canSend() then skips them.
	private static net.neoforged.neoforge.network.registration.PayloadRegistrar registrar(
			net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
		return event.registrar("1").optional();
	}

	public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return player.connection != null && player.connection.hasChannel(type);
	}

	public static Collection<ServerPlayer> tracking(Entity entity) {
		return entity.level() instanceof ServerLevel level ? level.getChunkSource().chunkMap.getPlayersWatching(entity) : java.util.List.of();
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}
}
