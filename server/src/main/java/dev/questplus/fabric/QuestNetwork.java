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
    private static final int CHARS=6000;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    /** Stable order ensures progress under sustained updates and backpressure. */
    private static final Set<UUID> DIRTY=new LinkedHashSet<>();
    private static final int MAX_SYNC_PLAYERS_PER_TICK=12;
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
    public record ChooseReward(String quest,UUID token,int slot,int option) implements CustomPacketPayload {
        public static final Type<ChooseReward> TYPE=new Type<>(Identifier.parse("questplus:choose_reward"));
        public static final StreamCodec<RegistryFriendlyByteBuf,ChooseReward> CODEC=StreamCodec.of(
            (b,v)->{b.writeUtf(v.quest,256);b.writeUUID(v.token);b.writeVarInt(v.slot);b.writeVarInt(v.option);},
            b->new ChooseReward(b.readUtf(256),b.readUUID(),b.readVarInt(),b.readVarInt()));
        @Override public Type<ChooseReward> type(){return TYPE;}
    }
    private static final class Session {
        long generation=-1, sequence, window, expiresAt=Long.MAX_VALUE, lastFullRequest;
        boolean forceFull;
        int requests;
        JsonObject progress;
        JsonElement team, invitations;
        Set<String> visible=Set.of();
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE,Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ChooseReward.TYPE,ChooseReward.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ChooseReward.TYPE,(payload,context)->context.server().execute(()->{
            var player=context.player();
            if(!allowRequest(player))return;
            try { QuestPlusMod.runtime().chooseRewardFromTablet(player,payload.quest(),payload.token().toString(),payload.slot(),payload.option()); }
            catch(Exception rejected){error(player,"action_rejected",rejected.getMessage());}
        }));
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,(payload,context) -> context.server().execute(() -> handle(context.player(),payload)));
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            // Presence only affects the player's team, not every player on the server.
            invalidatePresence(handler.player.getUUID());
            if (ServerPlayNetworking.canSend(handler.player,State.TYPE)) invalidate(handler.player.getUUID());
        });
    }
    public static void disconnect(UUID id,MinecraftServer server) {
        invalidatePresence(id);
        SESSIONS.remove(id); DIRTY.remove(id);
    }
    /** Team presence is projected to the affected members only. Never fan out a join/quit globally. */
    private static void invalidatePresence(UUID player) {
        try {
            var team=QuestPlusMod.runtime().teams().teamOf(player);
            if(team.isPresent()) team.get().members.forEach(QuestNetwork::invalidate);
        } catch (Exception unavailable) {
            QuestPlusMod.LOGGER.debug("Quest Plus team presence invalidation skipped for {}",player,unavailable);
        }
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
        if (DIRTY.isEmpty()) return;
        // Cap serialization + packet bursts per tick; duplicate invalidations coalesce.
        // Active queues are drained fairly on successive ticks, not all at once.
        int processed=0;
        var pending=DIRTY.iterator();
        while(pending.hasNext() && processed++<MAX_SYNC_PLAYERS_PER_TICK) {
            UUID id=pending.next();
            pending.remove();
            ServerPlayer player=server.getPlayerList().getPlayer(id);
            if (player==null || !ServerPlayNetworking.canSend(player,State.TYPE)) continue;
            try { sync(player,SESSIONS.get(id)!=null && SESSIONS.get(id).forceFull); }
            catch (Exception e) { QuestPlusMod.LOGGER.error("Quest Plus sync failed for {}",id,e); error(player,"sync_failed",e.getMessage()); }
        }
    }
    private static void sync(ServerPlayer player,boolean force) throws Exception {
        var runtime=QuestPlusMod.runtime(); UUID id=player.getUUID();
        Session session=SESSIONS.computeIfAbsent(id,k -> new Session());
        var visible=runtime.visibleQuests(id);
        Set<String> visibleIds=new HashSet<>(); visible.forEach(q -> visibleIds.add(q.id()));
        var progress=ClientProjection.progress(visible,runtime.engine(),runtime.state(id),runtime.player(id));
        boolean saving=runtime.isBusy(id);
        for(var entry:progress.entrySet()) {
            var value=entry.getValue().getAsJsonObject();
            value.addProperty("saving",saving);
            if(saving){value.addProperty("claimable",false);value.addProperty("claimableAny",false);}
        }
        JsonElement team=team(player,player.level().getServer(),runtime.teams().teamOf(id).orElse(null));
        JsonArray invitations=new JsonArray();
        for (TeamData invite:runtime.teams().invitationsFor(id,System.currentTimeMillis())) {
            JsonObject value=new JsonObject(); value.addProperty("id",invite.id.toString()); value.addProperty("name",invite.name);
            value.addProperty("owner",invite.owner.toString()); value.addProperty("expiresAt",invite.invitations.get(id)); invitations.add(value);
        }
        boolean restricted=visible.stream().anyMatch(q->q.presentation().hideDetailsUntilUnlocked()||q.presentation().hideTextUntilCompleted());
        boolean full=force || restricted&&!Objects.equals(progress,session.progress) || session.generation!=runtime.catalogGeneration() || session.progress==null || !session.visible.equals(visibleIds);
        JsonObject packet=new JsonObject();
        if (full) {
            packet.addProperty("type","snapshot");
            packet.add("categories",ClientProjection.categories(runtime.categories(),visible));
            packet.add("quests",ClientProjection.quests(visible,runtime.state(id)));
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
        session.sequence++; session.generation=runtime.catalogGeneration(); session.progress=progress; session.team=team; session.invitations=invitations; session.visible=visibleIds; session.forceFull=false;
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
        List<String> parts=new ArrayList<>();
        for (int start=0;start<data.length();) {
            int end=Math.min(start+CHARS,data.length());
            if (end<data.length() && Character.isHighSurrogate(data.charAt(end-1))) end--;
            parts.add(data.substring(start,end)); start=end;
        }
        long id=++transfer;
        for (int i=0;i<parts.size();i++) ServerPlayNetworking.send(player,new State(id,i,parts.size(),parts.get(i)));
    }
    public static void notice(ServerPlayer player,String title,String description,String icon) {
        if(!ServerPlayNetworking.canSend(player,State.TYPE)) { player.sendSystemMessage(net.minecraft.network.chat.Component.literal(title+(description.isBlank()?"":"\n"+description))); return; }
        var packet=new JsonObject();packet.addProperty("type","notice");packet.addProperty("title",title);
        packet.addProperty("description",description);packet.addProperty("icon",icon);send(player,packet);
    }
    static void error(ServerPlayer player,String code) { error(player,code,null); }
    private static void error(ServerPlayer player,String code,String detail) {
        if (!ServerPlayNetworking.canSend(player,State.TYPE)) return;
        JsonObject response=new JsonObject(); response.addProperty("type","error"); response.addProperty("error",code);
        if (detail!=null && !detail.isBlank()) response.addProperty("detail",detail.substring(0,Math.min(1500,detail.length())));
        send(player,response);
    }
    private static boolean allowRequest(ServerPlayer player) {
        UUID actor=player.getUUID(); Session session=SESSIONS.computeIfAbsent(actor,k -> new Session()); long now=System.currentTimeMillis();
        if (now-session.window>=1000) { session.window=now; session.requests=0; }
        if (++session.requests>8) { error(player,"rate_limited"); return false; }
        return true;
    }
    private static void handle(ServerPlayer player,Action request) {
        if(!allowRequest(player))return;
        UUID actor=player.getUUID(); long now=System.currentTimeMillis();
        try {
            QuestRuntime runtime=QuestPlusMod.runtime(); String value=request.argument();
            switch(request.action()) {
                case "sync" -> {
                    // A client-triggered recovery requests a full snapshot, but never
                    // serializes it synchronously inside a network receive callback.
                    Session session=SESSIONS.computeIfAbsent(actor,k->new Session());
                    if(now-session.lastFullRequest>=2000){
                        session.lastFullRequest=now; session.forceFull=true; invalidate(actor);
                    }
                }
                case "claim" -> { runtime.visibleQuest(actor,value); runtime.claimFromTablet(player,value); }
                case "submit" -> {
                    String[] parts=value.split("\\|",2);
                    if(parts.length!=2 || parts[0].isBlank() || !parts[1].matches("[a-z0-9_.-]+"))
                        throw new IllegalArgumentException("Invalid submission selection");
                    runtime.submitItem(player,parts[0],parts[1]);
                }
                case "claim_all" -> runtime.claimAllFromTablet(player);
                case "interact" -> {
                    String[] parts=value.split("\\|",2);
                    if(parts.length!=2 || parts[0].isBlank() || !parts[1].matches("[a-z0-9_.-]+"))
                        throw new IllegalArgumentException("Invalid manual objective selection");
                    runtime.interactObjective(player,parts[0],parts[1]);
                }
                case "restart" -> { runtime.visibleQuest(actor,value); runtime.mutate(actor,value,"restart"); }
                case "trigger" -> runtime.interactObjective(player,value,null);
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
