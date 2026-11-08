package dev.questplus.fabric;

import dev.questplus.core.Localization;
import dev.questplus.fabric.client.*;
import dev.questplus.fabric.client.ui.QuestButton;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.*;

/** Real relay draft, category picker, refresh, apply and reopen regression. */
public final class QuestRelayClientGameTest {
    public static void run(ClientGameTestContext context) {
        var language = context.computeOnClient(client -> {
            client.options.languageCode = "fr_fr";
            client.getLanguageManager().setSelected("fr_fr");
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> language.isDone(), 600); language.join();
        try (var world = context.worldBuilder().create()) {
            context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
            context.getInput().resizeWindow(1920, 1080);
            var server = world.getServer();
            BlockPos[] pos = new BlockPos[1];
            server.runOnServer(instance -> {
                try {
                    var rt = QuestPlusMod.runtime();
                    Files.writeString(instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/relay-base.json5"), """
                            {schemaVersion:2,id:'questplus:blocks_client',category:'%s',icon:'minecraft:stone',
                            title:{en_us:'Supply the settlement'},progression:'once',objectives:[
                            {id:'stone',event:'item_turn_in',target:'minecraft:stone',count:8}],rewards:[]}
                            """.formatted(rt.categories().getFirst().id()));
                    rt.reload();
                    var player = instance.getPlayerList().getPlayers().getFirst();
                    pos[0] = player.blockPosition().offset(0, 0, 2);
                    player.level().setBlock(pos[0], QuestWorldBlocks.RELAY.defaultBlockState(), 3);
                    var relay = (QuestUtilityEntity) player.level().getBlockEntity(pos[0]);
                    relay.claim(player.getUUID());
                    relay.configure(player, QuestWorldBlocksGameTest.config("questplus:blocks_client", "stone"), null, 0);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            run(context, server, pos[0]);
        }
    }
    static void run(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
        context.runOnClient(client -> client.options.guiScale().set(2));
        server.runOnServer(instance -> {
            try {
                var rt = QuestPlusMod.runtime();
                var directory = instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests");
                for (int i = 0; i < 2; i++) Files.writeString(directory.resolve("relay-picker-" + i + ".json5"), """
                        {schemaVersion:2,id:'questplus:relay_picker_%s',category:'%s',icon:'minecraft:diamond',
                        title:{en_us:'Relay picker %s'},progression:'once',objectives:[
                        {id:'stone',event:'item_turn_in',target:'minecraft:stone',count:8}],rewards:[]}
                        """.formatted(i, i == 0 ? rt.categories().getFirst().id() : rt.categories().getLast().id(), i));
                rt.reload();
                var player = instance.getPlayerList().getPlayers().getFirst();
                QuestBlockNetwork.open(player, (QuestUtilityEntity) player.level().getBlockEntity(pos));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        context.waitFor(client -> client.gui.screen() instanceof QuestRelayScreen
                && QuestPlusClient.STATE.quests.containsKey("questplus:relay_picker_1"), 300);
        context.runOnClient(client -> {
            if (client.gui.screen().children().stream().anyMatch(EditBox.class::isInstance))
                throw new AssertionError("Relay configuration must use named choices, not ID text fields");
            for (var child : client.gui.screen().children()) if (child instanceof QuestButton b
                    && b.getMessage().getString().matches(".*[a-f0-9]{8}-[a-f0-9-]{27}.*"))
                throw new AssertionError("UUID exposed in relay controls");
        });
        context.takeScreenshot("relay-configuration");
        String playerName = context.computeOnClient(client -> client.player.getPlainTextName());
        clickLabel(context, playerName);
        context.takeScreenshot("relay-player-names");
        clickLabel(context, playerName);
        click(context, "output.binary");
        click(context, "pick_quest");
        context.takeScreenshot("relay-category-picker");
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst();
            var relay = (QuestUtilityEntity) player.level().getBlockEntity(pos);
            if (!relay.config.output().equals("binary")) throw new AssertionError("Draft was applied before Apply");
            relay.signal = 7;
        });
        context.waitTicks(30);
        context.runOnClient(client -> {
            if (!client.gui.screen().getClass().getSimpleName().equals("QuestBlockPicker"))
                throw new AssertionError("Live server snapshot closed the picker");
        });
        String categoryName = context.computeOnClient(client -> {
            var state = QuestPlusClient.STATE;
            var data = state.categories.get(state.quests.get("questplus:relay_picker_0").category()).getAsJsonObject("title");
            Map<String, String> names = new HashMap<>(); data.entrySet().forEach(e -> names.put(e.getKey(), e.getValue().getAsString()));
            return Localization.text(names, client.options.languageCode, Map.of());
        });
        clickPaged(context, categoryName);
        context.runOnClient(client -> {
            if (client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .anyMatch(b -> b.getMessage().getString().equals("Relay picker 1")))
                throw new AssertionError("Quest from another category leaked into selection");
            client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow().setValue("Relay picker 0");
        });
        context.takeScreenshot("relay-quests-with-icons");
        clickLabel(context, "Relay picker 0");
        context.waitTicks(3);
        click(context, "save"); context.waitTicks(25);
        server.runOnServer(instance -> {
            var relay = (QuestUtilityEntity) instance.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos);
            if (!relay.config.output().equals("pulse") || !relay.config.quests().contains("questplus:relay_picker_0"))
                throw new AssertionError("Named quest selection or draft did not reach server: " + relay.config.encode());
        });
        context.takeScreenshot("relay-applied");
        clickLabel(context, Component.translatable("questplus.blocks.relay.tab.linked", 2).getString());
        context.takeScreenshot("relay-linked-quests");
        click(context, "relay.remove");
        click(context, "close"); context.waitTicks(10);
        server.runOnServer(instance -> {
            var player = instance.getPlayerList().getPlayers().getFirst(); var relay = (QuestUtilityEntity) player.level().getBlockEntity(pos);
            if (relay.config.quests().size() != 2) throw new AssertionError("Close saved an abandoned removal");
            QuestBlockNetwork.open(player, relay);
        });
        context.waitFor(client -> client.gui.screen() instanceof QuestRelayScreen, 200);
        click(context, "relay.target_category");
        context.runOnClient(client -> {
            if (!(client.gui.screen() instanceof QuestRelayScreen)) throw new AssertionError("Target modes must switch inline");
            if (client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .anyMatch(b -> b.active && b.getMessage().getString().equals(Component.translatable("questplus.blocks.save").getString())))
                throw new AssertionError("Cannot apply an empty category target");
            if (client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .anyMatch(b -> b.visible && b.getMessage().getString().equals(Component.translatable("questplus.blocks.pick_quest").getString())))
                throw new AssertionError("Category mode must not expose the individual quest selector");
        });
        click(context, "relay.choose_category"); clickPaged(context, categoryName);
        context.takeScreenshot("relay-category-mode");
        click(context, "relay.view_category");
        context.runOnClient(client -> {
            if (client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .anyMatch(b -> b.getMessage().getString().equals(Component.translatable("questplus.blocks.relay.remove").getString())))
                throw new AssertionError("Category preview must not allow removing individual quests");
        });
        context.takeScreenshot("relay-category-preview");
        click(context, "relay.tab.configuration");
        click(context, "save"); context.waitTicks(25);
        server.runOnServer(instance -> {
            var relay = (QuestUtilityEntity) instance.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos);
            if (relay.config.category().isEmpty() || !relay.config.quests().isEmpty())
                throw new AssertionError("Category target retained stale quest links");
        });
        click(context, "condition.tier");
        context.runOnClient(client -> client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .findFirst().orElseThrow().setValue("2"));
        context.takeScreenshot("relay-tier-threshold");
        click(context, "save"); context.waitTicks(25);
        server.runOnServer(instance -> {
            var relay = (QuestUtilityEntity) instance.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos);
            if (!relay.config.condition().equals("tier") || relay.config.threshold() != 2)
                throw new AssertionError("Tier input did not reach server");
        });
        context.getInput().resizeWindow(1280, 800);
        context.runOnClient(client -> client.options.guiScale().set(4)); context.waitTicks(5);
        bounds(context);
        context.takeScreenshot("relay-compact-top");
        context.runOnClient(client -> client.gui.screen().mouseScrolled(100, 100, 0, -40));
        bounds(context);
        context.takeScreenshot("relay-compact-output-and-apply");
        for (int[] size : new int[][]{{1280,800,2},{1024,768,2},{1280,720,3},{1280,800,4}}) {
            context.getInput().resizeWindow(size[0],size[1]);
            context.runOnClient(client -> client.options.guiScale().set(size[2])); context.waitTicks(3);
            context.runOnClient(client -> client.gui.screen().mouseScrolled(100,100,0,100)); bounds(context);
            context.takeScreenshot("relay-layout-"+size[0]+"x"+size[1]+"-scale-"+size[2]);
            context.runOnClient(client -> client.gui.screen().mouseScrolled(100,100,0,-100)); bounds(context);
        }
        click(context, "close"); context.waitFor(client -> client.gui.screen() == null, 100);
        modelViews(context, server, pos);
    }
    private static void click(ClientGameTestContext context, String key) {
        clickLabel(context, Component.translatable("questplus.blocks." + key).getString());
    }
    private static void clickPaged(ClientGameTestContext context, String label) {
        for (int i = 0; i < 32; i++) {
            if (context.computeOnClient(client -> client.gui.screen().children().stream().filter(QuestButton.class::isInstance)
                    .map(QuestButton.class::cast).anyMatch(b -> b.getMessage().getString().equals(label)))) {
                clickLabel(context, label); return;
            }
            clickLabel(context, "›");
        }
        throw new AssertionError("Category not found: " + label);
    }
    private static void clickLabel(ClientGameTestContext context, String label) {
        context.runOnClient(client -> { if (client.gui.screen().getFocused() != null) client.gui.screen().getFocused().setFocused(false); });
        bounds(context);
        for (int i = 0; i < 30; i++) {
            int direction = context.computeOnClient(client -> {
                var b = client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                        .filter(button -> button.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
                return b.visible ? 0 : b.getY() < client.gui.screen().height / 2 ? 1 : -1;
            });
            if (direction == 0) break;
            context.runOnClient(client -> client.gui.screen().mouseScrolled(100, 200, 0, direction)); context.waitTicks(1);
        }
        var coords = context.computeOnClient(client -> {
            var button = client.gui.screen().children().stream().filter(QuestButton.class::isInstance).map(QuestButton.class::cast)
                    .filter(b -> b.getMessage().getString().equals(label)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
            if (!button.active || !button.visible || button.getY() < 0 || button.getBottom() > client.gui.screen().height)
                throw new AssertionError("Unreachable relay button: " + label + " at " + button.getY());
            return new double[]{(button.getX() + button.getWidth() / 2.0) * client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth(),
                    (button.getY() + button.getHeight() / 2.0) * client.getWindow().getHeight() / client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(coords[0], coords[1]);
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); context.waitTicks(2);
        context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT); context.waitTicks(5);
    }
    private static void modelViews(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
        context.getInput().resizeWindow(1600, 1000);
        context.runOnClient(c -> {c.options.guiScale().set(2);c.options.fov().set(55);c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);});
        server.runOnServer(instance -> {
            var p = instance.getPlayerList().getPlayers().getFirst(); var level = p.level();
            for (int x=-5;x<=5;x++)for(int z=-6;z<=5;z++) level.setBlock(pos.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(),3);
            level.setBlock(pos,QuestWorldBlocks.RELAY.defaultBlockState(),3);
            level.setBlock(pos.east(2),QuestWorldBlocks.RELAY.defaultBlockState().setValue(QuestUtilityBlock.POWERED,true),3);
            p.teleportTo(pos.getX()+1,pos.getY(),pos.getZ()-4);p.setYRot(0);p.setXRot(16);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        });
        context.getInput().lookAt(0,16);context.waitTicks(25);context.takeScreenshot("relay-model-off-and-on");
        for (String name : List.of("relay", "station")) {
            server.runOnServer(instance -> {
                var p = instance.getPlayerList().getPlayers().getFirst();
                var item = name.equals("relay") ? QuestWorldBlocks.RELAY.asItem() : QuestWorldBlocks.STATION.asItem();
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(item));
                var drop = new net.minecraft.world.entity.item.ItemEntity(p.level(),p.getX()+1,p.getY()+.15,p.getZ()+1,new net.minecraft.world.item.ItemStack(item));
                drop.setPickUpDelay(32000);drop.setNoGravity(true);p.level().addFreshEntity(drop);
            });
            context.waitTicks(15);context.takeScreenshot(name+"-first-person");
            context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(15);context.takeScreenshot(name+"-third-person-hand");
            server.runOnServer(instance -> {
                var p=instance.getPlayerList().getPlayers().getFirst();
                p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,p.getMainHandItem().copy());
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            });
            context.waitTicks(10);context.takeScreenshot(name+"-third-person-offhand");
            server.runOnServer(instance -> instance.getPlayerList().getPlayers().getFirst().setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY));
            context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        }
        server.runOnServer(instance -> {
            var p = instance.getPlayerList().getPlayers().getFirst();
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
            for(int i=0;i<2;i++) {
                var item = i==0 ? QuestWorldBlocks.RELAY.asItem() : QuestWorldBlocks.STATION.asItem();
                var drop = new net.minecraft.world.entity.item.ItemEntity(p.level(),p.getX()+.7+i*.8,p.getY()+.05,p.getZ()+2,new net.minecraft.world.item.ItemStack(item));
                drop.setPickUpDelay(32000);drop.setNoGravity(true);drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.level().addFreshEntity(drop);
            }
        });
        context.getInput().lookAt(-29,38);context.waitTicks(10);context.takeScreenshot("utility-small-ground-drops");
    }
    private static void bounds(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var widgets=client.gui.screen().children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                    .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).filter(w->w.visible).toList();
            for(var w:widgets) if(w.getX()<0||w.getY()<0||w.getRight()>client.gui.screen().width||w.getBottom()>client.gui.screen().height)
                throw new AssertionError("Widget outside screen: "+w.getMessage().getString());
            for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++) {
                var a=widgets.get(i);var b=widgets.get(j);
                if(a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY())
                    throw new AssertionError("Overlapping controls: "+a.getMessage().getString()+" / "+b.getMessage().getString());
            }
        });
    }
}
