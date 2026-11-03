package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.fabric.QuestNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;

public final class QuestPlusClient implements ClientModInitializer {
    public static final QuestClientState STATE = new QuestClientState();
    public static ClientSettings settings;
    private static KeyMapping open;
    private static long transfer=-1;
    private static String[] fragments;
    private static int bytes, received;
    private static long started, lastSync;
    public static String notice;
    public static long noticeUntil;
    private static long progressNoticeAfter;
    public static Component tr(String key,Object... arguments) { return Component.translatable("questplus.client."+key,arguments); }
    public static void action(String action,String argument) {
        if (ClientPlayNetworking.canSend(QuestNetwork.Action.TYPE)) ClientPlayNetworking.send(new QuestNetwork.Action(action,argument));
        else STATE.error="unsupported";
    }
    public static void sync() { lastSync=System.currentTimeMillis(); action("sync",""); }
    @Override public void onInitializeClient() {
        settings=ClientSettings.load();
        TabletPoseClient.register();
        open=KeyMappingHelper.registerKeyMapping(new KeyMapping("questplus.client.open", InputConstants.KEY_J,KeyMapping.Category.MISC));
        ClientPlayNetworking.registerGlobalReceiver(QuestNetwork.State.TYPE,(payload,context) -> context.client().execute(() -> receive(payload)));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client) -> { clear(); sync(); });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.consumeClick()) if (client.player!=null) {
                if (client.gui.screen() instanceof QuestScreen screen) screen.onClose();
                else client.gui.setScreen(new QuestScreen());
            }
            long now=System.currentTimeMillis();
            if (fragments!=null && now-started>15000) { fragments=null; STATE.error="timeout"; }
            if (client.player!=null && !STATE.ready && now-lastSync>10000 && ClientPlayNetworking.canSend(QuestNetwork.Action.TYPE)) sync();
        });
        HudElementRegistry.addLast(Identifier.parse("questplus:pinned"),(graphics,delta) -> QuestHud.draw(graphics));
    }
    private static void clear() { STATE.clear(); fragments=null; transfer=-1; notice=null; lastSync=0; }
    private static void receive(QuestNetwork.State packet) {
        try {
            if (packet.total()<1 || packet.total()>2048 || packet.index()<0 || packet.index()>=packet.total()) throw new IllegalArgumentException("fragment bounds");
            if (transfer!=packet.transfer()) { transfer=packet.transfer(); fragments=new String[packet.total()]; received=bytes=0; started=System.currentTimeMillis(); }
            if (fragments==null || fragments.length!=packet.total()) throw new IllegalArgumentException("fragment count");
            if (fragments[packet.index()]!=null) return;
            bytes+=packet.data().length(); if (bytes>16*1024*1024) throw new IllegalArgumentException("transfer too large");
            fragments[packet.index()]=packet.data(); received++;
            if (received!=fragments.length) return;
            var message=dev.questplus.core.QuestRegistry.parseStrict(new java.io.StringReader(String.join("",fragments))).getAsJsonObject(); fragments=null;
            boolean initial=!STATE.ready;
            Map<String,JsonObject> previous=Map.copyOf(STATE.progress);
            if (!STATE.apply(message)) { sync(); return; }
            if (!initial && settings.notifications && message.has("progress")) {
                for (var entry:message.getAsJsonObject("progress").entrySet()) {
                    var old=previous.getOrDefault(entry.getKey(),new JsonObject()); var next=entry.getValue().getAsJsonObject();
                    String key=(!QuestClientState.flag(old,"completed") && QuestClientState.flag(next,"completed")) || QuestClientState.count(next,"cycles")>QuestClientState.count(old,"cycles") ? "completed"
                        : !QuestClientState.flag(old,"claimable") && QuestClientState.flag(next,"claimable") ? "reward_ready"
                        : QuestClientState.count(next,"tier")>QuestClientState.count(old,"tier") ? "tier_reached" : null;
                    var quest=STATE.quests.get(entry.getKey());
                    if (key!=null && quest!=null) { notice=tr(key,STATE.text(quest.title())).getString(); noticeUntil=System.currentTimeMillis()+4500; break; }
                    if (quest!=null && settings.pinned.contains(quest.id()) && System.currentTimeMillis()>progressNoticeAfter) {
                        var before=old.getAsJsonObject("counters"); var after=next.getAsJsonObject("counters");
                        if (after!=null) for (var objective:quest.objectives()) {
                            long a=before!=null && before.has(objective.id()) ? before.get(objective.id()).getAsLong() : 0;
                            long b=after.has(objective.id()) ? after.get(objective.id()).getAsLong() : 0;
                            long target=dev.questplus.clientdata.QuestPresentation.target(quest,objective,next);
                            if (b>a && (b>=target && a<target || b*4/target>a*4/target)) {
                                notice=tr(b>=target ? "objective_completed" : "progress_notice",STATE.text(quest.title())).getString();
                                noticeUntil=System.currentTimeMillis()+3500; progressNoticeAfter=System.currentTimeMillis()+8000; break;
                            }
                        }
                    }
                }
            }
        } catch (Exception error) { fragments=null; STATE.error="network_error"; dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Invalid Quest Plus client transfer",error); }
    }
}
