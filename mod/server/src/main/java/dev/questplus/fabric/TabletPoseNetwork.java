package dev.questplus.fabric;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Cosmetic pose only: no quest content or progression is transmitted. */
public final class TabletPoseNetwork {
	private static final Map<UUID, Long> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	private static final Set<UUID> PENDING = new HashSet<>();

	private TabletPoseNetwork() {
	}

	public record Request(boolean open) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.parse("questplus:tablet_pose_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec
				.of((buf, value) -> buf.writeBoolean(value.open), buf -> new Request(buf.readBoolean()));

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	public record State(UUID player, boolean open) implements CustomPacketPayload {
		public static final Type<State> TYPE = new Type<>(Identifier.parse("questplus:tablet_pose"));
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, value) -> {
			buf.writeUUID(value.player);
			buf.writeBoolean(value.open);
		}, buf -> new State(buf.readUUID(), buf.readBoolean()));

		@Override
		public Type<State> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,
				(payload, context) -> context.server().execute(() -> request(context.player(), payload.open)));
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID()))
				send(viewer, player.getUUID(), true);
		});
		EntityTrackingEvents.STOP_TRACKING.register((entity, viewer) -> {
			if (entity instanceof ServerPlayer player && ACTIVE.containsKey(player.getUUID()))
				send(viewer, player.getUUID(), false);
		});
	}

	private static void request(ServerPlayer player, boolean open) {
		UUID id = player.getUUID();
		long now = System.currentTimeMillis();
		if (!open || !player.isAlive() || player.isSpectator()) {
			close(player);
			return;
		}
		if (now - LAST.getOrDefault(id, 0L) < 100) {
			if (!ACTIVE.containsKey(id))
				PENDING.add(id);
			return;
		}
		PENDING.remove(id);
		LAST.put(id, now);
		boolean first = !ACTIVE.containsKey(id);
		ACTIVE.put(id, now + 15000);
		if (first)
			broadcast(player, true);
	}

	private static void send(ServerPlayer viewer, UUID id, boolean open) {
		if (ServerPlayNetworking.canSend(viewer, State.TYPE))
			ServerPlayNetworking.send(viewer, new State(id, open));
	}

	private static void broadcast(ServerPlayer player, boolean open) {
		for (ServerPlayer viewer : PlayerLookup.tracking(player))
			send(viewer, player.getUUID(), open);
	}

	public static void close(ServerPlayer player) {
		PENDING.remove(player.getUUID());
		if (ACTIVE.remove(player.getUUID()) != null)
			broadcast(player, false);
	}

	public static void disconnect(ServerPlayer player) {
		close(player);
		LAST.remove(player.getUUID());
	}

	public static void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		for (UUID id : List.copyOf(PENDING)) {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				PENDING.remove(id);
				LAST.remove(id);
			} else if (now - LAST.getOrDefault(id, 0L) >= 100)
				request(player, true);
		}
		for (UUID id : List.copyOf(ACTIVE.keySet())) {
			var player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				ACTIVE.remove(id);
				LAST.remove(id);
			} else if (!player.isAlive() || player.isSpectator() || ACTIVE.get(id) < now)
				close(player);
		}
	}

	public static void clear() {
		ACTIVE.clear();
		LAST.clear();
		PENDING.clear();
	}
}
