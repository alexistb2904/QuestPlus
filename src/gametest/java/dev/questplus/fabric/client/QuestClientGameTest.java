package dev.questplus.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.*;
import java.nio.file.*;

/** Exercises real client initialization, packet transfer, rendering and authoritative mutations. */
public final class QuestClientGameTest implements FabricClientGameTest {
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
            context.clickScreenButton("questplus.client.team"); context.waitTicks(3);
            context.runOnClient(client -> { var screen=(QuestScreen)client.gui.screen(); screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("Client test team"); });
            context.clickScreenButton("questplus.client.create");
            context.waitFor(client -> QuestPlusClient.STATE.team!=null,300);
            context.takeScreenshot("questplus-team");
            context.clickScreenButton("questplus.client.settings"); context.waitTicks(3);
            context.takeScreenshot("questplus-settings");
            context.runOnClient(client -> client.gui.screen().onClose()); context.waitTicks(30);
            context.runOnClient(client -> { if (client.gui.screen()!=null) throw new AssertionError("Screen did not close"); });
            world.getServer().runCommand("questplus reload");
            context.waitFor(client -> QuestPlusClient.STATE.generation>=2,300);
        }
        context.runOnClient(client -> { if (QuestPlusClient.STATE.ready || !QuestPlusClient.STATE.quests.isEmpty()) throw new AssertionError("Disconnected data retained"); });
    }
}
