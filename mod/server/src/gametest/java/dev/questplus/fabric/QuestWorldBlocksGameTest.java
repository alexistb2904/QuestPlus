package dev.questplus.fabric;

import dev.questplus.team.ProgressScope;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
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
    @GameTest(maxTicks=200)
    public void sharedRankingIsReusedAndInvalidatedWithinTheTick(GameTestHelper h)throws Exception {
        var actor=player(h);var rt=QuestPlusMod.runtime();var level=h.getLevel();
        var boards=new ArrayList<QuestUtilityEntity>();
        for(int i=0;i<2;i++) {
            var pos=h.absolutePos(new BlockPos(i*4,8,1));level.setBlock(pos,QuestWorldBlocks.BOARD.defaultBlockState(),3);
            var be=(QuestUtilityEntity)level.getBlockEntity(pos);be.claim(actor.getUUID());
            be.config=QuestBlockConfig.parse(QuestBlockConfig.DEFAULT.encode().replace("\"leaderboard\":\"players\"","\"leaderboard\":\"contributions\""));
            boards.add(be);
        }
        QuestWorldBlocks.invalidate(actor.getUUID());boards.forEach(QuestUtilityEntity::refresh);
        h.assertTrue(boards.getFirst().leaders==boards.getLast().leaders,"Boards share one immutable ranking in the tick");
        var before=boards.getFirst().leaders;
        rt.player(actor.getUUID()).blockContributions=123456;rt.changed(actor.getUUID());boards.forEach(QuestUtilityEntity::refresh);
        h.assertTrue(boards.getFirst().leaders!=before,"Progress invalidates a ranking immediately");
        h.assertTrue(boards.getFirst().leaders.stream().anyMatch(r->r.player().equals(actor.getUUID().toString())&&r.progress().equals("123456")),"Contribution changes appear in the same tick");
        for(var be:boards)level.removeBlock(be.getBlockPos(),false);
        level.getServer().getPlayerList().remove(actor);rt.disconnect(actor.getUUID());
        h.succeed();
    }
    @GameTest(maxTicks=1200)
    public void modularBoardsConnectInEveryDirectionAndDropOnce(GameTestHelper h)throws Exception {
        var path=fixture(h,"modules","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var level=h.getLevel();var actor=player(h);var origin=h.absolutePos(new BlockPos(4,10,4));
        for(var facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            actor.setPos(origin.getX()+facing.getStepX()*4,origin.getY(),origin.getZ()+facing.getStepZ()*4);
            for(int y=0;y<3;y++)for(int x=0;x<9;x++) {
                var pos=QuestUtilityBlock.at(origin,facing,x,y);var wall=pos.relative(facing.getOpposite());
                level.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
                var stack=new ItemStack(QuestWorldBlocks.BOARD,2);actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall),facing,wall,false);
                ((BlockItem)QuestWorldBlocks.BOARD.asItem())
                        .place(new net.minecraft.world.item.context.BlockPlaceContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit));
                h.assertTrue(level.getBlockState(pos).is(QuestWorldBlocks.BOARD),"One module placed: "+facing+" "+x+","+y);
                h.assertTrue(stack.getCount()==1,"Each placement consumes exactly one item");
                if(x==0&&y==0)((QuestUtilityEntity)level.getBlockEntity(pos)).configure(actor,config("questplus:blocks_modules","items"),null,0);
            }
            var board=(QuestUtilityEntity)level.getBlockEntity(origin);
            h.assertTrue(board.boardWidth==9&&board.boardHeight==3,"Free-size 9 by 3 assembly: "+facing);
            board.configure(actor,config("questplus:blocks_modules","items"),null,0);
            var middle=QuestUtilityBlock.at(origin,facing,2,1);
            h.assertTrue(level.getBlockState(middle).getValue(QuestUtilityBlock.EDGES)==0,"Interior has no frame");
            var center=(QuestUtilityEntity)level.getBlockEntity(middle);
            h.assertTrue(center.config.quests().contains("questplus:blocks_modules"),"New parts inherit settings");
            var area=new net.minecraft.world.phys.AABB(origin).inflate(13);
            for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))entity.discard();
            level.destroyBlock(middle,true);
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area);
            h.assertTrue(drops.stream().filter(e->e.getItem().is(QuestWorldBlocks.BOARD.asItem())).mapToInt(e->e.getItem().getCount()).sum()==1,"Breaking one module drops exactly one board");
            h.assertTrue(level.getBlockState(origin).is(QuestWorldBlocks.BOARD),"Breaking the middle leaves other modules intact");
            level.destroyBlock(origin,true);
            var survivor=(QuestUtilityEntity)level.getBlockEntity(origin.above());
            h.assertTrue(survivor!=null&&survivor.config.equals(board.config),"Breaking anchor preserves other settings");
            for(int y=0;y<3;y++)for(int x=0;x<9;x++) {
                var pos=QuestUtilityBlock.at(origin,facing,x,y);level.removeBlock(pos,false);level.removeBlock(pos.relative(facing.getOpposite()),false);
            }
            for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))entity.discard();
        }
        Files.delete(path);QuestPlusMod.runtime().reload();h.succeed();
    }
    @GameTest(maxTicks=1200)
    public void modularBoardLimitAndOwnershipAreValidatedBeforeMutation(GameTestHelper h)throws Exception {
        var level=h.getLevel();var actor=player(h);var other=player(h);var origin=h.absolutePos(new BlockPos(2,15,2));
        var state=QuestWorldBlocks.BOARD.defaultBlockState().setValue(QuestUtilityBlock.MODULAR,true);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++){
            var pos=origin.offset(x,y,0);level.setBlock(pos.south(),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos,state,3);
            ((QuestUtilityEntity)level.getBlockEntity(pos)).claim(actor.getUUID());
        }
        var board=(QuestUtilityEntity)level.getBlockEntity(origin);QuestBoardParts.rebuild(board);
        h.assertTrue(board.boardWidth==16&&board.boardHeight==16,"256 modules form a complete board");
        var extra=origin.east(16);level.setBlock(extra.south(),Blocks.STONE.defaultBlockState(),3);
        var stack=new ItemStack(QuestWorldBlocks.BOARD);actor.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(extra.south()),Direction.NORTH,extra.south(),false);
        var context=new net.minecraft.world.item.context.BlockPlaceContext(actor,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit);
        h.assertTrue(QuestWorldBlocks.BOARD.getStateForPlacement(context)==null,"257th same-owner module is rejected");
        level.setBlock(extra,state,3);var separate=(QuestUtilityEntity)level.getBlockEntity(extra);separate.claim(other.getUUID());
        QuestBoardParts.rebuild(separate);h.assertTrue(QuestBoardParts.connected(separate).size()==1,"Different owners do not merge");
        other.setPos(extra.getX(),extra.getY(),extra.getZ()-2);
        boolean rejected=false;try{separate.configure(other,QuestBlockConfig.DEFAULT,actor.getUUID(),0);}catch(IllegalArgumentException expected){rejected=true;}
        h.assertTrue(rejected&&separate.owner.equals(other.getUUID()),"Oversized ownership transfer changes nothing");
        h.succeed();
    }
    @GameTest(maxTicks=1200)
    public void legacyBoardBreaksWithoutDuplicateDrops(GameTestHelper h) {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(0,1,4));
        for(int y=0;y<2;y++)for(int x=0;x<3;x++)level.setBlock(origin.offset(x,y,1),Blocks.STONE.defaultBlockState(),3);
        var area=new net.minecraft.world.phys.AABB(origin).inflate(5);
        for(int broken=0;broken<6;broken++) {
            for(var item:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))item.discard();
            QuestUtilityBlock.assemble(level,origin,Direction.NORTH,0);
            level.destroyBlock(origin.offset(broken%3,broken/3,0),true);
            int count=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream()
                    .filter(e->e.getItem().is(QuestWorldBlocks.BOARD.asItem())).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(count==1,"Legacy piece "+broken+" must drop exactly one board, got "+count);
        }
        h.succeed();
    }
    /** Loot tables carry both pool condition formats (26.1/26.2 "conditions", 26.3 "condition"); each target must read its own. */
    @GameTest(maxTicks=100)
    public void blockLootSurvivesExplosionConditionOnEveryTarget(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,1,1));
        for(var block:List.of(QuestWorldBlocks.BOARD,QuestWorldBlocks.STATION,QuestWorldBlocks.RELAY)) {
            java.util.function.Function<Float,List<ItemStack>> drops=radius->{
                var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.atCenterOf(pos))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL,ItemStack.EMPTY);
                if(radius!=null)params.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.EXPLOSION_RADIUS,radius);
                return block.defaultBlockState().getDrops(params);
            };
            h.assertTrue(drops.apply(null).stream().anyMatch(s->s.is(block.asItem())),block+" drops itself");
            h.assertTrue(drops.apply(Float.MAX_VALUE).isEmpty(),block+" honours survives_explosion");
        }
        h.succeed();
    }
    @GameTest(maxTicks=1200)
    public void boardSnapshotCodecKeepsLargePinnedPayload(GameTestHelper h) {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var packet=new QuestBlockNetwork.Snapshot(BlockPos.ZERO,UUID.randomUUID(),"x".repeat(80000));
            QuestBlockNetwork.Snapshot.CODEC.encode(buffer,packet);
            h.assertTrue(packet.equals(QuestBlockNetwork.Snapshot.CODEC.decode(buffer)),"Expanded board snapshot round trip");
        }finally{buffer.release();}
        h.succeed();
    }
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
                    LoaderTestSupport.insertItems(be,Items.STONE,2);be.refresh();
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
        LoaderTestSupport.insertItems(station,Items.STONE,2);station.refresh();
        h.assertTrue(rt.state(actor.getUUID()).quests.get("questplus:blocks_tier").tier==1,"First tier completes");
        h.assertTrue(!station.canPlaceItem(0,new ItemStack(Items.STONE)),"Default stops before next tier");
        var cfg=new QuestBlockConfig(ProgressScope.PERSONAL,List.of("questplus:blocks_tier"),"","items","pinned","players","tier","analog",2,true,63,true,true);
        station.configure(actor,cfg,null,0);
        var r=p.east(2);level.setBlock(r,QuestWorldBlocks.RELAY.defaultBlockState(),3);
        var relay=(QuestUtilityEntity)level.getBlockEntity(r);relay.claim(actor.getUUID());relay.serverTick();relay.configure(actor,cfg,null,0);
        h.assertTrue(relay.signal==7,"One of two tiers gives analog signal seven");
        LoaderTestSupport.insertItems(station,Items.STONE,2);station.refresh();relay.refresh();
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
        LoaderTestSupport.insertItems(source,Items.STONE,3);
        source.configure(actor,config("questplus:blocks_move","fluid"),null,0);
        LoaderTestSupport.insertFluid(source,Fluids.WATER,8100);
        source.configure(actor,config("questplus:blocks_move","energy"),null,0);
        var old=LoaderTestSupport.probe(level,p,Direction.UP);
        old.insertEnergy(500,true);
        var packed=Block.getDrops(source.getBlockState(),level,p,source).getFirst();
        level.removeBlock(p,false);
        var destination=p.east(2);level.setBlock(destination,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var moved=(QuestUtilityEntity)level.getBlockEntity(destination);moved.claim(actor.getUUID(),packed);
        h.assertTrue(moved.getItem(0).getCount()==3 && moved.fluidAmount==8100 && moved.energy==500,"All buffers move together");
        h.assertTrue(moved.owner.equals(actor.getUUID()),"Ownership retained by packed item");
        h.assertTrue(old.extractItems(Items.STONE,3,false)==0,"Stale item capability cannot debit moved buffers");
        h.assertTrue(old.extractFluid(Fluids.WATER,8100,false)==0,"Stale fluid capability disabled");
        h.assertTrue(old.extractEnergy(500,false)==0,"Stale energy capability disabled");
        var duplicate=destination.east(2);level.setBlock(duplicate,QuestWorldBlocks.STATION.defaultBlockState(),3);
        var second=(QuestUtilityEntity)level.getBlockEntity(duplicate);second.claim(actor.getUUID(),packed.copy());
        h.assertTrue(second.isEmpty()&&second.fluidAmount==0&&second.energy==0,"Replayed packed item cannot duplicate balances");
        h.assertTrue(!QuestBlockNetwork.authorized(actor,destination,UUID.randomUUID()),"Forged configuration token rejected");
        var other=player(h);h.assertTrue(!moved.canConfigure(other),"Other player cannot configure personal station");
        h.assertTrue(moved.getBlockState().getPistonPushReaction()==dev.questplus.compat.CommonAdapter.INSTANCE.immovable(),"Piston movement rejected");
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
        var storage=LoaderTestSupport.probe(level,pos,Direction.UP);
        h.assertTrue(storage.hasItems(),"Sided item capability registered");
        h.assertTrue(storage.insertItems(Items.DIRT,64,false)==0,"Useless input rejected");
        h.assertTrue(storage.insertItems(Items.STONE,64,false)==4,"Input bounded to objective");
        h.assertTrue(be.isEmpty(),"Aborted insertion restores inventory");
        storage.insertItems(Items.STONE,4,true);
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
        var fluid=LoaderTestSupport.probe(level,pos,Direction.UP);
        h.assertTrue(fluid.insertFluid(Fluids.LAVA,81000,false)==0,"Wrong fluid rejected");
        h.assertTrue(fluid.insertFluid(Fluids.WATER,81000,true)==81000,"Fluid capability accepts one bucket");
        restored.refresh();h.assertTrue(restored.fluidAmount==0,"Fluid consumed using 81 droplets per mB");
        restored.configure(actor,config("questplus:blocks_resources","energy"),null,0);
        var energy=LoaderTestSupport.probe(level,pos,Direction.UP);
        h.assertTrue(energy.hasEnergy(),"Optional energy capability registered");
        h.assertTrue(energy.insertEnergy(1000,false)==1000,"Energy accepted");
        h.assertTrue(restored.energy==0,"Energy rollback");
        energy.insertEnergy(1000,true);
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
    @GameTest(maxTicks=400)
    public void boardListsInProgressQuestsInAutomaticAndMixedModes(GameTestHelper h)throws Exception{
        var path=fixture(h,"progress","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var level=h.getLevel();var actor=player(h);var origin=h.absolutePos(new BlockPos(0,1,4));
        for(int y=0;y<2;y++)for(int x=0;x<3;x++)level.setBlock(origin.offset(x,y,1),Blocks.STONE.defaultBlockState(),3);
        QuestUtilityBlock.assemble(level,origin,Direction.NORTH,0);
        var board=(QuestUtilityEntity)level.getBlockEntity(origin);board.claim(actor.getUUID());board.serverTick();
        QuestPlusMod.runtime().setObjectiveProgress(actor,"questplus:blocks_progress","items",2);
        for(String mode:List.of("automatic","mixed")){
            board.configure(actor,new QuestBlockConfig(ProgressScope.PERSONAL,List.of(),"","",mode,"players","completed","binary",1,true,63,false,false),null,0);
            h.assertTrue(!board.rows.isEmpty()&&"questplus:blocks_progress".equals(board.rows.getFirst().id()),mode+" board lists the in-progress quest first: "+board.rows);
            // Quests that can be started fill the rest, so a world with no progress yet still has a board.
            h.assertTrue(board.rows.size()>1&&board.rows.get(1).fraction()==0,mode+" board fills with startable quests: "+board.rows);
        }
        board.configure(actor,new QuestBlockConfig(ProgressScope.PERSONAL,List.of(),"","","leaderboard","players","completed","binary",1,true,63,false,false),null,0);
        h.assertTrue(board.boardLeaderboard()&&board.rows.equals(board.leaders),"Leaderboard mode shows the ranking");
        Files.delete(path);QuestPlusMod.runtime().reload();h.succeed();
    }
    @GameTest(maxTicks=2000)
    public void personalBoardFollowsOwnersSharedTeamProgress(GameTestHelper h)throws Exception{
        var path=fixture(h,"teamboard","{id:'items',event:'item_turn_in',target:'minecraft:stone',count:4}");
        var rt=QuestPlusMod.runtime();var level=h.getLevel();var actor=player(h);UUID id=actor.getUUID();
        var origin=h.absolutePos(new BlockPos(0,1,4));
        for(int y=0;y<2;y++)for(int x=0;x<3;x++)level.setBlock(origin.offset(x,y,1),Blocks.STONE.defaultBlockState(),3);
        Runnable idle=()->{if(rt.isBusy(id)){java.util.concurrent.locks.LockSupport.parkNanos(1000000);h.fail("Await durable team operation");}};
        h.startSequence().thenExecute(step(()->rt.changeTeam(m->m.create(id,"Board team"))))
                .thenWaitUntil(idle).thenExecute(step(()->{
                    // Progress now lands in the shared team record. The player's personal record stays empty.
                    rt.setObjectiveProgress(actor,"questplus:blocks_teamboard","items",2);
                    QuestUtilityBlock.assemble(level,origin,Direction.NORTH,0);
                    var board=(QuestUtilityEntity)level.getBlockEntity(origin);board.claim(id);board.serverTick();
                    board.configure(actor,new QuestBlockConfig(ProgressScope.PERSONAL,List.of(),"","","automatic","players","completed","binary",1,true,63,false,false),null,0);
                    h.assertTrue(board.rows.stream().filter(r->"questplus:blocks_teamboard".equals(r.id())&&r.fraction()==.5).count()==1,
                            "Personal board shows the progress the owner's tablet shows, once: "+board.rows);
                    Files.delete(path);rt.reload();
                })).thenSucceed();
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
