package dev.questplus.fabric;

import dev.questplus.core.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-owned six-block raycast. Walls and a changed target break the dwell time. */
final class ObservationEvents {
    private static final Map<UUID,ObservationCounter> COUNTERS=new HashMap<>();
    private ObservationEvents() {}
    static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->COUNTERS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->COUNTERS.clear());
    }
    static void tick(MinecraftServer server) {
        var runtime=QuestPlusMod.runtime();var registry=runtime.registry();
        if(!registry.hasEvent("observe_block")&&!registry.hasEvent("observe_entity"))return;
        for(var player:server.getPlayerList().getPlayers()) {
            var counter=COUNTERS.computeIfAbsent(player.getUUID(),id->new ObservationCounter());
            String identity=null,type=null,target=null;Set<String> tags=Set.of();
            if(!player.isCreative()&&!player.isSpectator()) {
                var eye=player.getEyePosition();var view=player.getViewVector(1).scale(6);var end=eye.add(view);
                var block=player.pick(6,1,false);
                double limit=block.getType()==HitResult.Type.MISS?36:eye.distanceToSqr(block.getLocation());
                var entity=ProjectileUtil.getEntityHitResult(player,eye,end,player.getBoundingBox().expandTowards(view).inflate(1),
                    candidate->!candidate.isSpectator()&&candidate.isPickable(),limit);
                if(entity!=null) {
                    var found=entity.getEntity();type="observe_entity";target=BuiltInRegistries.ENTITY_TYPE.getKey(found.getType()).toString();
                    tags=found.getType().builtInRegistryHolder().tags().map(t->t.location().toString()).collect(java.util.stream.Collectors.toSet());
                    identity=player.level().dimension().identifier()+":entity:"+found.getUUID();
                } else if(block instanceof BlockHitResult hit && block.getType()==HitResult.Type.BLOCK) {
                    var state=player.level().getBlockState(hit.getBlockPos());type="observe_block";target=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    tags=state.typeHolder().tags().map(t->t.location().toString()).collect(java.util.stream.Collectors.toSet());
                    identity=player.level().dimension().identifier()+":block:"+hit.getBlockPos().asLong()+":"+target;
                }
            }
            long ticks=counter.update(identity,server.getTickCount());
            for(String event:List.of("observe_block","observe_entity")) {
                var values=new HashMap<String,Long>();
                for(var objective:registry.objectives(event)) {
                    String authored=objective.target();
                    values.put(authored,event.equals(type) && (authored.equals(target)||authored.startsWith("#")&&tags.contains(authored.substring(1)))?ticks:0L);
                }
                if(values.isEmpty())continue;
                var context=QuestPlusMod.context(player);context.put("stateValues",values);
                // One event per authored target also resets stale progress when looking away.
                for(String authored:values.keySet())runtime.event(player,new QuestEvent(event,authored.startsWith("#")?"minecraft:air":authored,
                    authored.startsWith("#")?Set.of(authored.substring(1)):Set.of(),1,context));
            }
        }
    }
}
