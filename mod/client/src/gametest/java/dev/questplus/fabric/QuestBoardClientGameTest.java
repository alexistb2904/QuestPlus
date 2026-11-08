package dev.questplus.fabric;

import dev.questplus.fabric.client.*;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import java.nio.file.Files;
import java.util.*;

/** Real module placement, board UI round trips and scaled native rendering. */
public final class QuestBoardClientGameTest {
    public static void run(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            var server=world.getServer();BlockPos[] origin={null};
            context.waitFor(c->QuestPlusClient.STATE.ready,600);
            context.getInput().resizeWindow(1280,800);
            context.runOnClient(c->{c.options.guiScale().set(2);QuestPlusClient.settings.tablet=false;});
            server.runOnServer(instance->{try{
                var rt=QuestPlusMod.runtime();var p=instance.getPlayerList().getPlayers().getFirst();var level=p.level();
                var folder=instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests");
                String[] names={"A New Beginning","Mine & Gather","Alchemists Laboratory with an exceptionally long quest title"};
                String[] icons={"book","iron_pickaxe","grass_block"};
                for(int i=0;i<3;i++)Files.writeString(folder.resolve("board-client-"+i+".json5"),"""
                        {schemaVersion:2,id:'questplus:board_client_%d',category:'%s',icon:'minecraft:%s',title:{en_us:'%s'},
                        progression:'once',objectives:[{id:'items',event:'item_turn_in',target:'minecraft:stone',count:%d}],rewards:[]}
                        """.formatted(i,rt.categories().getFirst().id(),icons[i],names[i],new int[]{5,64,10}[i]));
                rt.reload();p.setGameMode(GameType.SURVIVAL);
                origin[0]=p.blockPosition().offset(-2,0,6);var o=origin[0];
                p.connection.teleport(o.getX()+2,o.getY(),o.getZ()-3.5,0,0);p.setYRot(0);p.setXRot(0);
                for(int y=0;y<2;y++)for(int x=0;x<4;x++){
                    var pos=o.offset(x,y,0);var wall=pos.south();level.setBlock(wall,Blocks.STONE_BRICKS.defaultBlockState(),3);
                    var stack=new ItemStack(QuestWorldBlocks.BOARD,2);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                    var hit=new BlockHitResult(Vec3.atCenterOf(wall).add(0,0,-.5),Direction.NORTH,wall,false);
                    p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,hit);
                    if(!level.getBlockState(pos).is(QuestWorldBlocks.BOARD)||stack.getCount()!=1)throw new AssertionError("Module placement/consumption failed");
                }
                var board=(QuestUtilityEntity)level.getBlockEntity(o);
                board.configure(p,new QuestBlockConfig(dev.questplus.team.ProgressScope.PERSONAL,
                        List.of("questplus:board_client_0","questplus:board_client_1","questplus:board_client_2"),"","","mixed","players","completed","binary",1,true,63,false,false),null,0);
                rt.setObjectiveProgress(p,"questplus:board_client_0","items",3);rt.setObjectiveProgress(p,"questplus:board_client_1","items",24);
                for(int i=0;i<3;i++)rt.player(UUID.nameUUIDFromBytes(("board-leader-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8))).lastKnownName=
                        i==0?"A very long adventurer name that must stay in its row":"Adventurer"+(i+1);
                board.refresh();
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            }catch(Exception error){throw new RuntimeException(error);}});
            context.waitFor(c->QuestPlusClient.STATE.quests.containsKey("questplus:board_client_2"),300);
            context.waitTicks(30);context.takeScreenshot("board-modular-world-4x2");
            server.runOnServer(s->{try{s.getCommands().getDispatcher().execute("time set midnight",s.createCommandSourceStack());}
                catch(Exception error){throw new RuntimeException(error);}});
            context.waitTicks(10);context.takeScreenshot("board-readable-at-night");
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();QuestBlockNetwork.open(p,(QuestUtilityEntity)p.level().getBlockEntity(origin[0]));});
            context.waitFor(c->c.gui.screen() instanceof QuestBoardScreen,200);
            context.takeScreenshot("board-configuration-desktop");
            click(context,Component.translatable("questplus.board.owner","Player0").getString());
            context.runOnClient(c->{if(c.gui.screen().children().stream().noneMatch(net.minecraft.client.gui.components.EditBox.class::isInstance))throw new AssertionError("Offline UUID input missing");});
            click(context,Component.translatable("questplus.board.configuration").getString());
            click(context,"↓");context.takeScreenshot("board-reorder-draft");
            click(context,Component.translatable("questplus.blocks.save").getString());context.waitTicks(30);context.takeScreenshot("board-reorder-applied");
            server.runOnServer(s->{var be=(QuestUtilityEntity)s.getPlayerList().getPlayers().getFirst().level().getBlockEntity(origin[0]);
                if(!be.config.quests().getFirst().equals("questplus:board_client_1"))throw new AssertionError("Pinned order did not reach server");
                var neighbor=(QuestUtilityEntity)be.getLevel().getBlockEntity(origin[0].east());
                if(!neighbor.config.equals(be.config))throw new AssertionError("Connected settings not propagated");
            });
            click(context,Component.translatable("questplus.board.leaderboard").getString());context.takeScreenshot("board-leaderboard-desktop");
            click(context,Component.translatable("questplus.board.configuration").getString());
            context.runOnClient(c->c.options.guiScale().set(4));context.waitTicks(5);
            context.takeScreenshot("board-configuration-small-top");
            context.runOnClient(c->c.gui.screen().mouseScrolled(20,100,0,-30));context.waitTicks(2);
            context.takeScreenshot("board-configuration-small-bottom");
            click(context,Component.translatable("questplus.blocks.close").getString());
            context.waitFor(c->c.gui.screen()==null,100);
            context.runOnClient(c->c.options.guiScale().set(2));context.waitTicks(5);
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();QuestBlockNetwork.open(p,(QuestUtilityEntity)p.level().getBlockEntity(origin[0]));});
            context.waitFor(c->c.gui.screen() instanceof QuestBoardScreen,200);
            context.takeScreenshot("board-reopened-saved-settings");
            click(context,Component.translatable("questplus.board.add").getString());
            server.runOnServer(s->{try{QuestPlusMod.runtime().setObjectiveProgress(s.getPlayerList().getPlayers().getFirst(),"questplus:board_client_0","items",4);}catch(Exception e){throw new RuntimeException(e);}});
            context.waitTicks(25);
            context.runOnClient(c->{if(!c.gui.screen().getClass().getSimpleName().equals("QuestBlockPicker"))throw new AssertionError("Live snapshot closed the picker");c.gui.screen().onClose();});
            click(context,Component.translatable("questplus.blocks.close").getString());
            itemPresentation(context,server);
        }
    }
    private static void itemPresentation(ClientGameTestContext context,net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server){
        server.runCommand("time set day");
        server.runOnServer(s->{
            var p=s.getPlayerList().getPlayers().getFirst();var pos=p.blockPosition();p.setGameMode(GameType.CREATIVE);
            for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)p.level().setBlock(pos.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            p.connection.teleport(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(QuestWorldBlocks.BOARD));
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        });
        context.runOnClient(c->{c.options.fov().set(55);c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);});
        context.getInput().lookAt(0,0);context.waitTicks(8);context.takeScreenshot("board-held-mainhand-first-person");
        context.runOnClient(c->c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.getInput().lookAt(0,0);context.waitTicks(12);context.takeScreenshot("board-held-mainhand-third-person-front");
        context.runOnClient(c->c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        context.waitTicks(5);context.takeScreenshot("board-held-mainhand-third-person-back");
        server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(QuestWorldBlocks.BOARD));});
        context.runOnClient(c->c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(8);context.takeScreenshot("board-held-offhand-third-person-front");
        context.runOnClient(c->c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        context.waitTicks(5);context.takeScreenshot("board-held-offhand-first-person");
        server.runOnServer(s->{
            var p=s.getPlayerList().getPlayers().getFirst();p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            var board=new net.minecraft.world.entity.item.ItemEntity(p.level(),p.getX(),p.getY()+.2,p.getZ()+2,new ItemStack(QuestWorldBlocks.BOARD));
            board.setPickUpDelay(32767);board.setDeltaMovement(Vec3.ZERO);p.level().addFreshEntity(board);
            var stone=new net.minecraft.world.entity.item.ItemEntity(p.level(),p.getX()+.8,p.getY()+.2,p.getZ()+2,Items.STONE.getDefaultInstance());
            stone.setPickUpDelay(32767);stone.setDeltaMovement(Vec3.ZERO);p.level().addFreshEntity(stone);
        });
        context.runOnClient(c->c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));context.getInput().lookAt(0,30);
        context.waitTicks(25);context.takeScreenshot("board-dropped-next-to-vanilla-stone");
    }
    private static void click(ClientGameTestContext context,String label){
        context.runOnClient(c->{if(c.gui.screen().getFocused()!=null)c.gui.screen().getFocused().setFocused(false);});
        var xy=context.computeOnClient(c->{var b=c.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                .filter(button->button.active&&button.getMessage().getString().equals(label)).findFirst().orElseThrow();
            return new double[]{(b.getX()+b.getWidth()/2.0)*c.getWindow().getWidth()/c.getWindow().getGuiScaledWidth(),
                    (b.getY()+b.getHeight()/2.0)*c.getWindow().getHeight()/c.getWindow().getGuiScaledHeight()};});
        context.getInput().setCursorPos(xy[0],xy[1]);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);context.waitTicks(2);
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);context.waitTicks(5);
    }
}
