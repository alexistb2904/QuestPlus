package dev.questplus.fabric;

import dev.questplus.team.ProgressScope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;

public final class QuestWorldBlocksGameTest {
    @FunctionalInterface private interface Step {void run()throws Exception;}
    private static Runnable step(Step action){return ()->{try{action.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}};}
    @GameTest(maxTicks=5000)
    public void sharedScopePermissionsAndTeamChanges(GameTestHelper h)throws Exception {
        var path=fixture(h,"team","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var rt=QuestPlusMod.runtime();var level=h.getLevel();var a=player(h);var b=player(h);
        UUID aid=a.getUUID(),bid=b.getUUID();UUID[] team={null};QuestUtilityEntity[] station={null};
        Runnable idle=()->{
            if(rt.isBusy(aid)||rt.isBusy(bid)){java.util.concurrent.locks.LockSupport.parkNanos(1000000);h.fail("Await durable team operation");}
        };
        h.startSequence().thenExecute(step(()->rt.changeTeam(m->m.create(aid,"Block team"))))
                .thenWaitUntil(idle).thenExecute(step(()->{team[0]=rt.player(aid).teamId;rt.changeTeam(m->m.invite(aid,bid,System.currentTimeMillis()));}))
                .thenWaitUntil(idle).thenExecute(step(()->rt.changeTeam(m->m.join(bid,team[0],System.currentTimeMillis()))))
                .thenWaitUntil(idle).thenExecute(step(()->{
                    var p=h.absolutePos(new BlockPos(2,1,2));level.setBlock(p,QuestWorldBlocks.STATION.defaultBlockState(),3);
                    station[0]=(QuestUtilityEntity)level.getBlockEntity(p);var be=station[0];be.claim(aid);be.serverTick();
                    var cfg=new QuestBlockConfig(ProgressScope.TEAM,List.of("questplus:blocks_team"),"","items","pinned","players","completed","binary",1,true,63,true,false);
                    be.configure(a,cfg,null,0);h.assertTrue(!be.canConfigure(b),"Ordinary member cannot reconfigure owner's station");
                    try(var tx=Transaction.openOuter()){be.items.insert(ItemVariant.of(Items.STONE),2,tx);tx.commit();}be.refresh();
                    h.assertTrue(rt.state(bid).quests.get("questplus:blocks_team").counters.get("items")==2,"Selected team scope shared with second player");
                    h.assertTrue(!rt.player(bid).quests.containsKey("questplus:blocks_team"),"Personal progress remains separate");
                    rt.changeTeam(m->m.sharedQuestProgress(aid,false));
                })).thenWaitUntil(idle).thenExecute(step(()->{
                    station[0].refresh();h.assertTrue(!station[0].canPlaceItem(0,new ItemStack(Items.STONE)),"Sharing disabled pauses station");
                    rt.changeTeam(m->m.sharedQuestProgress(aid,true));
                })).thenWaitUntil(idle).thenExecute(step(()->{
                    h.assertTrue(station[0].canPlaceItem(0,new ItemStack(Items.STONE)),"Re-enabled original team resumes binding");
                    rt.changeTeam(m->m.transfer(aid,bid));
                })).thenWaitUntil(idle).thenExecute(step(()->rt.changeTeam(m->m.leave(aid))))
                .thenWaitUntil(idle).thenExecute(step(()->{
                    station[0].refresh();h.assertTrue(!station[0].canPlaceItem(0,new ItemStack(Items.STONE)),"Leaving team cannot retarget another scope");
                    rt.changeTeam(m->m.disband(bid));
                })).thenWaitUntil(idle).thenExecute(step(()->{
                    station[0].refresh();h.assertTrue(station[0].status.equals("blocked"),"Disbanded team remains safely blocked");
                    Files.delete(path);rt.reload();
                })).thenSucceed();
    }
    @GameTest(maxTicks=1200)
    public void pulseStagesAndTierAutomation(GameTestHelper h)throws Exception {
        var rt=QuestPlusMod.runtime();var level=h.getLevel();var actor=player(h);
        var folder=level.getServer().getWorldPath(LevelResource.ROOT).resolve("questplus/quests");var path=folder.resolve("blocks-tier.json5");
        Files.writeString(path,"""
                {schemaVersion:2,id:'questplus:blocks_tier',category:'%s',icon:'minecraft:stone',title:{en_us:'Tiers'},
                 progression:'tiered',objectives:[{id:'items',event:'item_turn_in',target:'minecraft:stone',count:2}],
                 tiers:[{id:'one',threshold:1,rewards:[]},{id:'two',threshold:2,rewards:[]}],afterLast:'stop',rewards:[]}
                """.formatted(rt.categories().getFirst().id()));rt.reload();
        var p=h.absolutePos(new BlockPos(2,1,2));level.setBlock(p,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var station=(QuestUtilityEntity)level.getBlockEntity(p);station.claim(actor.getUUID());station.serverTick();station.configure(actor,config("questplus:blocks_tier","items"),null,0);
        try(var tx=Transaction.openOuter()){station.items.insert(ItemVariant.of(Items.STONE),2,tx);tx.commit();}station.refresh();
        h.assertTrue(rt.state(actor.getUUID()).quests.get("questplus:blocks_tier").tier==1,"First tier completes");
        h.assertTrue(!station.canPlaceItem(0,new ItemStack(Items.STONE)),"Default stops before next tier");
        var cfg=new QuestBlockConfig(ProgressScope.PERSONAL,List.of("questplus:blocks_tier"),"","items","pinned","players","tier","analog",2,true,63,true,true);
        station.configure(actor,cfg,null,0);
        var r=p.east(2);level.setBlock(r,QuestWorldBlocks.RELAY.defaultBlockState(),3);
        var relay=(QuestUtilityEntity)level.getBlockEntity(r);relay.claim(actor.getUUID());relay.serverTick();relay.configure(actor,cfg,null,0);
        h.assertTrue(relay.signal==7,"One of two tiers gives analog signal seven");
        try(var tx=Transaction.openOuter()){station.items.insert(ItemVariant.of(Items.STONE),2,tx);tx.commit();}station.refresh();relay.refresh();
        h.assertTrue(relay.signal==15,"Final tier gives signal fifteen");
        var stage=new QuestBlockConfig(ProgressScope.PERSONAL,List.of(),"questplus:blocks_stage","","pinned","players","stage","pulse",1,true,63,false,false);
        relay.configure(actor,stage,null,0);h.assertTrue(relay.signal==0,"Stage initially locked");
        QuestStages.set(actor,"questplus:blocks_stage",true);relay.refresh();h.assertTrue(relay.signal==15,"Stage change emits pulse");
        h.startSequence().thenIdle(6).thenExecute(step(()->{
            h.assertTrue(relay.signal==0,"Pulse resets after four ticks");
            relay.refresh();h.assertTrue(relay.signal==0,"Unchanged stage does not emit another pulse");
            QuestStages.set(actor,"questplus:blocks_stage",false);relay.refresh();h.assertTrue(relay.signal==15,"Falling condition emits pulse");
            Files.delete(path);rt.reload();
        })).thenSucceed();
    }
    @GameTest(maxTicks=1200)
    public void relocationMovesBuffersOnceAndKeepsOwnership(GameTestHelper h)throws Exception {
        var path=fixture(h,"move","""
                {id:'items',event:'item_turn_in',target:'minecraft:stone',count:4},
                {id:'fluid',event:'fluid_submit',target:'minecraft:water',count:1000},
                {id:'energy',event:'energy_submit',target:'questplus:fe',count:1000}
                """);
        var level=h.getLevel();var actor=player(h);var p=h.absolutePos(new BlockPos(1,1,1));
        level.setBlock(p,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var source=(QuestUtilityEntity)level.getBlockEntity(p);source.claim(actor.getUUID());source.serverTick();
        source.configure(actor,config("questplus:blocks_move","items"),null,0);
        try(var tx=Transaction.openOuter()){source.items.insert(ItemVariant.of(Items.STONE),3,tx);tx.commit();}
        source.configure(actor,config("questplus:blocks_move","fluid"),null,0);
        try(var tx=Transaction.openOuter()){source.fluids.insert(FluidVariant.of(Fluids.WATER),8100,tx);tx.commit();}
        source.configure(actor,config("questplus:blocks_move","energy"),null,0);
        var energy=team.reborn.energy.api.EnergyStorage.SIDED.find(level,p,Direction.UP);
        var oldItems=ItemStorage.SIDED.find(level,p,Direction.UP);
        var oldFluid=FluidStorage.SIDED.find(level,p,Direction.UP);
        try(var tx=Transaction.openOuter()){energy.insert(500,tx);tx.commit();}
        var packed=Block.getDrops(source.getBlockState(),level,p,source).getFirst();
        level.removeBlock(p,false);
        var destination=p.east(2);level.setBlock(destination,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var moved=(QuestUtilityEntity)level.getBlockEntity(destination);moved.claim(actor.getUUID(),packed);
        h.assertTrue(moved.getItem(0).getCount()==3 && moved.fluids.amount==8100 && moved.energy==500,"All buffers move together");
        h.assertTrue(moved.owner.equals(actor.getUUID()),"Ownership retained by packed item");
        try(var tx=Transaction.openOuter()) {
            h.assertTrue(oldItems.extract(ItemVariant.of(Items.STONE),3,tx)==0,"Stale item capability cannot debit moved buffers");
            h.assertTrue(oldFluid.extract(FluidVariant.of(Fluids.WATER),8100,tx)==0,"Stale fluid capability disabled");
            h.assertTrue(energy.extract(500,tx)==0,"Stale energy capability disabled");
        }
        var duplicate=destination.east(2);level.setBlock(duplicate,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var second=(QuestUtilityEntity)level.getBlockEntity(duplicate);second.claim(actor.getUUID(),packed.copy());
        h.assertTrue(second.isEmpty()&&second.fluids.amount==0&&second.energy==0,"Replayed packed item cannot duplicate balances");
        h.assertTrue(!QuestBlockNetwork.authorized(actor,destination,UUID.randomUUID()),"Forged configuration token rejected");
        var other=player(h);h.assertTrue(!moved.canConfigure(other),"Other player cannot configure personal station");
        h.assertTrue(moved.getBlockState().getPistonPushReaction()==net.minecraft.world.level.material.PushReaction.IMMOVEABLE,"Piston movement rejected");
        Files.delete(path);QuestPlusMod.runtime().reload();h.succeed();
    }
    static QuestBlockConfig config(String quest,String objective){
        return new QuestBlockConfig(ProgressScope.PERSONAL,List.of(quest),"",objective,"pinned","players","completed","binary",1,true,63,true,false);
    }
    static ServerPlayer player(GameTestHelper h){
        var level=h.getLevel();var server=level.getServer();
        var p=new ServerPlayer(server,level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"blocktester"),ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection,p,CommonListenerCookie.createInitial(p.getGameProfile(),false));
        p.setGameMode(GameType.SURVIVAL);var pos=h.absolutePos(new BlockPos(2,2,2));p.setPos(pos.getX(),pos.getY(),pos.getZ());return p;
    }
    static Path fixture(GameTestHelper h,String name,String objectives)throws Exception{
        var rt=QuestPlusMod.runtime();var path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("questplus/quests/blocks-"+name+".json5");
        Files.writeString(path,"""
                {schemaVersion:2,id:'questplus:blocks_%s',category:'%s',icon:'minecraft:stone',title:{en_us:'Block integration'},
                 progression:'once',objectives:[%s],rewards:[{type:'experience',amount:3}]}
                """.formatted(name,rt.categories().getFirst().id(),objectives));rt.reload();return path;
    }
    @GameTest(maxTicks=1000)
    public void stationTransactionsOfflineFluidEnergyAndPersistence(GameTestHelper h)throws Exception{
        var path=fixture(h,"resources","""
                {id:'items',event:'item_turn_in',target:'minecraft:stone',count:4},
                {id:'fluid',event:'fluid_submit',target:'minecraft:water',count:1000},
                {id:'energy',event:'energy_submit',target:'questplus:fe',count:1000}
                """);
        var level=h.getLevel();var actor=player(h);var pos=h.absolutePos(new BlockPos(2,1,2));
        level.setBlock(pos,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var be=(QuestUtilityEntity)level.getBlockEntity(pos);be.claim(actor.getUUID());be.serverTick();
        be.configure(actor,config("questplus:blocks_resources","items"),null,0);
        var storage=ItemStorage.SIDED.find(level,pos,Direction.UP);
        h.assertTrue(storage!=null,"Sided item capability registered");
        try(var tx=Transaction.openOuter()){
            h.assertTrue(storage.insert(ItemVariant.of(Items.DIRT),64,tx)==0,"Useless input rejected");
            h.assertTrue(storage.insert(ItemVariant.of(Items.STONE),64,tx)==4,"Input bounded to objective");
        }
        h.assertTrue(be.isEmpty(),"Aborted insertion restores inventory");
        try(var tx=Transaction.openOuter()){storage.insert(ItemVariant.of(Items.STONE),4,tx);tx.commit();}
        var saved=be.saveWithFullMetadata(level.registryAccess());
        // Reloading the block resolves the journalled balance rather than stale chunk contents.
        level.removeBlockEntity(pos);var restored=(QuestUtilityEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,QuestWorldBlocks.STATION.defaultBlockState(),saved,level.registryAccess());
        level.setBlockEntity(restored);restored.serverTick();
        h.assertTrue(restored.getItem(0).getCount()==4,"Buffered items survive block reload");
        level.getServer().getPlayerList().remove(actor);
        restored.refresh();
        h.assertTrue(restored.isEmpty(),"Offline owner consumes resources");
        h.assertTrue(QuestPlusMod.runtime().state(actor.getUUID()).quests.get("questplus:blocks_resources").counters.get("items")==4,"Offline progress committed");
        restored.refresh();h.assertTrue(QuestPlusMod.runtime().player(actor.getUUID()).blockContributions==4,"No duplicate consumption");
        restored.configure(actor,config("questplus:blocks_resources","fluid"),null,0);
        var fluid=FluidStorage.SIDED.find(level,pos,Direction.UP);
        try(var tx=Transaction.openOuter()){h.assertTrue(fluid.insert(FluidVariant.of(Fluids.LAVA),81000,tx)==0,"Wrong fluid rejected");}
        try(var tx=Transaction.openOuter()){h.assertTrue(fluid.insert(FluidVariant.of(Fluids.WATER),81000,tx)==81000,"Fluid capability accepts one bucket");tx.commit();}
        restored.refresh();h.assertTrue(restored.fluids.amount==0,"Fluid consumed using 81 droplets per mB");
        restored.configure(actor,config("questplus:blocks_resources","energy"),null,0);
        var energy=team.reborn.energy.api.EnergyStorage.SIDED.find(level,pos,Direction.UP);
        h.assertTrue(energy!=null,"Optional energy capability registered");
        try(var tx=Transaction.openOuter()){h.assertTrue(energy.insert(1000,tx)==1000,"Energy accepted");}
        h.assertTrue(restored.energy==0,"Energy rollback");
        try(var tx=Transaction.openOuter()){energy.insert(1000,tx);tx.commit();}
        restored.refresh();h.assertTrue(restored.energy==0,"Energy consumed");
        h.assertTrue(QuestPlusMod.runtime().state(actor.getUUID()).quests.get("questplus:blocks_resources").completed,"All resources complete one quest");
        h.assertTrue(QuestPlusMod.runtime().player(actor.getUUID()).entitlements.containsKey("questplus:blocks_resources"),"Offline reward entitlement retained");
        Files.delete(path);QuestPlusMod.runtime().reload();h.succeed();
    }
    @GameTest(maxTicks=1200)
    public void hopperAndInvalidTargetKeepResources(GameTestHelper h)throws Exception{
        var path=fixture(h,"hopper","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var level=h.getLevel();var actor=player(h);var pos=h.absolutePos(new BlockPos(2,1,2));
        level.setBlock(pos,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var be=(QuestUtilityEntity)level.getBlockEntity(pos);be.claim(actor.getUUID());be.serverTick();be.configure(actor,config("questplus:blocks_hopper","items"),null,0);
        var above=pos.above();level.setBlock(above,Blocks.HOPPER.defaultBlockState(),3);
        var hopper=(HopperBlockEntity)level.getBlockEntity(above);hopper.setItem(0,new ItemStack(Items.STONE,5));
        h.startSequence().thenWaitUntil(()->{
            try {
                var progress=QuestPlusMod.runtime().state(actor.getUUID());
                h.assertTrue(progress.quests.containsKey("questplus:blocks_hopper")
                        && progress.quests.get("questplus:blocks_hopper").completed,"Hopper completed the submission");
            } catch(java.io.IOException error) { throw new RuntimeException(error); }
        })
                .thenExecute(()->{
                    h.assertTrue(hopper.getItem(0).getCount()==1,"Surplus stays in hopper");
                    try{Files.delete(path);QuestPlusMod.runtime().reload();}catch(Exception e){throw new RuntimeException(e);}
                    h.assertTrue(!be.canPlaceItem(0,new ItemStack(Items.STONE)),"Removed quest refuses input");
                }).thenSucceed();
    }
    @GameTest(maxTicks=1200)
    public void multiblockSizesOneEntityAndRelaySignals(GameTestHelper h)throws Exception{
        var path=fixture(h,"board","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var level=h.getLevel();var actor=player(h);var origin=h.absolutePos(new BlockPos(0,1,4));
        for(int y=0;y<4;y++)for(int x=0;x<6;x++)level.setBlock(origin.offset(x,y,1),Blocks.STONE.defaultBlockState(),3);
        QuestUtilityBlock.assemble(level,origin,Direction.NORTH,0);
        var board=(QuestUtilityEntity)level.getBlockEntity(origin);board.claim(actor.getUUID());board.serverTick();
        for(int size:new int[]{1,2,3,0}){
            board.configure(actor,config("questplus:blocks_board","items"),null,size);
            int entities=0;
            for(int y=0;y<QuestWorldBlocks.HEIGHT[size];y++)for(int x=0;x<QuestWorldBlocks.WIDTH[size];x++)
                if(level.getBlockEntity(origin.offset(x,y,0)) instanceof QuestUtilityEntity)entities++;
            h.assertTrue(entities==1,"One entity for size "+size);
        }
        h.assertTrue(board.rows.size()==1,"Board caches pinned quest");
        var relayPos=h.absolutePos(new BlockPos(2,1,2));level.setBlock(relayPos,QuestWorldBlocks.RELAY.defaultBlockState(),3);
        var relay=(QuestUtilityEntity)level.getBlockEntity(relayPos);relay.claim(actor.getUUID());relay.serverTick();
        relay.configure(actor,config("questplus:blocks_board","items"),null,0);
        h.assertTrue(relay.signal==0,"Incomplete binary output");
        QuestPlusMod.runtime().setObjectiveProgress(actor,"questplus:blocks_board","items",2);
        var analog=new QuestBlockConfig(ProgressScope.PERSONAL,List.of("questplus:blocks_board"),"","","pinned","players","completed","analog",1,true,63,false,false);
        relay.configure(actor,analog,null,0);h.assertTrue(relay.signal==7,"Half progress analog output");
        QuestPlusMod.runtime().setObjectiveProgress(actor,"questplus:blocks_board","items",4);relay.refresh();
        h.assertTrue(relay.signal==15,"Complete analog output");
        h.assertTrue(level.getSignal(relayPos,Direction.UP)==15,"Real redstone output");
        level.destroyBlock(origin.offset(2,1,0),true);
        h.assertTrue(level.getBlockState(origin).isAir(),"Destroying a part removes the master");
        Files.delete(path);QuestPlusMod.runtime().reload();h.succeed();
    }
}
