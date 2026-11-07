package dev.questplus.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;

/** Real GPU captures in a disposable world. Compiled only by showcase.gradle. */
public final class QuestShowcaseGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().adjustSettings(settings -> {
            var preset=settings.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
            settings.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
            settings.setSeed("20261105");
            settings.setName("QuestPlus Showcase");
            settings.setDifficulty(Difficulty.PEACEFUL);
        }).create()) {
            context.waitFor(client -> QuestPlusClient.STATE.ready,600);
            context.getInput().resizeWindow(1600,900);
            context.runOnClient(client -> {
                client.gui.setScreen(null);
                client.options.fov().set(70);
                client.options.guiScale().set(2);
                client.options.renderDistance().set(8);
                QuestPlusClient.settings.blur=false;
                QuestPlusClient.settings.screenEmissive=true;
                QuestPlusClient.settings.animations=true;
                QuestPlusClient.settings.tablet=true;
                QuestPlusClient.settings.notifications=false;
                QuestPlusClient.settings.hud=true;
                QuestPlusClient.settings.scale=1;
                shader(true);
                if (!TabletShaderCompat.shadersActive()) throw new AssertionError("Complementary is not active");
            });
            world.getServer().runCommand("time set 1000");
            world.getServer().runCommand("weather clear");
            world.getServer().runCommand("gamemode survival @p");
            world.getServer().runCommand("effect give @p minecraft:resistance infinite 4 true");
            world.getServer().runCommand("tp @p ~ ~ ~ 155 0");
            world.getServer().runCommand("execute as @p run questplus team create Explorers");
            context.waitFor(client -> QuestPlusClient.STATE.team!=null,300);
            context.waitTicks(160);
            context.takeScreenshot("01-landscape-complementary");

            context.runOnClient(QuestClientTabletFlow::toggle);
            frames(context,"opening",42);
            context.waitFor(client -> TabletSurfaceRenderer.active(),120);
            int[] position=context.computeOnClient(client -> {
                var ids=new java.util.ArrayList<>(QuestPlusClient.STATE.categories.keySet());
                int index=ids.indexOf("questplus:equipment");
                if (index<0) throw new AssertionError("Missing bundled equipment chapter");
                int scroll=Math.min(index,Math.max(0,ids.size()-8));
                return new int[]{scroll,index-scroll};
            });
            var sidebar=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(20,180));
            context.getInput().setCursorPos(sidebar.x(),sidebar.y());
            for (int i=0;i<position[0];i++) context.getInput().scroll(-1);
            clickFace(context,20,63+position[1]*26);
            var diagram=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(270,165));
            context.getInput().setCursorPos(diagram.x(),diagram.y());
            context.getInput().scroll(-8);
            context.getInput().pressKey(InputConstants.KEY_HOME);
            context.getInput().setCursorPos(20,20);
            context.waitTicks(4);
            frames(context,"tree",16);
            context.takeScreenshot("02-tablet-complementary");
            // Use the real UI and server catalogue; no fake progress or synthetic quest screen.
            int selected=context.computeOnClient(client -> QuestPlusClient.STATE.quests.values().stream()
                .filter(q -> q.category().equals("questplus:equipment")).map(q -> q.id()).sorted().toList()
                .indexOf("questplus:equipment/set_leather"));
            if (selected<0) throw new AssertionError("Missing leather equipment quest");
            for (int i=0;i<=selected;i++) context.getInput().pressKey(InputConstants.KEY_F6);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.getInput().pressKey(InputConstants.KEY_HOME);
            context.waitTicks(5);
            context.takeScreenshot("03-quest-details-complementary");
            frames(context,"details",24);
            clickFace(context,410,77);
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(3);
            clickFace(context,125,13);
            context.waitTicks(4);
            context.takeScreenshot("04-team-complementary");
            clickFace(context,45,13);
            context.waitTicks(4);
            context.runOnClient(client -> client.gui.screen().onClose());
            frames(context,"closing",24);
            context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(),120);
            context.runOnClient(client -> {
                if (client.gui.screen()!=null || TabletSurfaceRenderer.hidesHud())
                    throw new AssertionError("The tablet did not restore the game HUD");
                if (!QuestPlusClient.settings.pinned.contains("questplus:equipment/set_leather"))
                    throw new AssertionError("The real Pin to HUD button did not pin the selected quest");
            });
            context.waitTicks(10);
            context.takeScreenshot("05-hud-complementary");
            context.runOnClient(client -> shader(false));
            context.waitTicks(30);
            context.runOnClient(QuestClientTabletFlow::toggle);
            context.waitFor(client -> TabletSurfaceRenderer.active(),120);
            context.waitTicks(8);
            context.getInput().pressKey(InputConstants.KEY_HOME);
            context.takeScreenshot("06-tablet-readable");
            context.runOnClient(client -> client.gui.screen().onClose());
            context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(),120);
            context.runOnClient(client -> QuestPlusClient.settings.tablet=false);
            context.runOnClient(QuestClientTabletFlow::toggle);
            context.waitTicks(8);
            context.getInput().pressKey(InputConstants.KEY_HOME);
            context.takeScreenshot("07-tablet-flat");
            dev.questplus.fabric.QuestPlusMod.LOGGER.info("SHOWCASE PASS: Complementary active, real catalogue, details, team, close and HUD");
        }
    }

    private static void clickFace(ClientGameTestContext context,double x,double y) {
        var point=context.computeOnClient(client -> TabletFaceAnchor.windowPoint(x,y));
        context.getInput().setCursorPos(point.x(),point.y());
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static void frames(ClientGameTestContext context,String clip,int count) {
        var folder=FabricLoader.getInstance().getGameDir().resolve("frames").resolve(clip);
        try {
            Files.createDirectories(folder);
            for (int i=0;i<count;i++) {
                context.runOnClient(client -> captureClock());
                context.waitTick();
                Files.copy(context.takeScreenshot(clip+"-"+i),folder.resolve(String.format("%04d.png",i)),StandardCopyOption.REPLACE_EXISTING);
                context.runOnClient(client -> captureClock());
            }
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void captureClock() {
        // Exclude slow synchronous PNG readbacks from animation watchdog time in this capture-only run.
        try {
            for (String name:new String[]{"openingSince","closingSince"}) {
                var field=QuestClientTabletFlow.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setLong(null,System.nanoTime());
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void shader(boolean enabled) {
        try {
        String pack;
        try (var files=Files.list(FabricLoader.getInstance().getGameDir().resolve("shaderpacks"))) {
            pack=files.filter(p -> p.getFileName().toString().contains("complementary-reimagined")).findFirst().orElseThrow().getFileName().toString();
        }
        var iris=Class.forName("net.irisshaders.iris.Iris");
        var config=iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName",String.class).invoke(config,pack);
        config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,enabled);
        config.getClass().getMethod("save").invoke(config);
        iris.getMethod("reload").invoke(null);
        if (Boolean.TRUE.equals(iris.getMethod("isFallback").invoke(null))) throw new AssertionError("Iris shader fallback");
        } catch (ReflectiveOperationException | java.io.IOException e) {
            throw new AssertionError("Cannot enable Complementary",e);
        }
    }
}
