package dev.questplus.fabric;

import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public final class QuestBlockNetwork {
    public record Request(BlockPos pos, UUID token, String action, String data, String owner, int size) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(Identifier.parse("questplus:block_config"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,v)->{
            b.writeBlockPos(v.pos);b.writeUUID(v.token);b.writeUtf(v.action,16);b.writeUtf(v.data,8192);b.writeUtf(v.owner,36);b.writeVarInt(v.size);
        },b->new Request(b.readBlockPos(),b.readUUID(),b.readUtf(16),b.readUtf(8192),b.readUtf(36),b.readVarInt()));
        public Type<Request> type(){return TYPE;}
    }
    public record Snapshot(BlockPos pos,UUID token,String data) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(Identifier.parse("questplus:block_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,v)->{
            b.writeBlockPos(v.pos);b.writeUUID(v.token);b.writeUtf(v.data,131072);
        },b->new Snapshot(b.readBlockPos(),b.readUUID(),b.readUtf(131072)));
        public Type<Snapshot> type(){return TYPE;}
    }
    private record Session(QuestUtilityEntity block,UUID token,long expires){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Integer> LAST=new HashMap<>();
    private static final Map<UUID,String> SENT=new HashMap<>();
    private QuestBlockNetwork(){}
    static void register(){
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(packet,ctx)->ctx.server().execute(()->handle(ctx.player(),packet)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{SESSIONS.remove(handler.player.getUUID());LAST.remove(handler.player.getUUID());SENT.remove(handler.player.getUUID());});
    }
    static void clear(){SESSIONS.clear();LAST.clear();SENT.clear();}
    static void tick(MinecraftServer server){
        if(server.getTickCount()%200==0) SESSIONS.values().removeIf(s->s.expires<server.getTickCount()||s.block.isRemoved());
        if(server.getTickCount()%20==0)for(var entry:List.copyOf(SESSIONS.entrySet())) {
            var player=server.getPlayerList().getPlayer(entry.getKey());var s=entry.getValue();
            if(player!=null && player.level()==s.block.getLevel() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.block.getBlockPos()))<=64)
                send(player,s,"");
        }
    }
    static void open(ServerPlayer actor,QuestUtilityEntity block){
        if(actor.isSpectator()||!actor.level().mayInteract(actor,block.getBlockPos())||!ServerPlayNetworking.canSend(actor,Snapshot.TYPE))return;
        if(block.owner==null)block.claim(actor.getUUID());
        var session=new Session(block,UUID.randomUUID(),actor.level().getServer().getTickCount()+12000);
        SESSIONS.put(actor.getUUID(),session);send(actor,session,"");
    }
    static boolean authorized(ServerPlayer actor,BlockPos pos,UUID token){
        var s=SESSIONS.get(actor.getUUID());
        return s!=null && s.token.equals(token) && s.block.getBlockPos().equals(pos) && !s.block.isRemoved()
                && actor.level()==s.block.getLevel() && actor.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=64
                && actor.level().getServer().getTickCount()<=s.expires && s.block.canConfigure(actor);
    }
    private static void handle(ServerPlayer actor,Request packet){
        var open=SESSIONS.get(actor.getUUID());
        if(packet.action.equals("close")) {
            if(open!=null && open.token.equals(packet.token)) {SESSIONS.remove(actor.getUUID());SENT.remove(actor.getUUID());}
            return;
        }
        int tick=actor.level().getServer().getTickCount(),last=LAST.getOrDefault(actor.getUUID(),-100);
        if(tick-last<4)return;LAST.put(actor.getUUID(),tick);
        if(!authorized(actor,packet.pos,packet.token))return;
        var s=SESSIONS.get(actor.getUUID());var block=s.block;
        try{
            switch(packet.action){
                case "save"->block.configure(actor,QuestBlockConfig.parse(packet.data),packet.owner.isEmpty()?null:UUID.fromString(packet.owner),packet.size);
                case "return_items"->{
                    var stack=block.removeItem(0,64);
                    if(!stack.isEmpty()&&!actor.getInventory().add(stack))net.minecraft.world.level.block.Block.popResource(actor.level(),actor.blockPosition(),stack);
                }
                case "refresh"->block.refresh();
                default->throw new IllegalArgumentException("Unknown block action");
            }
            send(actor,s,"");
        }catch(Exception e){
            QuestPlusMod.LOGGER.warn("Quest block configuration rejected at {}: {}",block.getBlockPos(),e.toString());
            send(actor,s,"configuration_rejected");
        }
    }
    private static void send(ServerPlayer actor,Session session,String error){
        var be=session.block;var json=new JsonObject();
        json.add("config",QuestBlockConfig.JSON.toJsonTree(be.config));json.addProperty("editable",be.canConfigure(actor));
        json.addProperty("owner",be.owner==null?"":be.owner.toString());json.addProperty("team",be.boundTeam==null?"":be.boundTeam.toString());
        json.addProperty("kind",be.kind());json.addProperty("size",be.kind()==0?be.getBlockState().getValue(QuestUtilityBlock.SIZE):0);
        json.addProperty("status",be.status);json.addProperty("signal",be.signal);json.addProperty("error",error);
        json.addProperty("items",be.getItem(0).getCount());json.addProperty("fluid",be.fluids.amount/81);json.addProperty("energy",be.energy);
        json.add("rows",QuestBlockConfig.JSON.toJsonTree(be.rows));json.add("leaders",QuestBlockConfig.JSON.toJsonTree(be.leaders));
        json.addProperty("modular",be.kind()==0&&QuestBoardParts.modular(be.getBlockState()));
        json.addProperty("boardWidth",be.boardWidth);json.addProperty("boardHeight",be.boardHeight);
        var pins=new JsonObject();json.add("pins",pins);
        var choices=new JsonArray();
        try{
            var rt=QuestPlusMod.runtime();
            if(be.kind()==0&&be.owner!=null)for(String id:be.config.quests()) {
                var q=rt.registry().find(id);if(q.isPresent())pins.add(id,QuestBlockConfig.JSON.toJsonTree(be.boardRow(q.get(),be.boardState(be.owner,be.config.scope()))));
            }
            if(be.owner!=null)json.addProperty("ownerName",rt.player(be.owner).lastKnownName);
            json.addProperty("teamName",be.boundTeam==null?"":rt.teams().get(be.boundTeam).name);
            for(String id:be.config.quests()){
                var q=rt.registry().find(id);if(q.isPresent())json.addProperty("progress",Math.round(100*QuestUtilityEntity.fraction(q.get(),be.boardState(be.owner,be.config.scope()))));
            }
            for(var player:actor.level().getServer().getPlayerList().getPlayers().stream().limit(64).toList()){
                var p=new JsonObject();p.addProperty("id",player.getUUID().toString());p.addProperty("name",player.getPlainTextName());choices.add(p);
            }
        }catch(Exception ignored){}
        json.add("players",choices);
        String data=json.toString(),key=session.token+data;
        if(!key.equals(SENT.put(actor.getUUID(),key)))ServerPlayNetworking.send(actor,new Snapshot(be.getBlockPos(),session.token,data));
    }
}
