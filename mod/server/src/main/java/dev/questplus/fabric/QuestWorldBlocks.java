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
    private static final Map<String,List<QuestUtilityEntity.Row>> RANKINGS = new HashMap<>();
    private static int rankingTick = Integer.MIN_VALUE;
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
            LOADED.clear(); DIRTY.clear(); BOARD_GEOMETRY.clear(); OWNERS.clear(); LEADERBOARDS.clear(); RANKINGS.clear();
            rankingTick=Integer.MIN_VALUE; QuestBlockNetwork.clear();
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
    static void enqueue(QuestUtilityEntity be) {
        if(QuestBoardParts.modular(be.getBlockState()) && be.boardWidth==0 && !BOARD_GEOMETRY.contains(be))return;
        DIRTY.add(be);
    }
    public static void invalidate(UUID owner) {
        RANKINGS.clear();
        OWNERS.getOrDefault(owner, Set.of()).forEach(QuestWorldBlocks::enqueue);
        LEADERBOARDS.forEach(QuestWorldBlocks::enqueue);
    }
    static void invalidateAll() { RANKINGS.clear(); LOADED.forEach(QuestWorldBlocks::enqueue); }

    static List<QuestUtilityEntity.Row> ranking(net.minecraft.server.level.ServerLevel level,String kind) throws java.io.IOException {
        int now=level.getServer().getTickCount();
        if(rankingTick!=now) {RANKINGS.clear();rankingTick=now;}
        var cached=RANKINGS.get(kind);if(cached!=null)return cached;
        var rt=QuestPlusMod.runtime();
        record Score(UUID id,String name,long score){}
        var scores=new ArrayList<Score>();
        if(kind.equals("teams")) for(var t:rt.teams().allTeams())
            scores.add(new Score(t.owner,t.name,t.progress.quests.values().stream().filter(q->q.completed||q.terminal).count()));
        else for(UUID id:rt.knownPlayers()) {
            var personal=rt.player(id);var online=level.getServer().getPlayerList().getPlayer(id);
            String name=online==null?(personal.lastKnownName.isEmpty()?id.toString().substring(0,8):personal.lastKnownName):online.getPlainTextName();
            long score=kind.equals("contributions")?personal.blockContributions
                    :rt.state(id).quests.values().stream().filter(q->q.completed||q.terminal).count();
            scores.add(new Score(id,name,score));
        }
        scores.sort(Comparator.comparingLong(Score::score).reversed().thenComparing(s->s.id().toString()));
        var rows=new ArrayList<QuestUtilityEntity.Row>();int rank=1;
        for(var score:scores.subList(0,Math.min(12,scores.size())))
            rows.add(new QuestUtilityEntity.Row(rank+++". "+score.name(),"minecraft:player_head",Long.toString(score.score()),"",score.id().toString(),"",0,Long.toString(score.score())));
        var result=List.copyOf(rows);RANKINGS.put(kind,result);return result;
    }
}
