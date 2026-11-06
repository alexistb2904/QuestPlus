package dev.questplus.fabric.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class TabletCarryGameTest {
    static void run(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            context.waitFor(client -> QuestPlusClient.STATE.ready,600);
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                QuestPlusClient.settings.animations=true;QuestPlusClient.settings.tablet=true;
                QuestPlusClient.settings.reducedMotion=false;QuestPlusClient.settings.blur=false;
                client.options.fov().set(70);client.options.guiScale().set(2);
            });
            context.getInput().resizeWindow(1280,720);
            world.getServer().runCommand("gamemode creative @p");
            context.waitTicks(5);
            context.runOnClient(client -> {
                var screen=new CreativeModeInventoryScreen(client.player,client.level.enabledFeatures(),true);
                client.gui.setScreen(screen);
                var tab=BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse("questplus:quest"));
                if (tab==null || !((FabricCreativeModeInventoryScreen)screen).setSelectedTab(tab)
                    || !tab.contains(BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:tablet")).getDefaultInstance())
                    || !tab.contains(BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:loot_crate")).getDefaultInstance()))
                    throw new AssertionError("Quest creative tab is missing its items");
            });
            context.waitTicks(4);context.takeScreenshot("questplus-creative-quest");
            context.runOnClient(client -> client.gui.setScreen(null));
            world.getServer().runCommand("item replace entity @p weapon.mainhand with questplus:tablet");
            context.waitTicks(5);
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(5);context.takeScreenshot("questplus-local-carried-third-person");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
            firstPersonItemTrial(context);
            try (var files=java.nio.file.Files.list(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("shaderpacks"))) {
                var bliss=files.map(path -> path.getFileName().toString()).filter(name -> name.contains("bliss-shader")).findFirst().orElseThrow();
                context.runOnClient(client -> TabletCompatibilityTest.shader(bliss));
                context.waitTicks(30);
                context.runOnClient(client -> { if (!TabletShaderCompat.shadersActive()) throw new AssertionError("Bliss did not activate"); });
                firstPersonItemTrial(context);
                context.runOnClient(client -> TabletCompatibilityTest.shader(""));
                context.waitTicks(5);
            } catch (java.io.IOException error) { throw new AssertionError(error); }
            world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
            context.waitTicks(5);
            QuestClientGameTest.remotePoseTrial(context,world.getServer());
            dev.questplus.fabric.QuestPlusMod.LOGGER.info("TABLET THIRD PERSON CARRY AND CREATIVE TAB PASS");
        }
    }
    private static void firstPersonItemTrial(ClientGameTestContext context) {
        context.getInput().lookAt(0,-45);
        context.waitFor(client -> QuestClientTabletFlow.carrying() && TabletNativeArms.active()
            && com.github.razorplay01.fp_pal.firstperson.FpRenderState.getExtraBone("tablet_root")!=null,120);
        context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("",false)));
        context.waitTicks(5);
        context.runOnClient(client -> {
            if (!QuestClientTabletFlow.carrying() || !TabletNativeArms.active())
                throw new AssertionError("Chat interrupted the tablet carry pose");
            client.gui.setScreen(null);
        });
        context.waitTicks(2);
        var before=context.computeOnClient(client -> new org.joml.Vector3f(
            com.github.razorplay01.fp_pal.firstperson.FpRenderState.getExtraBone("tablet_root").position));
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client -> QuestClientTabletFlow.striking(),30);
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (before.distanceSquared(com.github.razorplay01.fp_pal.firstperson.FpRenderState.getExtraBone("tablet_root").position)<.1)
                throw new AssertionError("Left click did not move the custom tablet");
        });
        context.takeScreenshot("questplus-tablet-attack");
        context.waitFor(client -> QuestClientTabletFlow.carrying(),60);
        for (int cycle=0;cycle<2;cycle++) {
            context.runOnClient(QuestClientTabletFlow::toggle);
            context.waitFor(client -> TabletSurfaceRenderer.active(),120);
            context.runOnClient(client -> client.gui.screen().onClose());
            for (int tick=0;tick<25;tick++) {
                context.waitTicks(1);
                context.runOnClient(client -> {
                    if (!QuestClientTabletFlow.nativeHandsActive() || !TabletNativeArms.active()
                        || !com.github.razorplay01.fp_pal.firstperson.FpRenderState.isFpActive)
                        throw new AssertionError("Closing exposed a generic item frame");
                });
            }
            if (!context.computeOnClient(client -> QuestClientTabletFlow.carrying()))
                throw new AssertionError("Close did not return to the carry pose");
        }
        context.getInput().lookAt(0,0);
        dev.questplus.fabric.QuestPlusMod.LOGGER.info("TABLET STRIKE AND SEAMLESS CLOSE PASS");
    }
}
