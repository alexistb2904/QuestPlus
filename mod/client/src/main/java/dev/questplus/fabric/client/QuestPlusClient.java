package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import com.google.gson.*;
import dev.questplus.fabric.QuestNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class QuestPlusClient implements ClientModInitializer {
	public static final QuestClientState STATE = new QuestClientState();
	public static ClientSettings settings;
	/** Only newly-unlocked quests show a blue category marker until viewed. */
	public static final Set<String> NEW_UNLOCKS = new HashSet<>();
	private static KeyMapping open;

	public static boolean matchesOpenKey(KeyEvent event) {
		return open != null && open.matches(event);
	}

	private static long transfer = -1;
	private static String[] fragments;
	private static int bytes, received;
	private static long started, lastFragment, lastSync;
	// Cap allocations from fragmented state packets without truncating normal large
	// packs.
	private static final int MAX_TRANSFER_PARTS = 8192;
	private static final int MAX_TRANSFER_CHARS = 24 * 1024 * 1024;

	public static Component tr(String key, Object... arguments) {
		return Component.translatable("questplus.client." + key, arguments);
	}

	public static void action(String action, String argument) {
		if (ClientPlayNetworking.canSend(QuestNetwork.Action.TYPE))
			ClientPlayNetworking.send(new QuestNetwork.Action(action, argument));
		else
			STATE.error = "unsupported";
	}

	public static void chooseReward(String quest, String token, int slot, int option) {
		if (ClientPlayNetworking.canSend(QuestNetwork.ChooseReward.TYPE))
			ClientPlayNetworking.send(new QuestNetwork.ChooseReward(quest, UUID.fromString(token), slot, option));
		else
			STATE.error = "unsupported";
	}

	public static void sync() {
		lastSync = System.currentTimeMillis();
		action("sync", "");
	}

	@Override
	public void onInitializeClient() {
		ClientAdapter.INSTANCE.initialize();
		settings = ClientSettings.load();
		QuestWorldBlockRenderer.register();
		QuestLootChestRenderer.register();
		QuestGiverRenderer.register();
		ClientPlayNetworking.registerGlobalReceiver(dev.questplus.fabric.QuestGiverNetwork.Look.TYPE,
				(packet, context) -> context.client().execute(() -> QuestGiverLooks.receive(packet)));
		ClientPlayNetworking.registerGlobalReceiver(dev.questplus.fabric.QuestGiverNetwork.Dialog.TYPE,
				(packet, context) -> context.client().execute(() -> {
					if (ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestGiverScreen screen
							&& screen.entity() == packet.entity())
						screen.receive(packet);
					else
						ClientAdapter.INSTANCE.setScreen(context.client(), new QuestGiverScreen(packet));
				}));
		ClientPlayNetworking.registerGlobalReceiver(dev.questplus.fabric.QuestBlockNetwork.Snapshot.TYPE,
				(packet, context) -> context.client().execute(() -> {
					if (ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestStationScreen screen) screen.receive(packet);
					else if (ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestRelayScreen screen) screen.receive(packet);
					else if (ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestBlockScreen screen) screen.receive(packet);
					else if(ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestBoardScreen screen)screen.receive(packet);
					else if(ClientAdapter.INSTANCE.screen(context.client()) instanceof QuestBlockPicker picker)picker.receive(packet);
					else if(com.google.gson.JsonParser.parseString(packet.data()).getAsJsonObject().get("kind").getAsInt()==0)
						ClientAdapter.INSTANCE.setScreen(context.client(), new QuestBoardScreen(packet));
					else if(com.google.gson.JsonParser.parseString(packet.data()).getAsJsonObject().get("kind").getAsInt()==2)
						ClientAdapter.INSTANCE.setScreen(context.client(), new QuestRelayScreen(packet));
					else ClientAdapter.INSTANCE.setScreen(context.client(), new QuestStationScreen(packet));
				}));
		TabletPoseClient.register();
		QuestClientTabletFlow.register();
		PunchyTabletCompat.register();
		FirstPersonTabletCompat.register();
		open = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("questplus.client.open", ClientAdapter.INSTANCE.keys().j(), KeyMapping.Category.MISC));
		ClientPlayNetworking.registerGlobalReceiver(QuestNetwork.State.TYPE,
				(payload, context) -> context.client().execute(() -> receive(payload)));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			clear();
			sync();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			clear();
			client.execute(QuestGiverLooks::clear);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (open.consumeClick())
				if (client.player != null)
					QuestClientTabletFlow.toggle(client);
			long now = System.currentTimeMillis();
			if (fragments != null && (now - lastFragment > 15000 || now - started > 120000)) {
				fragments = null;
				STATE.error = "timeout";
			}
			if (client.player != null && fragments == null && !STATE.ready && now - lastSync > 10000
					&& ClientPlayNetworking.canSend(QuestNetwork.Action.TYPE))
				sync();
		});
		HudElementRegistry.addLast(Identifier.parse("questplus:pinned"), (graphics, delta) -> QuestHud.draw(graphics));
	}

	private static void clear() {
		QuestScreen.clearSavedView();
		NEW_UNLOCKS.clear();
		STATE.clear();
		fragments = null;
		transfer = -1;
		QuestToast.clear();
		lastSync = 0;
	}

	private static void receive(QuestNetwork.State packet) {
		try {
			if (packet.total() < 1 || packet.total() > MAX_TRANSFER_PARTS || packet.index() < 0
					|| packet.index() >= packet.total())
				throw new IllegalArgumentException("fragment bounds");
			if (transfer != packet.transfer()) {
				transfer = packet.transfer();
				fragments = new String[packet.total()];
				received = bytes = 0;
				started = System.currentTimeMillis();
			}
			if (fragments == null || fragments.length != packet.total())
				throw new IllegalArgumentException("fragment count");
			if (fragments[packet.index()] != null)
				return;
			if (bytes > MAX_TRANSFER_CHARS - packet.data().length())
				throw new IllegalArgumentException("transfer too large");
			bytes += packet.data().length();
			fragments[packet.index()] = packet.data();
			received++;
			lastFragment = System.currentTimeMillis();
			if (received != fragments.length)
				return;
			var message = dev.questplus.core.QuestRegistry
					.parseStrict(new java.io.StringReader(String.join("", fragments))).getAsJsonObject();
			fragments = null;
			boolean initial = !STATE.ready;
			// Only compare entries included in this transfer. Copying the entire
			// quest state map on every small delta is O(all authored quests).
			Map<String, JsonObject> previous = new HashMap<>();
			if (!initial && message.has("progress"))
				for (var entry : message.getAsJsonObject("progress").entrySet()) {
					var old = STATE.progress.get(entry.getKey());
					if (old != null)
						previous.put(entry.getKey(), old);
				}
			if (!STATE.apply(message)) {
				sync();
				return;
			}
			if (!initial && message.has("progress")) {
				for (var entry : message.getAsJsonObject("progress").entrySet()) {
					var old = previous.getOrDefault(entry.getKey(), new JsonObject());
					var next = entry.getValue().getAsJsonObject();
					String event = (!QuestClientState.flag(old, "completed")
							&& QuestClientState.flag(next, "completed"))
							|| QuestClientState.count(next, "cycles") > QuestClientState.count(old, "cycles")
									? "completed"
									: (!QuestClientState.flag(old, "unlocked")
											&& QuestClientState.flag(next, "unlocked")) ? "unlocked" : null;
					var quest = STATE.quests.get(entry.getKey());
					if ("unlocked".equals(event))
						NEW_UNLOCKS.add(entry.getKey());
					if (settings.notifications && event != null && quest != null) {
						if (!event.equals("completed") || !quest.presentation().disableCompletionToast())
							QuestToast.show(event, STATE.text(quest.title()), quest.icon());
						break;
					}
				}
			}
		} catch (Exception error) {
			fragments = null;
			STATE.error = "network_error";
			dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Invalid Quest Plus client transfer", error);
		}
	}
}
