package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.clientdata.ClientProjection;
import dev.questplus.core.QuestEvent;
import dev.questplus.team.TeamData;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-authoritative, per-recipient projection. No persisted records cross the network. */
public final class QuestNetwork {
    private static final Gson JSON=new Gson();
    private static final int CHARS=6000, MAX_DOCUMENT=8_000_000;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Set<UUID> DIRTY=new HashSet<>();
    private static long transfer;
    private QuestNetwork() {}

    public record State(long transfer,int index,int total,String data) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(Identifier.parse("questplus:state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of(
            (b,v) -> { b.writeLong(v.transfer); b.writeVarInt(v.index); b.writeVarInt(v.total); b.writeUtf(v.data,CHARS); },
            b -> new State(b.readLong(),b.readVarInt(),b.readVarInt(),b.readUtf(CHARS)));
        @Override public Type<State> type() { return TYPE; }
    }
    public record Action(String action,String argument) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(Identifier.parse("questplus:action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of(
            (b,v) -> { b.writeUtf(v.action,32); b.writeUtf(v.argument,256); },
            b -> new Action(b.readUtf(32),b.readUtf(256)));
        @Override public Type<Action> type() { return TYPE; }
    }
    private static final class Session {
        long generation=-1, sequence, window, expiresAt=Long.MAX_VALUE;
        int requests;
        JsonObject progress;
        JsonElement team, invitations;
        Set<String> visible=Set.of();
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE,Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,(payload,context) -> context.server().execute(() -> handle(context.player(),payload)));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            invalidateAll(server);
            if (ServerPlayNetworking.canSend(handler.player,State.TYPE)) invalidate(handler.player.getUUID());
        });
    }
    public static void disconnect(UUID id,MinecraftServer server) {
        SESSIONS.remove(id); DIRTY.remove(id); invalidateAll(server);
    }
    public static void clear() { SESSIONS.clear(); DIRTY.clear(); }
    public static void invalidate(UUID id) { DIRTY.add(id); }
    public static void invalidateAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(p -> DIRTY.add(p.getUUID()));
    }
    public static void tick(MinecraftServer server,int tick) {
        if (tick%20==0) {
            long now=System.currentTimeMillis();
            SESSIONS.forEach((id,session) -> { if (session.expiresAt<=now) DIRTY.add(id); });
        }
        if (tick%4!=0 || DIRTY.isEmpty()) return;
        for (UUID id:List.copyOf(DIRTY)) {
            DIRTY.remove(id);
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null || !ServerPlayNetworking.canSend(player,State.TYPE)) continue;
            try { sync(player,false); }
            catch (Exception e) { QuestPlusMod.LOGGER.error("Quest Plus sync failed for {}",id,e); error(player,"sync_failed",e.getMessage()); }
        }
    }
    private static void sync(ServerPlayer player,boolean force) throws Exception {
        var runtime=QuestPlusMod.runtime(); UUID id=player.getUUID();
        Session session=SESSIONS.computeIfAbsent(id,k -> new Session());
        var visible=runtime.visibleQuests(id);
        Set<String> visibleIds=new HashSet<>(); visible.forEach(q -> visibleIds.add(q.id()));
        var progress=ClientProjection.progress(visible,runtime.engine(),runtime.state(id),runtime.player(id));
        JsonElement team=team(player,player.level().getServer(),runtime.teams().teamOf(id).orElse(null));
        JsonArray invitations=new JsonArray();
        for (TeamData invite:runtime.teams().invitationsFor(id,System.currentTimeMillis())) {
            JsonObject value=new JsonObject(); value.addProperty("id",invite.id.toString()); value.addProperty("name",invite.name);
            value.addProperty("owner",invite.owner.toString()); value.addProperty("expiresAt",invite.invitations.get(id)); invitations.add(value);
        }
        boolean full=force || session.generation!=runtime.catalogGeneration() || session.progress==null || !session.visible.equals(visibleIds);
        JsonObject packet=new JsonObject();
        if (full) {
            packet.addProperty("type","snapshot");
            packet.add("categories",JSON.toJsonTree(runtime.categories()));
            packet.add("quests",ClientProjection.quests(visible));
            packet.add("progress",progress); packet.add("team",team); packet.add("invitations",invitations);
        } else {
            JsonObject changed=new JsonObject();
            progress.entrySet().forEach(e -> { if (!Objects.equals(e.getValue(),session.progress.get(e.getKey()))) changed.add(e.getKey(),e.getValue()); });
            if (!changed.isEmpty()) packet.add("progress",changed);
            if (!Objects.equals(team,session.team)) packet.add("team",team);
            if (!Objects.equals(invitations,session.invitations)) packet.add("invitations",invitations);
            if (packet.isEmpty()) return;
            packet.addProperty("type","delta");
        }
        packet.addProperty("generation",runtime.catalogGeneration()); packet.addProperty("sequence",session.sequence+1);
        send(player,packet);
        session.sequence++; session.generation=runtime.catalogGeneration(); session.progress=progress; session.team=team; session.invitations=invitations; session.visible=visibleIds;
        session.expiresAt=Long.MAX_VALUE;
        for (JsonElement invite:invitations) session.expiresAt=Math.min(session.expiresAt,invite.getAsJsonObject().get("expiresAt").getAsLong());
        if (team.isJsonObject()) for (JsonElement invite:team.getAsJsonObject().getAsJsonArray("invitations"))
            session.expiresAt=Math.min(session.expiresAt,invite.getAsJsonObject().get("expiresAt").getAsLong());
    }
    private static JsonElement team(ServerPlayer viewer,MinecraftServer server,TeamData team) {
        if (team==null) return JsonNull.INSTANCE;
        JsonObject out=new JsonObject(); out.addProperty("id",team.id.toString()); out.addProperty("name",team.name); out.addProperty("owner",team.owner.toString());
        JsonArray members=new JsonArray();
        team.members.stream().sorted().forEach(id -> { JsonObject value=new JsonObject(); value.addProperty("id",id.toString());
            ServerPlayer online=server.getPlayerList().getPlayer(id); value.addProperty("online",online!=null);
            if (online!=null) value.addProperty("name",online.getName().getString()); members.add(value); });
        out.add("members",members);
        JsonArray sent=new JsonArray();
        if (team.owner.equals(viewer.getUUID())) team.invitations.entrySet().stream().filter(e -> e.getValue()>System.currentTimeMillis()).sorted(Map.Entry.comparingByKey()).forEach(e -> {
            JsonObject value=new JsonObject(); value.addProperty("id",e.getKey().toString()); value.addProperty("expiresAt",e.getValue()); sent.add(value);
        });
        out.add("invitations",sent); return out;
    }
    private static void send(ServerPlayer player,JsonObject document) {
        String data=JSON.toJson(document);
        if (data.length()>MAX_DOCUMENT) throw new IllegalStateException("Quest Plus sync exceeds 8 million characters");
        List<String> parts=new ArrayList<>();
        for (int start=0;start<data.length();) {
            int end=Math.min(start+CHARS,data.length());
            if (end<data.length() && Character.isHighSurrogate(data.charAt(end-1))) end--;
            parts.add(data.substring(start,end)); start=end;
        }
        long id=++transfer;
        for (int i=0;i<parts.size();i++) ServerPlayNetworking.send(player,new State(id,i,parts.size(),parts.get(i)));
    }
    private static void error(ServerPlayer player,String code) { error(player,code,null); }
    private static void error(ServerPlayer player,String code,String detail) {
        if (!ServerPlayNetworking.canSend(player,State.TYPE)) return;
        JsonObject response=new JsonObject(); response.addProperty("type","error"); response.addProperty("error",code);
        if (detail!=null && !detail.isBlank()) response.addProperty("detail",detail.substring(0,Math.min(1500,detail.length())));
        send(player,response);
    }
    private static void handle(ServerPlayer player,Action request) {
        UUID actor=player.getUUID(); Session session=SESSIONS.computeIfAbsent(actor,k -> new Session()); long now=System.currentTimeMillis();
        if (now-session.window>=1000) { session.window=now; session.requests=0; }
        if (++session.requests>8) { error(player,"rate_limited"); return; }
        try {
            QuestRuntime runtime=QuestPlusMod.runtime(); String value=request.argument();
            switch(request.action()) {
                case "sync" -> sync(player,true);
                case "claim" -> { runtime.visibleQuest(actor,value); runtime.claim(player,value); }
                case "restart" -> { runtime.visibleQuest(actor,value); runtime.mutate(actor,value,"restart"); }
                case "trigger" -> { runtime.visibleQuest(actor,value); runtime.event(player,new QuestEvent("manual",value,Set.of(),1,QuestPlusMod.context(player))); }
                case "create","invite","join","decline","leave","kick","transfer","disband" -> {
                    runtime.changeTeam(m -> switch(request.action()) {
                        case "create" -> m.create(actor,value);
                        case "invite" -> m.invite(actor,UUID.fromString(value),now);
                        case "join" -> m.join(actor,UUID.fromString(value),now);
                        case "decline" -> m.decline(actor,UUID.fromString(value));
                        case "leave" -> m.leave(actor);
                        case "kick" -> m.kick(actor,UUID.fromString(value));
                        case "transfer" -> m.transfer(actor,UUID.fromString(value));
                        default -> m.disband(actor);
                    }).whenComplete((ok,ex) -> { if (ex!=null) error(player,"team_save_failed"); });
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
        } catch (Exception e) {
            QuestPlusMod.LOGGER.warn("Quest Plus client action rejected: {}",e.getMessage(),e);
            error(player,QuestPlusMod.unavailableReason().equals(e.getMessage()) ? "sync_failed" : "action_rejected",e.getMessage());
        }
    }
}
