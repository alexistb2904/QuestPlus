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
                    context.runOnClient(client -> {
                        var controller=((com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer)client.player).fpPal$getFpAnimationController();
                        double ticks=controller.getAnimationTicks();
                        FirstPersonPalBridge.prepareFrame(.1f);
                        var arm=new org.joml.Vector3f(com.github.razorplay01.fp_pal.firstperson.FpRenderState.fpRightArm.rotation);
                        var tablet=new org.joml.Vector3f(com.github.razorplay01.fp_pal.firstperson.FpRenderState.getExtraBone("tablet_root").position);
                        FirstPersonPalBridge.prepareFrame(.9f);
                        if (arm.distanceSquared(com.github.razorplay01.fp_pal.firstperson.FpRenderState.fpRightArm.rotation)<1e-9
                            || tablet.distanceSquared(com.github.razorplay01.fp_pal.firstperson.FpRenderState.getExtraBone("tablet_root").position)<1e-9)
                            throw new AssertionError("The arm and tablet did not move between sub-tick render samples");
                        if (controller.getAnimationTicks()!=ticks) throw new AssertionError("Rendering advanced the PAL animation clock");
                    });
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
                    if (cycle==1) {
                        var p=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(460,220));
                        context.getInput().setCursorPos(p.x(),p.y()); context.getInput().scroll(-5);
                        context.waitTicks(3); context.takeScreenshot("questplus-native-rewards");
                        categoryScrollTrial(context);
                        context.waitTicks(3);
                        context.runOnClient(client -> {
                            client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_F6,0,0));
                            client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_RETURN,0,0));
                        });
                    }
                    clickFace(context,125,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"invite"))
                            throw new AssertionError("The projected Team tab click did not open Team");
                    });
                    if (cycle==1) {
                        clickFace(context,200,78);
                        context.getInput().typeChars("00000000-0000-0000-0000-000000000001");
                        context.runOnClient(client -> {
                            var box=((QuestScreen)client.gui.screen()).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                            if (!box.getValue().equals("00000000-0000-0000-0000-000000000001")) throw new AssertionError("Projected text input lost keyboard focus");
                        });
                        context.takeScreenshot("questplus-native-centered-input");
                    }
                    context.takeScreenshot("questplus-native-"+cycle+"-team");
                    clickFace(context,205,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"scale",Math.round(QuestPlusClient.settings.scale*100)))
                            throw new AssertionError("The projected Settings tab click did not open Settings");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-settings");
                    if (cycle==1) {
                        context.runOnClient(client -> QuestPlusClient.settings.blur=false);
                        context.waitTicks(3); context.takeScreenshot("questplus-native-blur-off");
                        context.runOnClient(client -> QuestPlusClient.settings.blur=true);
                        context.waitTicks(3); context.takeScreenshot("questplus-native-blur-on");
                    }
                    clickFace(context,370,100);
                    context.runOnClient(client -> {
                        if (QuestPlusClient.settings.scale!=1.25 || client.gui.screen().width!=TabletSurfaceRenderer.logicalWidth())
                            throw new AssertionError("Interface scale did not resize the logical canvas");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-scaled");
                    clickFace(context,45,13);
                    context.runOnClient(client -> {
                        if (!hasButtonLabel(client,(QuestScreen)client.gui.screen(),"close_details"))
                            throw new AssertionError("The projected Quests tab click did not return to Quests");
                    });
                    context.takeScreenshot("questplus-native-"+cycle+"-scaled-details");
                    clickFace(context,300,59);
                    context.runOnClient(client -> {
                        if (hasButtonLabel(client,(QuestScreen)client.gui.screen(),"close_details")
                            || !hasButtonLabel(client,(QuestScreen)client.gui.screen(),"filter.all"))
                            throw new AssertionError("The detail close button collided with diagram controls at 125% scale");
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
                    if (cycle==1) for (int frame=0;frame<4;frame++) {
                        context.waitTick(); context.takeScreenshot("questplus-native-close-tail-"+frame);
                    }
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
                remotePoseTrial(context,world.getServer());
            }
            world.getServer().runCommand("questplus reload");
            context.waitFor(client -> QuestPlusClient.STATE.generation>=2,300);
            if (FabricLoader.getInstance().isModLoaded("fp_pal")) {
                context.getInput().pressKey(InputConstants.KEY_J);
                context.waitFor(client -> TabletSurfaceRenderer.active(),120);
                context.waitTicks(3);
            }
        }
        context.runOnClient(client -> {
            if (QuestPlusClient.STATE.ready || !QuestPlusClient.STATE.quests.isEmpty()) throw new AssertionError("Disconnected data retained");
            if (QuestClientTabletFlow.nativeHandsActive()) throw new AssertionError("Disconnected tablet pose retained");
            if (FabricLoader.getInstance().isModLoaded("fp_pal") && TabletFrameInterpolation.settled(net.minecraft.resources.Identifier.parse("questplus:questplus.idle"),0))
                throw new AssertionError("Disconnected interpolation snapshots retained");
        });
    }

    private static void categoryScrollTrial(ClientGameTestContext context) {
        var original=context.computeOnClient(client -> new java.util.LinkedHashMap<>(QuestPlusClient.STATE.categories));
        try {
            context.runOnClient(client -> {
                for (int i=0;i<20;i++) {
                    var category=original.values().iterator().next().deepCopy();
                    category.addProperty("id","questplus:test_category_"+i);
                    QuestPlusClient.STATE.categories.put(category.get("id").getAsString(),category);
                }
                QuestPlusClient.STATE.catalogueRevision++;
            });
            context.waitTicks(3);
            var point=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(17,170));
            context.getInput().setCursorPos(point.x(),point.y());
            for (int i=0;i<7;i++) { context.getInput().scroll(-1); context.waitTick(); }
            context.runOnClient(client -> {
                try {
                    var field=QuestScreen.class.getDeclaredField("categoryScroll"); field.setAccessible(true);
                    if (field.getInt(client.gui.screen())!=7) throw new AssertionError("Category rail did not scroll through projected mouse input");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            context.takeScreenshot("questplus-native-categories-scrolled");
            clickFace(context,17,61);
            context.runOnClient(client -> {
                try {
                    var field=QuestScreen.class.getDeclaredField("category"); field.setAccessible(true);
                    var expected=new java.util.ArrayList<>(QuestPlusClient.STATE.categories.keySet()).get(7);
                    if (!expected.equals(field.get(client.gui.screen()))) throw new AssertionError("Scrolled category hitbox does not match its visible icon");
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
        } finally {
            context.runOnClient(client -> { QuestPlusClient.STATE.categories.clear(); QuestPlusClient.STATE.categories.putAll(original); QuestPlusClient.STATE.catalogueRevision++; });
        }
    }

    /** Real rendering and pose packets, using two client-side remote avatars rather than two connections. */
    private static void remotePoseTrial(ClientGameTestContext context,net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        var holderId=new java.util.UUID(0,101);
        var witnessId=new java.util.UUID(0,102);
        context.runOnClient(client -> {
            client.options.fov().set(70); client.options.guiScale().set(2);
            skin=net.minecraft.client.resources.DefaultPlayerSkin.get(new java.util.UUID(0,0));
            int id=9000;
            for (var profile:java.util.List.of(new com.mojang.authlib.GameProfile(holderId,"Tablet holder"),new com.mojang.authlib.GameProfile(witnessId,"Witness"))) {
                var player=new net.minecraft.client.player.RemotePlayer(client.level,profile);
                player.setId(id++); player.setPos(client.player.getX()+(profile.id().equals(holderId) ? -.85 : .85),client.player.getY(),client.player.getZ()+3.2);
                player.setYRot(180); player.setXRot(0); player.yHeadRot=180; player.yBodyRot=180; player.setOldPosAndRot();
                client.level.addEntity(player);
            }
        });
        context.getInput().lookAt(0,0); context.waitTicks(5);
        sendPose(server,holderId,true); context.waitTicks(12); context.takeScreenshot("questplus-remote-opening");
        context.waitTicks(18); context.takeScreenshot("questplus-remote-idle");
        assertRemoteArms(context);
        var side=context.computeOnClient(client -> client.player.position().add(3,0,3.2));
        context.runOnClient(client -> { client.player.setPos(side); client.player.setOldPosAndRot(); });
        context.getInput().lookAt(90,0); context.waitTicks(3); context.takeScreenshot("questplus-remote-side");
        context.runOnClient(client -> { client.player.setPos(side.add(-3,0,-3.2)); client.player.setOldPosAndRot(); });
        context.getInput().lookAt(0,0);
        context.getInput().pressKey(InputConstants.KEY_J);
        context.waitFor(client -> TabletSurfaceRenderer.active(),120);
        context.waitTicks(3); context.takeScreenshot("questplus-local-and-remote-holding");
        context.getInput().pressKey(InputConstants.KEY_J);
        context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(),180);
        assertRemoteArms(context);
        sendPose(server,holderId,false); context.waitTicks(12); context.takeScreenshot("questplus-remote-closing");
        context.waitTicks(18); context.takeScreenshot("questplus-remote-closed");
        context.runOnClient(client -> {
            client.level.removeEntity(9000,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            client.level.removeEntity(9001,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED); skin=null;
        });
    }
    private static void assertRemoteArms(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var holder=(net.minecraft.client.player.RemotePlayer)client.level.getEntity(9000);
            var witness=(net.minecraft.client.player.RemotePlayer)client.level.getEntity(9001);
            @SuppressWarnings("unchecked") var renderer=(net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.RemotePlayer>)(Object)client.getEntityRenderDispatcher().getRenderer(holder);
            var state=renderer.createRenderState(); renderer.extractRenderState(holder,state,1);
            var model=renderer.getModel(); model.setupAnim(state);
            if (Math.abs(model.rightArm.xRot-Math.toRadians(-53))>.05) throw new AssertionError("Remote holder does not match the exported Blockbench arm");
            renderer.extractRenderState(witness,state,1); model.setupAnim(state);
            if (Math.abs(model.rightArm.xRot)>.2 || Math.abs(model.leftArm.xRot)>.2
                || model.rightSleeve.xRot!=0 || model.leftSleeve.xRot!=0)
                throw new AssertionError("Tablet pose leaked into the witness or sleeve child transforms");
        });
    }
    private static void sendPose(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,java.util.UUID id,boolean open) {
        server.runOnServer(instance -> instance.getPlayerList().getPlayers().forEach(player ->
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new dev.questplus.fabric.TabletPoseNetwork.State(id,open))));
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
