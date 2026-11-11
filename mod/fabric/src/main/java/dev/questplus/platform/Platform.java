package dev.questplus.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

/**
 * Fabric implementation of the gameplay hooks shared code registers. {@code mod/neoforge} declares
 * the same public methods (checked by {@code verifyPlatformApi}).
 */
public final class Platform {
	private Platform() {}

	public static void onServerStarted(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STARTED.register(listener::accept);
	}

	public static void onServerStopping(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
	}

	public static void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
	}

	public static void onServerTick(Consumer<MinecraftServer> listener) {
		ServerTickEvents.END_SERVER_TICK.register(listener::accept);
	}

	public static void onPlayerJoin(Consumer<ServerPlayer> listener) {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.player));
	}

	public static void onPlayerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> listener) {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> listener.accept(handler.player, server));
	}

	public static void onChunkGenerated(BiConsumer<ServerLevel, LevelChunk> listener) {
		ServerChunkEvents.CHUNK_GENERATE.register(listener::accept);
	}

	public static void onStartTracking(BiConsumer<Entity, ServerPlayer> listener) {
		EntityTrackingEvents.START_TRACKING.register(listener::accept);
	}

	public static void onStopTracking(BiConsumer<Entity, ServerPlayer> listener) {
		EntityTrackingEvents.STOP_TRACKING.register(listener::accept);
	}

	public static void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener) {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> listener.accept(dispatcher));
	}

	/** Observes only: never cancels the break. */
	public static void onBeforeBlockBreak(Hooks.BlockBreak listener) {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) listener.broken(serverPlayer, pos, state);
			return true;
		});
	}

	public static void onAfterBlockBreak(Hooks.BlockBreak listener) {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) listener.broken(serverPlayer, pos, state);
		});
	}

	public static void onLivingDeath(BiConsumer<LivingEntity, DamageSource> listener) {
		ServerLivingEntityEvents.AFTER_DEATH.register(listener::accept);
	}

	public static void onUseItem(Hooks.Use listener) {
		UseItemCallback.EVENT.register(listener::use);
	}

	public static void onUseBlock(Hooks.Use listener) {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> listener.use(player, level, hand));
	}

	public static void onUseEntity(Hooks.Use listener) {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> listener.use(player, level, hand));
	}

	public static <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory,
			Block... blocks) {
		return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
	}

	public static void registerAttributes(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder attributes) {
		FabricDefaultAttributeRegistry.register(type, attributes);
	}

	public static CreativeModeTab.Builder creativeTab() {
		return FabricCreativeModeTab.builder();
	}

	/** {@code handler} runs on the server thread. */
	public static <T extends CustomPacketPayload> void serverbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
		PayloadTypeRegistry.serverboundPlay().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type,
				(payload, context) -> context.server().execute(() -> handler.accept(context.player(), payload)));
	}

	/** The client handler is registered by {@code ClientPlatform.receive}. */
	public static <T extends CustomPacketPayload> void clientbound(CustomPacketPayload.Type<T> type,
			StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		PayloadTypeRegistry.clientboundPlay().register(type, codec);
	}

	public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
		return ServerPlayNetworking.canSend(player, type);
	}

	public static Collection<ServerPlayer> tracking(Entity entity) {
		return PlayerLookup.tracking(entity);
	}

	public static void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
	}
}
