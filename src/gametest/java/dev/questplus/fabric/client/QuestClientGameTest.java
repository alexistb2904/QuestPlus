package dev.questplus.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;

/** Exercises real client initialization, packet transfer, rendering and authoritative mutations. */
public final class QuestClientGameTest implements FabricClientGameTest {
    public static net.minecraft.world.entity.player.PlayerSkin skin;
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            context.waitFor(client -> QuestPlusClient.STATE.ready,600);
            context.runOnClient(client -> { if (QuestPlusClient.STATE.quests.isEmpty()) throw new AssertionError("Empty initial catalogue"); });
            context.setScreen(QuestScreen::new);
            context.waitTicks(30);
            context.takeScreenshot("questplus-tablet-initial");
            context.runOnClient(client -> {
                client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F6,0,0));
                client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN,0,0));
            });
            context.waitTicks(3); context.takeScreenshot("questplus-details");
            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE,0,0)));
            for (int[] resolution:new int[][]{{1280,800},{1024,768},{1600,900}}) {
                context.getInput().resizeWindow(resolution[0],resolution[1]); context.waitTicks(5);
                context.runOnClient(client -> {
                    var screen=(QuestScreen)client.gui.screen();
                    screen.mouseScrolled(screen.width/2.0,screen.height/2.0,0,2);
                    screen.keyPressed(new KeyEvent(InputConstants.KEY_HOME,0,0));
                });
                context.takeScreenshot("questplus-"+resolution[0]+"x"+resolution[1]);
            }
            clickButton(context,"team"); context.waitTicks(3);
            context.runOnClient(client -> { var screen=(QuestScreen)client.gui.screen(); screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Client test team"); });
            clickButton(context,"create");
            context.waitFor(client -> QuestPlusClient.STATE.team!=null,300);
            context.takeScreenshot("questplus-team");
            clickButton(context,"settings"); context.waitTicks(3);
            context.takeScreenshot("questplus-settings");
            context.runOnClient(client -> client.gui.screen().onClose()); context.waitTicks(12);
            context.runOnClient(client -> { if (client.gui.screen()!=null) throw new AssertionError("Screen did not close"); });

            if (FabricLoader.getInstance().isModLoaded("fp_pal")
                && FabricLoader.getInstance().isModLoaded("player_animation_library")) {
                int oldFov=context.computeOnClient(client -> client.options.fov().get());
                int oldScale=context.computeOnClient(client -> client.options.guiScale().get());
                double oldInterfaceScale=context.computeOnClient(client -> QuestPlusClient.settings.scale);
                int cycle=0;
                for (int[] setup:new int[][]{{1280,800,45,2},{1024,768,70,3},{1600,900,100,1},{1280,720,30,2},{1600,900,85,4}}) {
                    cycle++;
                    int currentCycle=cycle;
                    context.runOnClient(client -> {
                        QuestPlusClient.settings.scale=1;
                        boolean slim=currentCycle%2==0;
                        skin=java.util.stream.IntStream.range(0,100).mapToObj(i -> net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0,i)))
                            .filter(s -> (s.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM)==slim).findFirst().orElseThrow();
                    });
                    context.getInput().resizeWindow(setup[0],setup[1]);
                    context.runOnClient(client -> { client.options.fov().set(setup[2]); client.options.guiScale().set(setup[3]); });
                    context.getInput().pressKey(InputConstants.KEY_J);
                    context.waitTicks(4);
                    context.takeScreenshot("questplus-native-"+cycle+"-pocket");
                    context.waitTicks(8);
                    context.takeScreenshot("questplus-native-"+cycle+"-opening");
                    context.waitTicks(8);
                    context.takeScreenshot("questplus-native-"+cycle+"-settling");
                    context.waitFor(client -> client.gui.screen() instanceof QuestScreen && TabletSurfaceRenderer.active(),120);
                    context.waitTicks(3);
                    context.takeScreenshot("questplus-native-"+cycle+"-idle");
                    context.runOnClient(client -> {
                        if (!TabletSurfaceRenderer.active()) throw new AssertionError("Native tablet UI did not attach at cycle "+currentCycle);
                        if (!(client.gui.screen() instanceof QuestScreen screen)
                            || TabletSurfaceRenderer.WIDTH!=500 || TabletSurfaceRenderer.HEIGHT!=300)
                            throw new AssertionError("Native UI surface dimensions changed");
                        boolean projected=false;
                        for (int y=client.getWindow().getGuiScaledHeight()/10;y<client.getWindow().getGuiScaledHeight();y+=client.getWindow().getGuiScaledHeight()/10)
                            for (int x=client.getWindow().getGuiScaledWidth()/10;x<client.getWindow().getGuiScaledWidth();x+=client.getWindow().getGuiScaledWidth()/10)
                                if (TabletFaceAnchor.pointer(x,y)!=null) projected=true;
                        if (!projected) throw new AssertionError("No mouse coordinate projected onto the screen bone at cycle "+currentCycle);
                        screen.keyPressed(new KeyEvent(InputConstants.KEY_F6,0,0));
                        screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN,0,0));
                    });
                    context.waitTicks(2);
                    context.takeScreenshot("questplus-native-"+cycle+"-quest-interaction");
                    clickFace(context,125,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"invite"))
                            throw new AssertionError("The projected Team tab click did not open Team");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-team");
                    clickFace(context,205,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"scale",Math.round(QuestPlusClient.settings.scale*100)))
                            throw new AssertionError("The projected Settings tab click did not open Settings");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-settings");
                    clickFace(context,370,100);
                    context.runOnClient(client -> {
                        if (QuestPlusClient.settings.scale!=1.25 || client.gui.screen().width!=TabletSurfaceRenderer.logicalWidth())
                            throw new AssertionError("Interface scale did not resize the logical canvas");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-scaled");
                    clickFace(context,45,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"filter.all"))
                            throw new AssertionError("The projected Quests tab click did not return to Quests");
                    });
                    context.getInput().pressKey(InputConstants.KEY_J);
                    context.waitTicks(12);
                    context.takeScreenshot("questplus-native-"+cycle+"-closing");
                    context.waitFor(client -> client.gui.screen()==null && !QuestClientTabletFlow.nativeHandsActive(),180);
                    context.waitTicks(2);
                    context.runOnClient(client -> {
                        if (client.gui.screen()!=null || QuestClientTabletFlow.nativeHandsActive())
                            throw new AssertionError("Tablet close did not restore the game view at cycle "+currentCycle);
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-closed");
                }
                context.getInput().resizeWindow(1280,800);
                skin=null;
                QuestPlusClient.settings.scale=oldInterfaceScale;
                context.getInput().pressKey(InputConstants.KEY_J);
                context.waitTicks(2);
                context.getInput().pressKey(InputConstants.KEY_J);
                context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive() && client.gui.screen()==null,180);
                context.takeScreenshot("questplus-native-interrupted-closed");
                context.runOnClient(client -> { client.options.fov().set(oldFov); client.options.guiScale().set(oldScale); });
            }
            world.getServer().runCommand("questplus reload");
            context.waitFor(client -> QuestPlusClient.STATE.generation>=2,300);
        }
        context.runOnClient(client -> { if (QuestPlusClient.STATE.ready || !QuestPlusClient.STATE.quests.isEmpty()) throw new AssertionError("Disconnected data retained"); });
    }

    private static void clickButton(ClientGameTestContext context,String key) {
        var point=context.computeOnClient(client -> {
            var screen=(QuestScreen)client.gui.screen();
            var button=screen.children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                .filter(b -> b.getMessage().getString().equals(QuestPlusClient.tr(key).getString())).findFirst().orElseThrow();
            return new TabletFaceAnchor.Point((button.getX()+button.getWidth()/2.0)*client.getWindow().getWidth()/client.getWindow().getGuiScaledWidth(),
                (button.getY()+button.getHeight()/2.0)*client.getWindow().getHeight()/client.getWindow().getGuiScaledHeight());
        });
        clickWindow(context,point);
    }
    private static void clickFace(ClientGameTestContext context,double logicalX,double logicalY) {
        var point=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(logicalX*TabletSurfaceRenderer.uiScale(),logicalY*TabletSurfaceRenderer.uiScale()));
        if (point==null) throw new AssertionError("Cannot project tablet UI coordinate to the Minecraft window");
        clickWindow(context,point);
    }
    private static void clickWindow(ClientGameTestContext context,TabletFaceAnchor.Point point) {
        context.getInput().setCursorPos(point.x(),point.y());
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    private static boolean hasButtonLabel(net.minecraft.client.Minecraft client,QuestScreen screen,String key,Object... args) {
        String expected=QuestPlusClient.tr(key,args).getString();
        return screen.children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
            .anyMatch(button -> button.getMessage().getString().startsWith(expected));
    }
}
