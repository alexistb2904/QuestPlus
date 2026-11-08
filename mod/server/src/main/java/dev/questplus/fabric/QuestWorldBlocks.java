package dev.questplus.fabric;

import dev.questplus.persistence.BlockAccount;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import java.util.*;

public final class QuestWorldBlocks {
    public static QuestUtilityBlock BOARD, STATION, RELAY;
    public static BlockEntityType<QuestUtilityEntity> ENTITY;
    static final int[] WIDTH = {3,4,4,6}, HEIGHT = {2,2,3,4};
    private static final Set<QuestUtilityEntity> LOADED = new HashSet<>();
    private static final LinkedHashSet<QuestUtilityEntity> DIRTY = new LinkedHashSet<>();
    private static final Set<QuestUtilityEntity> BOARD_GEOMETRY = new HashSet<>();
    private static final Map<UUID, Set<QuestUtilityEntity>> OWNERS = new HashMap<>();
    private static final Set<QuestUtilityEntity> LEADERBOARDS = new HashSet<>();
    private QuestWorldBlocks() {}
    public static void register() {
        BOARD = block("quest_board", 0);
        STATION = block("submission_station", 1);
        RELAY = block("quest_relay", 2);
        ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.parse("questplus:utility"),
                FabricBlockEntityTypeBuilder.create(QuestUtilityEntity::new, BOARD, STATION, RELAY).build());
        ItemStorage.SIDED.registerForBlockEntity((be, side) -> !be.input(side) ? null : new net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage<>(be.items) {
            protected boolean canInsert(ItemVariant value) {return be.input(side);}
            protected boolean canExtract(ItemVariant value) {return be.input(side);}
        }, ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((be, side) -> !be.input(side) ? null : new net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage<>(be.fluids) {
            protected boolean canInsert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant value) {return be.input(side);}
            protected boolean canExtract(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant value) {return be.input(side);}
        }, ENTITY);
        if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) QuestBlockEnergy.register();
        QuestBlockNetwork.register();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int budget = 32;
            long deadline=System.nanoTime()+2_000_000;
            while (budget-- > 0 && !DIRTY.isEmpty() && System.nanoTime()<deadline) {
                var be = DIRTY.iterator().next(); DIRTY.remove(be);
                if (!be.isRemoved() && be.getLevel() != null && be.getLevel().hasChunkAt(be.getBlockPos())) {
                    if(BOARD_GEOMETRY.remove(be)) {
                        BOARD_GEOMETRY.removeAll(QuestBoardParts.connected(be));
                        QuestBoardParts.rebuild(be);
                    }
                    be.refresh();
                }
            }
            QuestBlockNetwork.tick(server);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LOADED.clear(); DIRTY.clear(); BOARD_GEOMETRY.clear(); OWNERS.clear(); LEADERBOARDS.clear(); QuestBlockNetwork.clear();
        });
    }
    private static QuestUtilityBlock block(String name, int kind) {
        var id = Identifier.parse("questplus:"+name);
        var key = ResourceKey.create(Registries.BLOCK, id);
        var properties = BlockBehaviour.Properties.of().setId(key).strength(2.5f).sound(SoundType.WOOD)
                .noOcclusion().pushReaction(PushReaction.IMMOVEABLE);
        var block = Registry.register(BuiltInRegistries.BLOCK, key, switch(kind) {
            case 0 -> new QuestUtilityBlock.Board(properties); case 1 -> new QuestUtilityBlock.Station(properties);
            default -> new QuestUtilityBlock.Relay(properties);
        });
        var itemKey = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block,
                new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
    static void loaded(QuestUtilityEntity be) {
        LOADED.add(be);
        if(be.getBlockState().is(BOARD))LEADERBOARDS.add(be);
        if(QuestBoardParts.modular(be.getBlockState()))BOARD_GEOMETRY.add(be);
        if (be.owner != null) OWNERS.computeIfAbsent(be.owner, k -> new HashSet<>()).add(be);
        enqueue(be);
    }
    static void unloaded(QuestUtilityEntity be) {
        LOADED.remove(be); DIRTY.remove(be); BOARD_GEOMETRY.remove(be); LEADERBOARDS.remove(be);
        if (be.owner != null) {
            var set = OWNERS.get(be.owner);
            if (set != null) { set.remove(be); if (set.isEmpty()) OWNERS.remove(be.owner); }
        }
    }
    static void enqueue(QuestUtilityEntity be) { DIRTY.add(be); }
    public static void invalidate(UUID owner) {
        DIRTY.addAll(OWNERS.getOrDefault(owner, Set.of()));
        DIRTY.addAll(LEADERBOARDS);
    }
    static void invalidateAll() { DIRTY.addAll(LOADED); }
}
