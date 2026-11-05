package dev.questplus.fabric;

import dev.questplus.core.*;
import dev.questplus.clientdata.ClientProjection;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;

public final class RewardIntegrationTest {
    @FunctionalInterface private interface Step { void run() throws Exception; }
    private static Runnable step(Step action){return ()->{try{action.run();}catch(RuntimeException e){throw e;}catch(Exception e){throw new RuntimeException(e);}};}
    private static ServerPlayer connect(GameTestHelper helper,GameProfile profile) {
        var server=helper.getLevel().getServer();
        var player=new ServerPlayer(server,helper.getLevel(),profile,ClientInformation.createDefault());
        var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection,player,CommonListenerCookie.createInitial(profile,false));
        player.setGameMode(GameType.SURVIVAL);return player;
    }
    private static Map.Entry<String,PlayerProgress.RewardGrant> pending(QuestRuntime runtime,UUID actor) throws Exception {
        var personal=runtime.player(actor);
        return personal.entitlements.entrySet().stream().filter(e->e.getValue().questId().equals("questplus:choice_integration")&&!personal.claims.containsKey(e.getKey())).findFirst().orElseThrow();
    }
    private static void reject(GameTestHelper helper,Step action,String message) throws Exception {
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){rejected=true;}
        helper.assertTrue(rejected,message);
    }
    @GameTest(maxTicks=2000)
    public void durablePersonalChoices(GameTestHelper helper) throws Exception {
        var runtime=QuestPlusMod.runtime();var server=helper.getLevel().getServer();
        var root=server.getWorldPath(LevelResource.ROOT);
        var fixture=root.resolve("questplus/quests/choice-integration.json5");
        Files.writeString(fixture,"""
            {schemaVersion:2,id:'questplus:choice_integration',category:'questplus:survival',icon:'minecraft:chest',
             title:{en_us:'Choice integration'},progression:'repeatable',
             objectives:[{id:'check',event:'manual',target:'questplus:choice_integration',count:1}],
             rewards:[
              {type:'questplus:choice',data:{options:[{type:'experience',amount:7},{type:'experience_levels',amount:2}]}},
              {type:'questplus:choice',data:{options:[{type:'experience',amount:11},{type:'experience_levels',amount:3}]}},
              {type:'questplus:random',data:{rolls:2,options:[{type:'experience',amount:1,weight:1},{type:'experience',amount:2,weight:1}]}}
             ]}
            """);
        runtime.reload();
        var a=connect(helper,new GameProfile(UUID.randomUUID(),"choice-a"));
        var profile=new GameProfile(UUID.randomUUID(),"choice-b");ServerPlayer[] b={connect(helper,profile)};
        UUID aid=a.getUUID(),bid=profile.id();UUID[] team={null};String[] token={null};int[] expected={0};
        Runnable idle=()->helper.assertTrue(!runtime.isBusy(aid)&&!runtime.isBusy(bid),"Await durable save");
        runtime.changeTeam(m->m.create(aid,"Choices"));
        helper.startSequence().thenWaitUntil(idle).thenExecute(step(()->{
            team[0]=runtime.player(aid).teamId;runtime.changeTeam(m->m.invite(aid,bid,System.currentTimeMillis()));
        })).thenWaitUntil(idle).thenExecute(step(()->runtime.changeTeam(m->m.join(bid,team[0],System.currentTimeMillis()))))
        .thenWaitUntil(idle).thenExecute(()->runtime.event(a,new QuestEvent("manual","questplus:choice_integration",Set.of(),1,QuestPlusMod.context(a))))
        .thenWaitUntil(idle).thenExecute(step(()->{
            var grant=pending(runtime,aid);token[0]=ClientProjection.rewardToken(grant.getKey());
            helper.assertTrue(grant.getValue().rewards().equals(pending(runtime,bid).getValue().rewards()),"Team shares the single resolved draw");
            expected[0]=18+grant.getValue().rewards().stream().filter(r->r.type().equals("experience")).mapToInt(QuestDefinition.Reward::amount).sum();
            reject(helper,()->runtime.claim(a,"questplus:choice_integration"),"Unresolved choices block claims");
            reject(helper,()->runtime.chooseReward(a,"questplus:choice_integration",UUID.randomUUID().toString(),0,0),"Forged token rejected");
            runtime.chooseReward(a,"questplus:choice_integration",token[0],0,0);
            reject(helper,()->runtime.chooseReward(a,"questplus:choice_integration",token[0],1,0),"Concurrent choice blocked");
        })).thenWaitUntil(idle).thenExecute(step(()->{
            reject(helper,()->runtime.chooseReward(a,"questplus:choice_integration",token[0],0,1),"Replay cannot consume another slot");
            helper.assertTrue(pending(runtime,aid).getValue().rewards().get(1).type().equals("questplus:choice"),"Second slot still unresolved");
            helper.assertTrue(pending(runtime,bid).getValue().rewards().getFirst().type().equals("questplus:choice"),"Teammate choice remains personal");
            runtime.chooseReward(a,"questplus:choice_integration",token[0],1,0);
            // Disconnect during the async choice write; reconnect must use the persisted result.
            server.getPlayerList().remove(a);runtime.disconnect(aid);
        })).thenWaitUntil(idle).thenExecute(step(()->{
            var saved=new com.google.gson.Gson().fromJson(Files.readString(root.resolve("data/questplus/players/"+aid+".json")),PlayerProgress.class);
            saved.validate();
            helper.assertTrue(saved.entitlements.equals(runtime.player(aid).entitlements),"Random outcomes and both choices persisted before claim");
            var reconnected=connect(helper,a.getGameProfile());b[0]=reconnected;
            runtime.claim(reconnected,"questplus:choice_integration");
            reject(helper,()->runtime.claim(reconnected,"questplus:choice_integration"),"Duplicate claim blocked");
        })).thenWaitUntil(idle).thenExecute(step(()->{
            helper.assertTrue(b[0].totalExperience==expected[0],"Exactly the saved XP rewards delivered once");
            reject(helper,()->runtime.claim(b[0],"questplus:choice_integration"),"Delivered receipt cannot replay");
            runtime.mutate(aid,"questplus:choice_integration","restart");
            runtime.event(b[0],new QuestEvent("manual","questplus:choice_integration",Set.of(),1,QuestPlusMod.context(b[0])));
        })).thenWaitUntil(idle).thenExecute(step(()->{
            reject(helper,()->runtime.chooseReward(b[0],"questplus:choice_integration",token[0],0,1),"Old cycle token cannot select the next cycle");
            helper.assertTrue(!ClientProjection.rewardToken(pending(runtime,aid).getKey()).equals(token[0]),"New cycle gets a distinct selection token");
            var personal=runtime.player(aid);
            personal.entitlements.entrySet().removeIf(e->!e.getValue().questId().equals("questplus:choice_integration"));
            personal.entitlements.put("0-unresolved",pending(runtime,aid).getValue());
            personal.entitlements.put("z-ready",new PlayerProgress.RewardGrant("questplus:choice_integration",List.of(new QuestDefinition.Reward("experience",null,0,5))));
            var projection=ClientProjection.progress(runtime.visibleQuests(aid),runtime.engine(),runtime.state(aid),personal).getAsJsonObject("questplus:choice_integration");
            helper.assertTrue(!projection.get("claimable").getAsBoolean() && projection.get("claimableAny").getAsBoolean(),"Collect all stays available alongside an unresolved cycle");
            runtime.claimAll(b[0]);
        })).thenWaitUntil(idle).thenExecute(step(()->{
            var personal=runtime.player(aid);
            helper.assertTrue(b[0].totalExperience==expected[0]+5,"Collect all delivers the exact ready grant once");
            helper.assertTrue(personal.entitlements.containsKey("0-unresolved"),"Collect all preserves unresolved choices");
            helper.assertTrue(personal.entitlements.entrySet().stream().filter(e->"delivered".equals(personal.claims.get(e.getKey()))).count()==1,"Only the latest delivered receipt is retained");
            helper.assertTrue(personal.claims.values().stream().filter("delivered"::equals).count()==2,"All delivered claim keys remain protected");
            Files.delete(fixture);runtime.reload();
        })).thenSucceed();
    }
}
