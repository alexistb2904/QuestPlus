package dev.questplus.fabric;

import com.google.gson.JsonParser;
import dev.questplus.fabric.client.QuestGiverScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;

/**
 * The NPC quest dialog in a real client: a long offer (many objectives and
 * rewards), the accepted quest's progress, an NPC whose quest is done, an NPC
 * with nothing, and the skin portrait.
 */
public final class QuestGiverClientGameTest {
	private static final String[] QUESTS = { """
			{schemaVersion:2,id:'questplus:npc_big',category:'%1$s',icon:'minecraft:filled_map',
			  title:{en_us:'The Long Road North',fr_fr:'La longue route du nord'},
			  description:{en_us:'The pass is snowed in and my carts are stuck. Bring me what the caravan needs and I will make it worth your while.',
			               fr_fr:'Le col est enneigé et mes charrettes sont bloquées. Rapporte-moi ce dont la caravane a besoin, et tu ne le regretteras pas.'},
			  progression:'once',objectives:[
			  {id:'a',event:'block_break',target:'minecraft:oak_log',count:32},
			  {id:'b',event:'item_craft',target:'minecraft:chest',count:4},
			  {id:'c',event:'entity_kill',target:'minecraft:wolf',count:3},
			  {id:'d',event:'biome_visit',target:'minecraft:snowy_plains',count:1},
			  {id:'e',event:'item_obtained',target:'minecraft:sweet_berries',count:16},
			  {id:'f',event:'block_place',target:'minecraft:torch',count:24}],
			  rewards:[{type:'item',item:'minecraft:diamond',count:3},{type:'item',item:'minecraft:emerald',count:12},
			  {type:'item',item:'minecraft:golden_apple',count:2},{type:'item',item:'minecraft:spyglass',count:1},
			  {type:'item',item:'minecraft:ender_pearl',count:8},{type:'item',item:'minecraft:bundle',count:1},
			  {type:'experience',amount:500}]}
			""", """
			{schemaVersion:2,id:'questplus:npc_small',category:'%1$s',icon:'minecraft:bread',
			  title:{en_us:'Bread for the Road',fr_fr:'Du pain pour la route'},progression:'once',
			  objectives:[{id:'a',event:'item_craft',target:'minecraft:bread',count:3}],
			  rewards:[{type:'experience',amount:50}]}
			""", """
			{schemaVersion:2,id:'questplus:npc_done',category:'%1$s',icon:'minecraft:compass',
			  title:{en_us:'The Lost Compass',fr_fr:'La boussole perdue'},progression:'once',
			  objectives:[{id:'a',event:'item_craft',target:'minecraft:compass',count:1}],rewards:[]}
			""" };

	private QuestGiverClientGameTest() {
	}

	public static void run(ClientGameTestContext context) {
		var language = context.computeOnClient(client -> {
			client.options.languageCode = "fr_fr";
			client.getLanguageManager().setSelected("fr_fr");
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> language.isDone(), 600);
		language.join();
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> dev.questplus.fabric.client.QuestPlusClient.STATE.ready, 600);
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(client -> client.options.guiScale().set(2));
			var server = world.getServer();
			server.runOnServer(instance -> {
				try {
					var rt = QuestPlusMod.runtime();
					var root = instance.getWorldPath(LevelResource.ROOT).resolve("questplus");
					for (int i = 0; i < QUESTS.length; i++)
						Files.writeString(root.resolve("quests/npc-dialog-" + i + ".json5"), QUESTS[i].formatted(rt.categories().getFirst().id()));
					var config = JsonParser.parseString(Files.readString(root.resolve("config.json"))).getAsJsonObject();
					config.add("npcs", JsonParser.parseString("""
							[{"id":"marlo","name":{"en_us":"Marlo the Cartographer","fr_fr":"Marlo le cartographe"},"model":"villager","villager":"taiga",
							  "quests":["questplus:npc_big","questplus:npc_small"]},
							 {"id":"sela","name":{"en_us":"Sela","fr_fr":"Sela"},"model":"villager","villager":"desert","quests":["questplus:npc_done"]},
							 {"id":"oswin","name":{"en_us":"Oswin the Wanderer","fr_fr":"Oswin le vagabond"},"model":"player","quests":[]}]
							"""));
					Files.writeString(root.resolve("config.json"), config.toString());
					rt.reload();
					var player = instance.getPlayerList().getPlayers().getFirst();
					rt.mutate(player.getUUID(), "questplus:npc_done", "grant");
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
			open(context, server, "marlo", 0);
			context.getInput().setCursorPos(1150, 560);
			context.waitTicks(2);
			context.takeScreenshot("npc-offer-long");
			context.runOnClient(client -> client.gui.screen().mouseScrolled(600, 300, 0, -40));
			context.waitTicks(2);
			context.takeScreenshot("npc-offer-scrolled");
			click(context, net.minecraft.network.chat.Component.translatable("questplus.npc.accept").getString());
			context.waitTicks(10);
			context.takeScreenshot("npc-accepted-progress");
			click(context, "›");
			context.waitTicks(5);
			context.takeScreenshot("npc-second-request");
			context.runOnClient(client -> client.gui.setScreen(null));
			open(context, server, "sela", 2);
			context.takeScreenshot("npc-already-done");
			context.runOnClient(client -> client.gui.setScreen(null));
			open(context, server, "oswin", 3);
			context.takeScreenshot("npc-nothing-skin-portrait");
			context.runOnClient(client -> client.options.guiScale().set(4));
			context.waitTicks(5);
			context.runOnClient(client -> client.gui.setScreen(null));
			open(context, server, "marlo", 0);
			context.takeScreenshot("npc-compact-scale-4");
		}
	}

	/** Clicks a dialog button the way a player does, through the real mouse input. */
	private static void click(ClientGameTestContext context, String label) {
		var coords = context.computeOnClient(client -> {
			var button = client.gui.screen().children().stream()
					.filter(dev.questplus.fabric.client.ui.QuestButton.class::isInstance)
					.map(dev.questplus.fabric.client.ui.QuestButton.class::cast)
					.filter(b -> b.getMessage().getString().equals(label)).findFirst()
					.orElseThrow(() -> new AssertionError("Missing NPC dialog button: " + label));
			double sx = (double) client.getWindow().getWidth() / client.getWindow().getGuiScaledWidth();
			return new double[] { (button.getX() + button.getWidth() / 2.0) * sx, (button.getY() + button.getHeight() / 2.0) * sx };
		});
		context.getInput().setCursorPos(coords[0], coords[1]);
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
		context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(5);
	}

	/** Spawns the NPC two blocks in front of the player (once) and opens its dialog. */
	private static void open(ClientGameTestContext context, TestServerContext server, String npc, int slot) {
		server.runOnServer(instance -> {
			var player = instance.getPlayerList().getPlayers().getFirst();
			var level = player.level();
			var existing = level.getEntitiesOfClass(QuestGiver.class, player.getBoundingBox().inflate(12))
					.stream().filter(giver -> giver.npc().equals(npc)).findFirst();
			var giver = existing.orElseGet(() -> QuestGiver.spawn(level, QuestPlusMod.runtime().npcs().get(npc),
					player.blockPosition().offset(slot - 1, 0, 3)));
			QuestGiverNetwork.open(player, giver);
		});
		context.waitFor(client -> client.gui.screen() instanceof QuestGiverScreen, 300);
		context.waitTicks(10);
	}
}
