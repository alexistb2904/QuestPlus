package dev.questplus.fabric;

import dev.questplus.platform.Platform;
import com.google.gson.*;
import dev.questplus.clientdata.ClientProjection;
import dev.questplus.core.Localization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/**
 * NPC quest dialog. The server decides what each quest offers this player. The
 * client only asks to accept or turn in within an open, nearby session.
 */
public final class QuestGiverNetwork {
	private static final int MAX_DATA = 262144;

	public record Dialog(int entity, UUID token, String data) implements CustomPacketPayload {
		public static final Type<Dialog> TYPE = new Type<>(Identifier.parse("questplus:npc_dialog"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Dialog> CODEC = StreamCodec.of((b, v) -> {
			b.writeVarInt(v.entity);
			b.writeUUID(v.token);
			b.writeUtf(v.data, MAX_DATA);
		}, b -> new Dialog(b.readVarInt(), b.readUUID(), b.readUtf(MAX_DATA)));

		@Override
		public Type<Dialog> type() {
			return TYPE;
		}
	}

	public record Request(int entity, UUID token, String action, String quest) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(Identifier.parse("questplus:npc_action"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((b, v) -> {
			b.writeVarInt(v.entity);
			b.writeUUID(v.token);
			b.writeUtf(v.action, 16);
			b.writeUtf(v.quest, 256);
		}, b -> new Request(b.readVarInt(), b.readUUID(), b.readUtf(16), b.readUtf(256)));

		@Override
		public Type<Request> type() {
			return TYPE;
		}
	}

	/**
	 * One NPC's appearance. An empty {@code npc} clears every appearance before a
	 * full resend.
	 */
	public record Look(String npc, String data) implements CustomPacketPayload {
		public static final Type<Look> TYPE = new Type<>(Identifier.parse("questplus:npc_look"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Look> CODEC = StreamCodec.of((b, v) -> {
			b.writeUtf(v.npc, 64);
			b.writeUtf(v.data, 32768);
		}, b -> new Look(b.readUtf(64), b.readUtf(32768)));

		@Override
		public Type<Look> type() {
			return TYPE;
		}
	}

	private record Session(int entity, UUID token, int expires) {
	}

	/** Every NPC appearance, to a player who just joined or after a quest reload. */
	static void sendLooks(ServerPlayer player) {
		if (!Platform.canSend(player, Look.TYPE))
			return;
		Map<String, dev.questplus.core.QuestNpcs.Npc> npcs;
		try {
			npcs = QuestPlusMod.runtime().npcs();
		} catch (IllegalStateException unavailable) {
			return;
		}
		Platform.send(player, new Look("", ""));
		for (var npc : npcs.values()) {
			var look = new JsonObject();
			look.addProperty("model", npc.look().model());
			look.addProperty("villager", npc.look().villager());
			if (!npc.look().skin().isEmpty())
				look.addProperty("skin", npc.look().skin());
			Platform.send(player, new Look(npc.id(), look.toString()));
		}
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();
	private static final Map<UUID, Integer> LAST = new HashMap<>();

	private QuestGiverNetwork() {
	}

	static void register() {
		Platform.clientbound(Dialog.TYPE, Dialog.CODEC);
		Platform.serverbound(Request.TYPE, Request.CODEC, QuestGiverNetwork::handle);
		Platform.clientbound(Look.TYPE, Look.CODEC);
		Platform.onPlayerJoin(QuestGiverNetwork::sendLooks);
		Platform.onPlayerDisconnect((player, server) -> {
			SESSIONS.remove(player.getUUID());
			LAST.remove(player.getUUID());
		});
		Platform.onServerStopped(server -> {
			SESSIONS.clear();
			LAST.clear();
		});
	}

	static void open(ServerPlayer player, QuestGiver giver) {
		if (player.isSpectator() || !Platform.canSend(player, Dialog.TYPE))
			return;
		var session = new Session(giver.getId(), UUID.randomUUID(), player.level().getServer().getTickCount() + 6000);
		SESSIONS.put(player.getUUID(), session);
		send(player, giver, session, "");
	}

	private static void handle(ServerPlayer player, Request packet) {
		var session = SESSIONS.get(player.getUUID());
		if (session == null || !session.token().equals(packet.token()) || session.entity() != packet.entity())
			return;
		if (packet.action().equals("close")) {
			SESSIONS.remove(player.getUUID());
			return;
		}
		int tick = player.level().getServer().getTickCount();
		if (tick - LAST.getOrDefault(player.getUUID(), -100) < 4 || tick > session.expires())
			return;
		LAST.put(player.getUUID(), tick);
		if (!(player.level().getEntity(packet.entity()) instanceof QuestGiver giver) || !giver.isAlive()
				|| player.distanceToSqr(giver) > 64 || player.isSpectator())
			return;
		String error = "";
		try {
			var runtime = QuestPlusMod.runtime();
			var npc = runtime.npcs().get(giver.npc());
			if (npc == null)
				throw new IllegalArgumentException("This NPC has no quests");
			switch (packet.action()) {
			case "accept" -> runtime.acceptFromNpc(player, npc, packet.quest());
			case "claim" -> runtime.claimFromNpc(player, npc, packet.quest());
			default -> throw new IllegalArgumentException("Unknown NPC action");
			}
		} catch (Exception rejected) {
			error = Objects.toString(rejected.getMessage(), rejected.getClass().getSimpleName());
		}
		send(player, giver, session, error);
	}

	private static void send(ServerPlayer player, QuestGiver giver, Session session, String error) {
		var json = new JsonObject();
		var quests = new JsonArray();
		json.addProperty("error", error);
		json.add("quests", quests);
		try {
			var runtime = QuestPlusMod.runtime();
			var npc = runtime.npcs().get(giver.npc());
			json.addProperty("npc", npc == null ? giver.getName().getString()
					: Localization.text(npc.name(), player.clientInformation().language(), Map.of()));
			if (npc != null) {
				var state = runtime.state(player.getUUID());
				var personal = runtime.player(player.getUUID());
				var context = QuestPlusMod.context(player);
				List<JsonObject> ready = new ArrayList<>(), offers = new ArrayList<>(), active = new ArrayList<>();
				// Why nothing is on offer, so the NPC can say it in character.
				var completed = new JsonArray();
				int locked = 0, resting = 0;
				for (String id : npc.quests()) {
					var quest = runtime.registry().get(id);
					boolean claimable = personal.entitlements.entrySet().stream()
							.anyMatch(e -> e.getValue().questId().equals(id) && !e.getValue().rewards().isEmpty()
									&& !personal.claims.containsKey(e.getKey()));
					var blocker = runtime.engine().acceptBlocker(state, id, context);
					String status = claimable ? "ready"
							: blocker.isEmpty() ? "offer" : blocker.get().equals("active") ? "active" : null;
					if (status == null) {
						var done = state.quests.get(id);
						if (done != null && done.completed && completed.size() < 16)
							completed.add(runtime.title(player, quest));
						if (blocker.get().equals("locked"))
							locked++;
						if (blocker.get().equals("cooldown"))
							resting++;
						continue;
					}
					var entry = new JsonObject();
					entry.addProperty("status", status);
					var projected = ClientProjection.quests(List.of(quest), state).get(0).getAsJsonObject();
					// The dialog shows plain text. Rich images would only bloat the packet.
					projected.remove("descriptionRich");
					entry.add("quest", projected);
					var progress = new JsonObject();
					var p = state.quests.get(id);
					progress.addProperty("tier", p == null ? 0 : p.tier);
					progress.add("counters", new Gson().toJsonTree(p == null ? Map.of() : p.counters));
					entry.add("progress", progress);
					(claimable ? ready : blocker.isEmpty() ? offers : active).add(entry);
				}
				json.add("completed", completed);
				json.addProperty("locked", locked);
				json.addProperty("resting", resting);
				int size = json.toString().length();
				for (var list : List.of(ready, offers, active))
					for (var entry : list) {
						size += entry.toString().length() + 1;
						if (size < MAX_DATA)
							quests.add(entry);
					}
			}
		} catch (Exception unavailable) {
			json.addProperty("error", Objects.toString(unavailable.getMessage(), "Quest Plus unavailable"));
		}
		if (!json.has("npc"))
			json.addProperty("npc", giver.getName().getString());
		Platform.send(player, new Dialog(giver.getId(), session.token(), json.toString()));
	}
}
