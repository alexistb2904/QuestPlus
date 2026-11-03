package dev.questplus.fabric;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;

public final class TeamIntegrationTest {
    @FunctionalInterface private interface Step { void run() throws Exception; }
    private static Runnable step(Step action) { return () -> { try { action.run(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); } }; }
    private static ServerPlayer player(GameTestHelper helper) {
        var player=(ServerPlayer)helper.makeMockServerPlayer(GameType.SURVIVAL);
        var connection=new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,CommonListenerCookie.createInitial(player.getGameProfile(),false));
        player.setGameMode(GameType.SURVIVAL); return player;
    }
    @GameTest(maxTicks=10000)
    public void retroactiveTeams(GameTestHelper helper) throws Exception {
        var runtime=QuestPlusMod.runtime(); var server=helper.getLevel().getServer(); var dispatcher=server.getCommands().getDispatcher();
        var a=player(helper); var b=player(helper); UUID aid=a.getUUID(),bid=b.getUUID(); UUID[] team=new UUID[1]; int[] xp=new int[1];
        var fixture=server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/team-integration.json");
        Files.writeString(fixture,"""
          {"schemaVersion":2,"id":"questplus:team_integration","category":"questplus:survival","icon":"minecraft:dirt",
           "title":{"en_us":"Team fixture"},"description":{"en_us":"Test"},"progression":"once",
           "requires":{"and":[{"quest":"questplus:survival/logs"},{"quest":"questplus:combat/skeletons"}]},
           "objectives":[{"id":"dirt","event":"block_break","target":"minecraft:dirt","count":2}],"rewards":[{"type":"experience","amount":7}]}
          """);
        runtime.reload();
        runtime.mutate(aid,"questplus:survival/logs","grant"); runtime.mutate(bid,"questplus:combat/skeletons","grant");
        Runnable idle=() -> helper.assertTrue(!runtime.isBusy(aid) && !runtime.isBusy(bid),"Await durable team operation");
        var sequence=helper.startSequence();
        sequence.thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(dispatcher.execute("questplus team create Integration",a.createCommandSourceStack())==1,"Create team command");
        })).thenWaitUntil(step(() -> {
            idle.run(); helper.assertTrue(runtime.player(aid).teamId!=null,"Team ID persisted"); team[0]=runtime.player(aid).teamId;
        })).thenExecute(step(() -> {
            helper.assertTrue(dispatcher.execute("questplus team invite "+bid,a.createCommandSourceStack())==1,"Owner invitation");
        })).thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(dispatcher.execute("questplus team join "+team[0],b.createCommandSourceStack())==1,"Invitation accepted");
        })).thenWaitUntil(step(() -> {
            idle.run(); helper.assertTrue(team[0].equals(runtime.player(bid).teamId),"Membership committed");
        })).thenExecute(step(() -> {
            var shared=runtime.state(aid);
            helper.assertTrue(shared==runtime.state(bid),"Team is sole progress authority");
            helper.assertTrue(shared.quests.get("questplus:survival/logs").completed && shared.quests.get("questplus:combat/skeletons").completed,"Historical completion union");
            helper.assertTrue(!runtime.player(bid).entitlements.containsKey("questplus:survival/logs") && !runtime.player(aid).entitlements.containsKey("questplus:combat/skeletons"),"Merge grants no historical reward");
            helper.assertTrue(dispatcher.execute("questplus claim questplus:survival/logs",b.createCommandSourceStack())==0,"Imported completion cannot be claimed");
            helper.assertTrue(dispatcher.execute("questplus team kick "+aid,b.createCommandSourceStack())==0,"Member cannot kick owner");
            server.getPlayerList().remove(b); runtime.disconnect(bid);
            BlockPos pos=helper.absolutePos(new BlockPos(2,2,2));
            for (int i=0;i<2;i++) { helper.getLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState()); a.gameMode.destroyBlock(pos); }
        })).thenWaitUntil(step(() -> {
            idle.run(); helper.assertTrue(runtime.player(bid).entitlements.containsKey("questplus:team_integration"),"Offline member entitled to new collective completion");
        })).thenExecute(step(() -> {
            helper.assertTrue(runtime.state(bid).quests.get("questplus:team_integration").completed,"Offline member sees shared completion");
            xp[0]=a.totalExperience;
            helper.assertTrue(dispatcher.execute("questplus claim questplus:team_integration",a.createCommandSourceStack())==1,"Collective quest personal claim");
        })).thenWaitUntil(step(() -> {
            idle.run(); helper.assertTrue("delivered".equals(runtime.player(aid).claims.get("questplus:team_integration")),"Claim reserved then delivered");
            helper.assertTrue(a.totalExperience==xp[0]+7,"Collective reward once");
        })).thenExecute(step(() -> runtime.changeTeam(m -> m.leave(bid))))
        .thenWaitUntil(step(() -> { idle.run(); helper.assertTrue(runtime.player(bid).teamId==null,"Offline member separation"); }))
        .thenExecute(step(() -> {
            helper.assertTrue(runtime.player(bid).quests.get("questplus:team_integration").completed,"Leave keeps snapshot");
            runtime.mutate(bid,"questplus:mining/stone","grant");
        })).thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(!runtime.state(aid).quests.containsKey("questplus:mining/stone"),"Departed member stops sharing progress");
            runtime.changeTeam(m -> m.create(bid,"Other"));
        })).thenWaitUntil(idle).thenExecute(step(() -> {
            boolean denied=false; try { runtime.changeTeam(m -> m.join(bid,team[0],System.currentTimeMillis())); } catch (IllegalArgumentException expected) { denied=true; }
            helper.assertTrue(denied,"Player cannot join two teams"); runtime.changeTeam(m -> m.disband(bid));
        })).thenWaitUntil(idle).thenExecute(step(() -> runtime.changeTeam(m -> m.invite(aid,bid,System.currentTimeMillis()))))
        .thenWaitUntil(idle).thenExecute(step(() -> runtime.changeTeam(m -> m.join(bid,team[0],System.currentTimeMillis()))))
        .thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(runtime.state(aid).quests.get("questplus:mining/stone").completed,"Rejoin retroactively imports independent completion");
            helper.assertTrue(!runtime.player(aid).entitlements.containsKey("questplus:mining/stone"),"Rejoin has no retroactive reward");
            helper.assertTrue("delivered".equals(runtime.player(aid).claims.get("questplus:team_integration")),"Rejoin retains beneficiary ledger");
            runtime.changeTeam(m -> m.transfer(aid,bid));
        })).thenWaitUntil(idle).thenExecute(step(() -> runtime.changeTeam(m -> m.kick(bid,aid))))
        .thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(runtime.player(aid).teamId==null && runtime.player(aid).quests.get("questplus:team_integration").completed,"Kick keeps snapshot");
            runtime.changeTeam(m -> m.disband(bid));
        })).thenWaitUntil(idle).thenExecute(step(() -> {
            helper.assertTrue(runtime.player(bid).teamId==null,"Dissolution separates offline owner");
            Files.writeString(Path.of(System.getProperty("questplus.integration.teams")),new Gson().toJson(Map.of(
                "root",server.getWorldPath(LevelResource.ROOT).resolve("data/questplus").toAbsolutePath().toString(),"a",aid.toString(),"b",bid.toString(),"team",team[0].toString())));
        })).thenSucceed();
    }
}
