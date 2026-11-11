package dev.questplus.fabric;

import dev.questplus.compat.GameTestShims;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;

public final class QuestIntegrationTest {
	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}

	private static Runnable step(Step action) {
		return () -> {
			try {
				action.run();
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static void diagramReload(GameTestHelper helper) throws Exception {
		var server = helper.getLevel().getServer();
		var runtime = QuestPlusMod.runtime();
		Path root = server.getWorldPath(LevelResource.ROOT).resolve("questplus");
		Path category = root.resolve("categories/diagram_fixture.json"), a = root.resolve("quests/diagram_a.json"),
				b = root.resolve("quests/diagram_b.json");
		String quest = """
				{"schemaVersion":1,"id":"questplus:diagram_%s","category":"questplus:diagram_fixture","icon":"minecraft:stone",
				 "title":{"en_us":"Diagram"},"objectives":[{"id":"mine","event":"block_break","target":"minecraft:stone","count":1}],
				 "rewards":[],"progression":"once"%s}
				""";
		String valid = """
				{"id":"questplus:diagram_fixture","title":{"en_us":"Diagram"},"description":{"en_us":"Fixture"},"group":"Builders",
				 "annotations":[{"id":"area","x":0,"y":0,"width":300,"height":180,"text":{},"style":"background","color":"#896f55"},
				    {"id":"caption","x":0,"y":-120,"text":{"en_us":"Aligned"},"style":"label","align":"right"}],
				 "linkRoutes":[{"from":"questplus:diagram_a","to":"questplus:diagram_b","points":[{"x":20,"y":30}]}]}
				""";
		boolean createdA = false, createdB = false, createdCategory = false;
		try {
			Files.writeString(a, quest.formatted("a", ""), StandardOpenOption.CREATE_NEW);
			createdA = true;
			Files.writeString(b, quest.formatted("b", ",\"requires\":{\"quest\":\"questplus:diagram_a\"}"),
					StandardOpenOption.CREATE_NEW);
			createdB = true;
			Files.writeString(category, valid, StandardOpenOption.CREATE_NEW);
			createdCategory = true;
			runtime.reload();
			var loaded = runtime.categories().stream().filter(c -> c.id().equals("questplus:diagram_fixture"))
					.findFirst().orElseThrow();
			helper.assertTrue(
					loaded.annotations().getFirst().style().equals("background")
							&& loaded.linkRoutes().getFirst().points().size() == 1,
					"Background and route survive actual registry reload");
			helper.assertTrue("right".equals(loaded.annotations().getLast().align()),
					"Annotation alignment survives authoritative reload and client projection");
			helper.assertTrue(
					loaded.group().equals("Builders") && dev.questplus.clientdata.ClientProjection
							.categories(java.util.List.of(loaded), runtime.registry().all()).get(0).getAsJsonObject()
							.get("group").getAsString().equals("Builders"),
					"Chapter group survives reload and projection");
			long generation = runtime.catalogGeneration();
			Files.writeString(category, valid.replace("\"align\":\"right\"", "\"align\":\"justify\""));
			boolean alignmentRejected = false;
			try {
				runtime.reload();
			} catch (IllegalArgumentException expected) {
				alignmentRejected = true;
			}
			helper.assertTrue(
					alignmentRejected && runtime.catalogGeneration() == generation
							&& runtime.categories().contains(loaded),
					"Invalid alignment keeps the previous registry intact");
			Files.writeString(category, valid.replace("\"x\":20", "\"x\":100001"));
			boolean rejected = false;
			try {
				runtime.reload();
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			helper.assertTrue(
					rejected && runtime.catalogGeneration() == generation && runtime.categories().contains(loaded),
					"Invalid route rejects the entire reload without replacing the previous registry");
		} finally {
			if (createdCategory)
				Files.deleteIfExists(category);
			if (createdA)
				Files.deleteIfExists(a);
			if (createdB)
				Files.deleteIfExists(b);
			runtime.reload();
		}
	}

	@GameTest(maxTicks = 4000)
	public void verticalSlice(GameTestHelper helper) throws Exception {
		var level = helper.getLevel();
		var server = level.getServer();
		var runtime = QuestPlusMod.runtime();
		LegacyGameTestFixtures.install(server);
		runtime.reload();
		Path global = Files.createTempDirectory("questplus-default-test-");
		try {
			Defaults.install(global, runtime::hasStructure);
			var pack = QuestHubFiles.snapshot(global);
			long generation = runtime.catalogGeneration();
			QuestHubFiles.applyTo(global, global.resolveSibling(global.getFileName() + "-backups"), pack, runtime,
					false, true);
			helper.assertTrue(runtime.catalogGeneration() == generation,
					"Editing defaults must not reload the active world");
			var invalid = pack.deepCopy();
			for (var entry : invalid)
				if (entry.getAsJsonObject().get("name").getAsString().equals("config.json"))
					entry.getAsJsonObject().addProperty("content", "{\"schemaVersion\":999,\"autosaveSeconds\":5}");
			boolean rejected = false;
			try {
				QuestHubFiles.applyTo(global, global.resolveSibling(global.getFileName() + "-backups"), invalid,
						runtime, false, true);
			} catch (Exception expected) {
				rejected = true;
			}
			helper.assertTrue(
					rejected && QuestHubFiles.snapshot(global).equals(pack)
							&& runtime.catalogGeneration() == generation,
					"Invalid default publication leaves both default pack and active world unchanged");
			Path testWorld = Files.createTempDirectory("questplus-sync-test-");
			try {
				Defaults.installWorld(global, testWorld.resolve("questplus"));
				Path config = global.resolve("config.json"), local = testWorld.resolve("questplus/config.json");
				var changed = Defaults.readObject(config);
				changed.addProperty("autosaveSeconds", 31);
				Files.writeString(config, changed.toString());
				QuestHubFiles.syncDefaults(global, testWorld, runtime);
				helper.assertTrue(Defaults.readObject(local).get("autosaveSeconds").getAsInt() == 31,
						"Changed defaults replace existing definitions");
				helper.assertTrue(Files.isDirectory(testWorld.resolve("questplus-hub-backups")),
						"Previous definitions are backed up");
				var edited = Defaults.readObject(local);
				edited.addProperty("autosaveSeconds", 32);
				Files.writeString(local, edited.toString());
				QuestHubFiles.syncDefaults(global, testWorld, runtime);
				helper.assertTrue(Defaults.readObject(local).get("autosaveSeconds").getAsInt() == 32,
						"Unchanged defaults do not overwrite world edits");
				changed.addProperty("autosaveSeconds", 0);
				Files.writeString(config, changed.toString());
				boolean invalidSync = false;
				try {
					QuestHubFiles.syncDefaults(global, testWorld, runtime);
				} catch (Exception expected) {
					invalidSync = true;
				}
				helper.assertTrue(invalidSync && Defaults.readObject(local).get("autosaveSeconds").getAsInt() == 32,
						"Invalid defaults preserve world definitions");
			} finally {
				try (var paths = Files.walk(testWorld)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
						Files.delete(path);
				}
			}
		} finally {
			for (Path dir : List.of(global, global.resolveSibling(global.getFileName() + "-backups")))
				if (Files.exists(dir))
					try (var paths = Files.walk(dir)) {
						for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
							Files.delete(path);
					}
		}
		diagramReload(helper);
		var player = GameTestShims.mockServerPlayer(helper, GameType.SURVIVAL);
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		var progress = runtime.player(player.getUUID());
		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		player.setShiftKeyDown(true);
		level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
		helper.assertTrue(player.gameMode.destroyBlock(pos), "Real crouched block break");
		helper.assertTrue(!progress.quests.containsKey("questplus:survival/logs"), "NOT rejects crouched log break");
		player.setShiftKeyDown(false);
		for (int i = 0; i < 16; i++) {
			level.setBlockAndUpdate(pos, Blocks.OAK_LOG.defaultBlockState());
			helper.assertTrue(player.gameMode.destroyBlock(pos), "Real log break");
		}
		helper.assertTrue(progress.quests.get("questplus:survival/logs").completed, "Block tag completes quest");
		int[] xp = new int[1];
		var sequence = helper.startSequence();
		Runnable idle = () -> helper.assertTrue(!runtime.isBusy(player.getUUID()), "Wait for durable operation");
		sequence.thenWaitUntil(idle).thenExecute(step(() -> {
			for (int i = 0; i < 5; i++) {
				var skeleton = helper.spawn(GameTestShims.SKELETON, new BlockPos(4, 2, 4));
				skeleton.setNoAi(true);
				Arrow arrow = helper.spawn(GameTestShims.ARROW, new BlockPos(4, 2, 4));
				arrow.setOwner(player);
				skeleton.hurtServer(level, level.damageSources().arrow(arrow, player), 1000);
				arrow.discard();
			}
			helper.assertTrue(progress.quests.get("questplus:combat/skeletons").completed,
					"Projectile credited to shooter");
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			Path file = server.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/integration.json");
			String extra = """
					{"schemaVersion":1,"id":"questplus:integration_pickup","category":"questplus:survival","icon":"minecraft:diamond",
					 "title":{"en_us":"Pickup integration"},"description":{"en_us":"Fixture"},"progression":"once",
					 "objectives":[{"id":"diamonds","event":"item_obtained","target":"minecraft:diamond","count":64}],"rewards":[]}
					""";
			Files.writeString(file, extra);
			runtime.reload();
			int count = runtime.registry().all().size();
			Files.writeString(file, "{bad json");
			boolean rejected = false;
			try {
				runtime.reload();
			} catch (Exception expected) {
				rejected = true;
			}
			helper.assertTrue(rejected && runtime.registry().all().size() == count, "Bad reload retains registry");
			Files.writeString(file, extra.replace("\"rewards\":[]",
					"\"rewards\":[{\"type\":\"item\",\"item\":\"minecraft:air\",\"count\":1}]"));
			rejected = false;
			try {
				runtime.reload();
			} catch (Exception expected) {
				rejected = true;
			}
			helper.assertTrue(rejected, "AIR reward rejected");
			Files.writeString(file, extra);
			runtime.reload();
			for (int slot = 0; slot < 36; slot++)
				player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 63));
			var item = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.DIAMOND, 3));
			item.setNoPickUpDelay();
			level.addFreshEntity(item);
			item.playerTouch(player);
			helper.assertTrue(progress.quests.get("questplus:integration_pickup").counters.get("diamonds") == 1,
					"Partial pickup exact delta");
			helper.assertTrue(progress.quests.get("questplus:exploration/diamond").completed, "OR dependency unlocks");
			item.playerTouch(player);
			helper.assertTrue(progress.quests.get("questplus:integration_pickup").counters.get("diamonds") == 1,
					"Full inventory no double count");
			player.getInventory().setItem(1, ItemStack.EMPTY);
			item.playerTouch(player);
		})).thenWaitUntil(() -> {
			idle.run();
			helper.assertTrue(progress.quests.get("questplus:integration_pickup").counters.get("diamonds") == 3,
					"Deferred remainder counted");
		}).thenExecute(step(() -> {
			player.getInventory().clearContent();
			var dispatcher = server.getCommands().getDispatcher();
			boolean denied = false;
			try {
				dispatcher.execute("questplus reload", player.createCommandSourceStack());
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
				denied = true;
			}
			helper.assertTrue(denied, "Non-operator denied");
			helper.assertTrue(dispatcher.execute("questplus progress questplus:survival/logs",
					player.createCommandSourceStack()) == 1, "Namespaced ID parsed");
			xp[0] = player.totalExperience;
			helper.assertTrue(dispatcher.execute("questplus claim questplus:survival/logs",
					player.createCommandSourceStack()) == 1, "Claim accepted");
			helper.assertTrue(dispatcher.execute("questplus claim questplus:survival/logs",
					player.createCommandSourceStack()) == 0, "Concurrent claim denied");
		})).thenWaitUntil(() -> {
			idle.run();
			helper.assertTrue("delivered".equals(progress.claims.get("questplus:survival/logs")),
					"Reward ledger delivered");
		}).thenExecute(step(() -> {
			helper.assertTrue(player.totalExperience == xp[0] + 40, "XP delivered once");
			helper.assertTrue(player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.BREAD))
					.mapToInt(ItemStack::getCount).sum() == 4, "Four bread delivered once");
			var dispatcher = server.getCommands().getDispatcher();
			helper.assertTrue(
					dispatcher.execute("questplus quest reset " + player.getUUID() + " questplus:survival/logs",
							server.createCommandSourceStack()) == 1,
					"Admin reset");
			helper.assertTrue(
					dispatcher.execute("questplus quest grant " + player.getUUID() + " questplus:survival/logs",
							server.createCommandSourceStack()) == 1,
					"Admin grant");
			helper.assertTrue(dispatcher.execute("questplus claim questplus:survival/logs",
					player.createCommandSourceStack()) == 0, "Reset retains claims");
			helper.assertTrue(player.totalExperience == xp[0] + 40, "No duplicate XP");
			List<String> offline = new ArrayList<>();
			for (int i = 0; i < 140; i++) {
				UUID uuid = UUID.randomUUID();
				offline.add(uuid.toString());
				runtime.engine().grant(runtime.player(uuid), "questplus:survival/logs");
				runtime.changed(uuid);
			}
			Files.writeString(Path.of(System.getProperty("questplus.integration.output")),
					new Gson().toJson(Map.of(
							"directory", server.getWorldPath(LevelResource.ROOT).resolve("data/questplus/players")
									.toAbsolutePath().toString(),
							"players", offline, "recipient", player.getUUID().toString())));
		})).thenSucceed();
	}
}
