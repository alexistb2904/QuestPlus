package dev.questplus.fabric;

import dev.questplus.fabric.client.*;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraft.client.gui.components.EditBox;
import java.nio.file.Files;
import java.util.*;

/** Rendered native blocks, actual item placement, C2S settings and server completion. */
public final class QuestBlocksClientGameTest {
    public static void run(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()){
            var server=world.getServer();
            context.waitFor(client->QuestPlusClient.STATE.ready,600);
            context.getInput().resizeWindow(1280,800);
            context.runOnClient(client->{client.options.guiScale().set(2);QuestPlusClient.settings.tablet=false;});
            BlockPos[] pos=new BlockPos[3];
            server.runOnServer(instance->{try{
                var rt=QuestPlusMod.runtime();var p=instance.getPlayerList().getPlayers().getFirst();var level=p.level();
                String quest="questplus:blocks_client";
                Files.writeString(instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/blocks-client.json5"),"""
                        {schemaVersion:2,id:'questplus:blocks_client',category:'%s',icon:'minecraft:stone',
                        title:{en_us:'Supply the settlement'},progression:'once',objectives:[
                        {id:'stone',event:'item_turn_in',target:'minecraft:stone',count:8}],rewards:[]}
                        """.formatted(rt.categories().getFirst().id()));rt.reload();
                p.setGameMode(GameType.SURVIVAL);
                pos[0]=p.blockPosition().offset(-2,0,5);pos[1]=pos[0].offset(4,0,0);pos[2]=pos[0].offset(3,0,-2);
                for(int y=0;y<4;y++)for(int x=0;x<6;x++)level.setBlock(pos[0].offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState(),3);
                for(int x=0;x<7;x++)for(int z=-3;z<3;z++)level.setBlock(pos[0].offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(QuestWorldBlocks.BOARD));
                var wall=pos[0].south();var hit=new BlockHitResult(Vec3.atCenterOf(wall).add(0,0,-.5),Direction.NORTH,wall,false);
                p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
                if(!level.getBlockState(pos[0]).is(QuestWorldBlocks.BOARD))throw new AssertionError("Board item placement failed");
                var board=(QuestUtilityEntity)level.getBlockEntity(pos[0]);board.configure(p,QuestWorldBlocksGameTest.config(quest,"stone"),null,0);
                for(int i=1;i<3;i++){
                    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(i==1?QuestWorldBlocks.STATION:QuestWorldBlocks.RELAY));
                    var floor=pos[i].below();var floorHit=new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false);
                    p.gameMode.useItemOn(p,level,p.getMainHandItem(),InteractionHand.MAIN_HAND,floorHit);
                    var be=(QuestUtilityEntity)level.getBlockEntity(pos[i]);
                    if(be==null)throw new AssertionError("Utility item placement failed");
                    if(i==2)be.configure(p,QuestWorldBlocksGameTest.config(quest,"stone"),null,0);
                }
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                p.setYRot(0);p.setXRot(0);
            }catch(Exception error){throw new RuntimeException(error);}});
            context.waitFor(client->QuestPlusClient.STATE.quests.containsKey("questplus:blocks_client"),300);
            context.runOnClient(client->{client.player.setYRot(0);client.player.setXRot(0);client.gui.setScreen(null);});
            context.waitTicks(25);context.takeScreenshot("world-blocks-placed-and-board");
            server.runOnServer(instance->{var p=instance.getPlayerList().getPlayers().getFirst();
                var be=(QuestUtilityEntity)p.level().getBlockEntity(pos[1]);QuestBlockNetwork.open(p,be);
            });
            context.waitFor(client->client.gui.screen() instanceof QuestBlockScreen,200);
            click(context,"pick_quest",null);
            context.runOnClient(client->client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow().setValue("Supply the settlement"));
            context.takeScreenshot("world-blocks-quest-picker");
            clickLabel(context,"Supply the settlement");
            click(context,"pick_objective",null);clickLabel(context,"stone");
            context.takeScreenshot("world-blocks-station-configuration");
            click(context,"automation",ComponentKey.YES_NO_FALSE);
            context.takeScreenshot("world-blocks-station-automation-on");
            click(context,"save",null);context.waitTicks(15);context.takeScreenshot("world-blocks-station-applied");
            server.runOnServer(instance->{
                var p=instance.getPlayerList().getPlayers().getFirst();var be=(QuestUtilityEntity)p.level().getBlockEntity(pos[1]);
                if(!be.config.automation()||!be.config.quests().contains("questplus:blocks_client"))throw new AssertionError("C2S configuration did not apply: "+be.config.encode());
                try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){
                    var storage=net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(p.level(),pos[1],Direction.UP);
                    if(storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.STONE),8,tx)!=8)throw new AssertionError("Submission refused");tx.commit();
                }
            });
            context.waitFor(client->QuestClientState.flag(QuestPlusClient.STATE.progress("questplus:blocks_client"),"completed"),300);
            context.takeScreenshot("world-blocks-station-completed");
            context.runOnClient(client->{client.options.guiScale().set(4);});context.waitTicks(5);
            context.runOnClient(client->client.gui.screen().mouseScrolled(100,100,0,-10));
            context.takeScreenshot("world-blocks-small-screen-settings");
            click(context,"close",null);context.waitTicks(20);
            context.waitFor(client->client.gui.screen()==null,100);
            context.takeScreenshot("world-blocks-completed-board-relay");
            server.runOnServer(instance->{
                var relay=(QuestUtilityEntity)instance.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos[2]);
                if(relay.signal!=15)throw new AssertionError("Event-driven relay did not update");
                var board=(QuestUtilityEntity)relay.getLevel().getBlockEntity(pos[0]);
                if(board.rows.stream().noneMatch(row->row.progress().equals("100%")))throw new AssertionError("Board did not update");
            });
        }
    }
    private enum ComponentKey {YES_NO_FALSE}
    private static void click(ClientGameTestContext context,String key,ComponentKey arg){
        String label=net.minecraft.network.chat.Component.translatable("questplus.blocks."+key,
                arg==null?new Object[0]:new Object[]{net.minecraft.network.chat.Component.translatable("questplus.blocks.no")}).getString();
        clickLabel(context,label);
    }
    private static void clickLabel(ClientGameTestContext context,String label){
        context.runOnClient(client->{
            if(client.gui.screen().getFocused()!=null)client.gui.screen().getFocused().setFocused(false);
        });
        var coords=context.computeOnClient(client->{
            var b=client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .filter(button->button.getMessage().getString().equals(label)).findFirst().orElseThrow();
            return new double[]{(b.getX()+b.getWidth()/2.0)*client.getWindow().getWidth()/client.getWindow().getGuiScaledWidth(),
                    (b.getY()+b.getHeight()/2.0)*client.getWindow().getHeight()/client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(coords[0],coords[1]);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);context.waitTicks(5);
    }
}
