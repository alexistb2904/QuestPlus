package dev.questplus.fabric;

import dev.questplus.core.QuestEvent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.*;
import java.util.*;
import java.util.stream.Collectors;

public final class QuestPlusMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("questplus");
    private static QuestRuntime runtime;
    private static volatile String startupError;
    public static String unavailableReason() {
        return startupError==null ? "Quest Plus has not loaded quest definitions" : startupError;
    }
    private static int networkTicks;
    private static QuestHubService hub;
    static QuestHubService hub() {
        if(hub==null)throw new IllegalStateException("QuestPlus Hub not initialized");
        return hub;
    }
    private record BreakTool(BlockPos position,String tool) {}
    private static final Map<UUID,BreakTool> BREAK_TOOLS = new HashMap<>();

    public static QuestRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("Quest Plus unavailable: "+unavailableReason());
        return runtime;
    }

    @Override public void onInitialize() {
        QuestCrateItem.register();
        QuestTabletItem.register();
        QuestCreativeTab.register();
        QuestNetwork.register();
        CommandRewards.install();
        TabletPoseNetwork.register();
        AdvancedEvents.register();
        ObservationEvents.register();
        ParityRewards.install();
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> QuestCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                runtime = new QuestRuntime(server);
                hub = new QuestHubService(server);
                startupError=null;
                LOGGER.info("Quest Plus ready: {} validated quests",runtime.registry().all().size());
            } catch (Exception e) {
                startupError=Objects.toString(e.getMessage(),e.getClass().getSimpleName());
                LOGGER.error("Quest Plus disabled: {}",startupError,e);
                server.getPlayerList().getPlayers().forEach(player -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Quest Plus: "+startupError)));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if(hub!=null){hub.close();hub=null;}
            if (runtime != null) {
                try { runtime.close(); } catch (Exception e) { LOGGER.error("Quest Plus shutdown save failed",e); }
                runtime = null;
            }
            startupError=null;
            BREAK_TOOLS.clear();
            QuestNetwork.clear();
            TabletPoseNetwork.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (runtime != null) { runtime.tick(); AdvancedEvents.tick(server); ObservationEvents.tick(server); QuestNetwork.tick(server,++networkTicks); TabletPoseNetwork.tick(server); if(hub!=null)hub.tick(); } });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> {
            if (runtime != null) runtime.disconnect(handler.player.getUUID());
            QuestNetwork.disconnect(handler.player.getUUID(),server);
            if(hub!=null)hub.disconnect(handler.player.getUUID());
            TabletPoseNetwork.disconnect(handler.player);
            BREAK_TOOLS.remove(handler.player.getUUID());
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,blockEntity) -> {
            if (player instanceof ServerPlayer) BREAK_TOOLS.put(player.getUUID(),new BreakTool(pos.immutable(),BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()));
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world,player,pos,state,blockEntity) -> {
            if (runtime != null && player instanceof ServerPlayer serverPlayer) {
                var context = context(serverPlayer);
                var tool = BREAK_TOOLS.remove(player.getUUID());
                if (tool != null && tool.position().equals(pos)) context.put("tool",tool.tool());
                runtime.event(serverPlayer,new QuestEvent("block_break",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    state.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet()),1,context));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source) -> {
            if (runtime == null) return;
            runtime.dropCrate(entity);
            if (entity instanceof ServerPlayer dead) runtime.event(dead,new QuestEvent("player_death","questplus:death",Set.of(),1,context(dead)));
            var responsible = source.getEntity() instanceof ServerPlayer player ? player
                : entity.getKillCredit() instanceof ServerPlayer player ? player : null;
            if (responsible != null && responsible!=entity) runtime.event(responsible,new QuestEvent("entity_kill",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).tags().map(t -> t.location().toString()).collect(Collectors.toSet()),1,context(responsible)));
        });
    }

    public static void pickedUp(ServerPlayer player,ItemStack item,int count) {
        if (runtime != null && count > 0) AdvancedEvents.item(player,"item_obtained",item,count);
    }

    public static Map<String,Object> context(ServerPlayer player) {
        var context=new HashMap<String,Object>(Map.of("dimension",player.level().dimension().identifier().toString(),"health",player.getHealth(),"time",System.currentTimeMillis(),
            "sneaking",player.isShiftKeyDown(),"tool",BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString()));
        context.putAll(AdvancedEvents.context(player));
        context.put("gear",AdvancedEvents.gearSnapshot(player));
        context.put("stages",QuestStages.stages(player));
        return context;
    }
}
