package dev.questplus.fabric;

import com.google.gson.*;
import dev.questplus.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

public final class FtbParityIntegrationTest {
	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}

	private static Runnable step(Step action) {
		return () -> {
			try {
				action.run();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};
	}

	private static ServerPlayer connect(GameTestHelper helper, String name) {
		var server = helper.getLevel().getServer();
		var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name),
				ClientInformation.createDefault());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	@GameTest(maxTicks = 1500)
	public void privateProgressMigrationConfirmsAndRollsBackWithoutReplayingClaims(GameTestHelper helper)
			throws Exception {
		var runtime = QuestPlusMod.runtime();
		var world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT);
		var player = connect(helper, "ftb-migration");
		UUID sourceId = UUID.randomUUID();
		UUID[] operation = { null };
		Path file = world.resolve("questplus/quests/ftb-migration-integration.json5"),
				metadata = world.resolve("questplus/queststudio-compatibility.json"),
				source = world.resolve("ftbquests/" + sourceId + ".json5");
		Files.createDirectories(source.getParent());
		Files.writeString(source,
				"{version:1,task_progress:{'11':1},started:{'10':1},completed:{'10':2},claimed_rewards:{}}\n");
		Files.writeString(file,
				"""
						{schemaVersion:2,id:'questplus:ftb_migration',category:'%s',icon:'minecraft:stone',title:{en_us:'Migrate'},progression:'once',objectives:[{id:'goal',event:'manual',target:'questplus:ftb_migration',count:1}],rewards:[]}
						"""
						.formatted(runtime.categories().getFirst().id()));
		runtime.reload();
		Runnable idle = () -> helper.assertTrue(!runtime.isBusy(player.getUUID()), "Await migration transaction");
		helper.startSequence().thenExecuteAfter(200, step(() -> {
			var data = Files.exists(metadata) ? JsonParser.parseString(Files.readString(metadata)).getAsJsonObject()
					: new JsonObject();
			data.add("ftbMappings", JsonParser.parseString(
					"[{sourceQuest:'000000000000000a',targetQuest:'questplus:ftb_migration',tasks:[{sourceId:'000000000000000b',objectiveId:'goal'}],rewards:[]}]")
					.getAsJsonArray());
			Files.writeString(metadata, data.toString());
			var parse = helper.getLevel().getServer().getCommands().getDispatcher()
					.parse("questplus migrate-ftb confirm", player.createCommandSourceStack());
			helper.assertTrue(parse.getReader().canRead(), "Non-operators cannot execute migration commands");
			String preview = runtime.migrations().preview("test-operator", sourceId, player.getUUID());
			var match = java.util.regex.Pattern.compile("[a-f0-9-]{36}").matcher(preview);
			helper.assertTrue(match.find(), "Preview includes rollback operation ID");
			operation[0] = UUID.fromString(match.group());
			boolean rejected = false;
			try {
				runtime.migrations().confirm("other-operator");
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			helper.assertTrue(rejected, "Only the reviewing operator can confirm");
			helper.assertTrue(!runtime.state(player.getUUID()).quests.containsKey("questplus:ftb_migration"),
					"Preview does not mutate progress");
			runtime.migrations().confirm("test-operator");
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(runtime.state(player.getUUID()).quests.get("questplus:ftb_migration").completed,
					"Confirmed migration persists completed state");
			helper.assertTrue(Files.exists(world.resolve("data/questplus/migrations/" + operation[0] + ".json")),
					"Private pre-migration backup exists");
			helper.assertTrue(Files.exists(source), "Original FTB save preserved");
			runtime.migrations().rollback(operation[0]);
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(!runtime.state(player.getUUID()).quests.containsKey("questplus:ftb_migration"),
					"Rollback restores prior state");
			var preview = runtime.migrations().preview("test-operator", sourceId, player.getUUID());
			var match = java.util.regex.Pattern.compile("[a-f0-9-]{36}").matcher(preview);
			match.find();
			operation[0] = UUID.fromString(match.group());
			runtime.migrations().confirm("test-operator");
		})).thenWaitUntil(idle).thenExecute(step(() -> {
			runtime.player(player.getUUID()).claims.put("test:newer_reward", "delivered");
			boolean rejected = false;
			try {
				runtime.migrations().rollback(operation[0]);
			} catch (IllegalStateException expected) {
				rejected = true;
			}
			helper.assertTrue(rejected, "Rollback refuses to erase a newer reward ledger");
			Files.delete(file);
			var data = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
			data.remove("ftbMappings");
			Files.writeString(metadata, data.toString());
			runtime.reload();
		})).thenSucceed();
	}

	@GameTest(maxTicks = 1000)
	public void realContainersXpComponentsAndCratesUseDurablePlans(GameTestHelper helper) throws Exception {
		var runtime = QuestPlusMod.runtime();
		var server = helper.getLevel().getServer();
		var definitions = server.getWorldPath(LevelResource.ROOT).resolve("questplus");
		Path file = definitions.resolve("quests/ftb-resource-integration.json5"),
				metadata = definitions.resolve("queststudio-compatibility.json");
		var library = Files.exists(metadata) ? JsonParser.parseString(Files.readString(metadata)).getAsJsonObject()
				: new JsonObject();
		if (!library.has("rewardTables"))
			library.add("rewardTables", new JsonArray());
		library.getAsJsonArray("rewardTables").add(JsonParser.parseString(
				"""
						{name:'integration_crate',crate:true,reward:{type:'questplus:all',data:{options:[{type:'item',item:'minecraft:stone',count:1,weight:1,components:{'minecraft:custom_data':{grade:2}}}]}}}
						"""));
		Files.writeString(metadata, library.toString());
		Files.writeString(file,
				"""
						{schemaVersion:2,id:'questplus:ftb_resources',category:'%s',icon:'minecraft:water_bucket',title:{en_us:'Resource submission'},progression:'once',objectiveMode:'sequence',
						 objectives:[{id:'levels',event:'xp_submit_levels',target:'questplus:levels',count:2},{id:'points',event:'xp_submit_points',target:'questplus:points',count:5},
						  {id:'water',event:'fluid_submit',target:'minecraft:water',count:1000},{id:'energy',event:'energy_submit',target:'questplus:fe',count:100,data:{fePerEnergy:10}}],rewards:[]}
						"""
						.formatted(runtime.categories().getFirst().id()));
		runtime.reload();
		var player = connect(helper, "ftb-resources");
		player.giveExperienceLevels(5);
		player.getInventory().setItem(5, new ItemStack(Items.WATER_BUCKET));
		var battery = LoaderTestSupport.battery(50);
		player.getInventory().setItem(6, battery);
		Runnable idle = () -> helper.assertTrue(!runtime.isBusy(player.getUUID()), "Await durable resource write");
		java.util.function.Supplier<List<net.minecraft.world.entity.item.ItemEntity>> dropped = () -> player.level()
				.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8, 128, 8),
						e -> e.getItem().is(Items.STONE));
		helper.startSequence().thenWaitUntil(idle).thenExecute(step(() -> {
			helper.assertTrue(!runtime.state(player.getUUID()).quests.containsKey("questplus:ftb_resources"),
					"Possessed resources do not count as submissions");
			helper.assertTrue(
					runtime.submitItem(player, "questplus:ftb_resources", "levels") == 2 && player.experienceLevel == 3,
					"Two real levels consumed");
			int before = Experience.points(player.experienceLevel, player.experienceProgress);
			helper.assertTrue(
					runtime.submitItem(player, "questplus:ftb_resources", "points") == 5
							&& Experience.points(player.experienceLevel, player.experienceProgress) == before - 5,
					"Five real points consumed");
			helper.assertTrue(
					runtime.submitItem(player, "questplus:ftb_resources", "water") == 1000
							&& player.getInventory().getItem(5).is(Items.BUCKET),
					"Fabric container emptied and bucket retained");
			helper.assertTrue(
					runtime.submitItem(player, "questplus:ftb_resources", "energy") == 100
							&& LoaderTestSupport.energy(player.getInventory().getItem(6)) == 40,
					"Energy adapter extracts real energy using explicit FE ratio");
			helper.assertTrue(runtime.state(player.getUUID()).quests.get("questplus:ftb_resources").completed,
					"All real submissions complete quest");
			for (int i = 0; i < 36; i++)
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			var crate = QuestItemStacks.decode(JsonParser.parseString(
					"{id:'questplus:loot_crate',components:{'minecraft:custom_data':{questplus_table:'integration_crate'}}}")
					.getAsJsonObject(), server.registryAccess());
			crate.setCount(2);
			player.getInventory().setItem(0, crate);
			runtime.openCrate(player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.getMainHandItem().getCount() == 1,
					"Exactly one crate consumed after saving its immutable rewards");
		})).thenWaitUntil(() -> helper.assertTrue(!dropped.get().isEmpty(),
				"Full inventory: crate loot drops at the player's feet")).thenWaitUntil(idle)
				.thenExecuteAfter(25, step(() -> {
					var stones = dropped.get();
					helper.assertTrue(stones.size() == 1 && stones.getFirst().getItem().getCount() == 1
							&& player.getInventory().countItem(Items.STONE) == 0,
							"Dropped once, never rerolled or duplicated");
					helper.assertTrue(
							QuestItemStacks.components(stones.getFirst().getItem(), server.registryAccess())
									.toString().contains("grade"),
							"Reward retains its custom component");
					stones.forEach(net.minecraft.world.entity.Entity::discard);
					Files.delete(file);
					var current = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
					current.getAsJsonArray("rewardTables").asList()
							.removeIf(t -> t.getAsJsonObject().get("name").getAsString().equals("integration_crate"));
					Files.writeString(metadata, current.toString());
					runtime.reload();
				})).thenSucceed();
	}

	@GameTest(maxTicks = 1000)
	public void sharedRewardsRemainUniqueAfterLeavingAndDisbanding(GameTestHelper helper) throws Exception {
		var runtime = QuestPlusMod.runtime();
		var file = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
				.resolve("questplus/quests/ftb-shared-integration.json5");
		Files.writeString(file,
				"""
						{schemaVersion:2,id:'questplus:ftb_shared',category:'%s',icon:'minecraft:diamond',title:{en_us:'Shared'},progression:'once',objectives:[{id:'done',event:'manual',target:'questplus:ftb_shared',count:1}],
						 rewards:[{type:'experience',amount:1,teamReward:true}]}
						"""
						.formatted(runtime.categories().getFirst().id()));
		runtime.reload();
		var owner = connect(helper, "ftb-owner");
		var member = connect(helper, "ftb-member");
		UUID[] team = { null };
		Runnable idle = () -> helper.assertTrue(!runtime.isBusy(owner.getUUID()) && !runtime.isBusy(member.getUUID()),
				"Await team write");
		helper.startSequence().thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.changeTeam(t -> t.create(owner.getUUID(), "FTB team"))))
				.thenWaitUntil(idle).thenExecute(step(() -> {
					team[0] = runtime.player(owner.getUUID()).teamId;
					runtime.changeTeam(t -> t.invite(owner.getUUID(), member.getUUID(), System.currentTimeMillis()));
				})).thenWaitUntil(idle)
				.thenExecute(step(
						() -> runtime.changeTeam(t -> t.join(member.getUUID(), team[0], System.currentTimeMillis()))))
				.thenWaitUntil(idle)
				.thenExecute(step(() -> runtime.interactObjective(owner, "questplus:ftb_shared", "done")))
				.thenWaitUntil(idle).thenExecute(step(() -> runtime.changeTeam(t -> t.leave(member.getUUID()))))
				.thenWaitUntil(idle).thenExecute(step(() -> runtime.claim(owner, "questplus:ftb_shared")))
				.thenWaitUntil(idle)
				.thenWaitUntil(() -> helper.assertTrue(owner.totalExperience == 1, "Owner received one team reward"))
				.thenExecute(step(() -> runtime.changeTeam(t -> t.disband(owner.getUUID())))).thenWaitUntil(idle)
				.thenExecute(step(() -> {
					boolean rejected = false;
					try {
						runtime.claim(member, "questplus:ftb_shared");
					} catch (IllegalArgumentException expected) {
						rejected = true;
					}
					helper.assertTrue(rejected && member.totalExperience == 0,
							"Former member cannot duplicate the shared reward after disband");
					helper.assertTrue(
							"delivered".equals(runtime.player(member.getUUID()).claims.get("questplus:ftb_shared")),
							"Shared receipt reconciles against original durable team scope");
					Files.delete(file);
					runtime.reload();
				})).thenSucceed();
	}
}
