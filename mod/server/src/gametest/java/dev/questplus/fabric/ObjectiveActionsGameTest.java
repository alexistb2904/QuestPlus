package dev.questplus.fabric;

import dev.questplus.fabric.api.QuestPlusApi;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class ObjectiveActionsGameTest {
	@GameTest(maxTicks = 300)
	public void javaExtensionSurvivesReloadAndValidatesItsData(GameTestHelper helper) throws Exception {
		String event = "questplus_test:machine";
		QuestPlusApi.registerEvent(event, "marker", false);
		QuestPlusApi.registerObjectiveData(event, data -> {
			if (!data.keySet().equals(java.util.Set.of("mode")) || !data.get("mode").isJsonPrimitive()
					|| !data.getAsJsonPrimitive("mode").isString())
				throw new IllegalArgumentException("Expected a machine mode");
		}, (data, submitted) -> data.get("mode").getAsString().equals(submitted.context().get("mode")));
		QuestPlusApi.registerEditorDescriptor("event", event, "Machine", com.google.gson.JsonParser.parseString(
				"[{key:'mode',type:'string',label:'Mode'}]").getAsJsonArray());
		var f = new ObjectiveTestFixture(helper, "java_extension", """
				[{id:'machine',event:'questplus_test:machine',target:'questplus_test:machine',count:4,data:{mode:'press'}}]
				""");
		try (f) {
			helper.assertTrue(dev.questplus.fabric.api.QuestStudioDescriptors.snapshot().asList().stream()
					.anyMatch(value -> value.getAsJsonObject().get("id").getAsString().equals(event)),
					"Extension descriptor is available to Studio");
			QuestPlusApi.submit(f.player, new dev.questplus.core.QuestEvent(event, event, java.util.Set.of(), 2,
					java.util.Map.of("mode", "wrong")));
			f.expect("machine", 0);
			QuestPlusApi.submit(f.player, new dev.questplus.core.QuestEvent(event, event, java.util.Set.of(), 2,
					java.util.Map.of("mode", "press")));
			f.expect("machine", 2);
			f.runtime.reload();
			QuestPlusApi.submit(f.player, new dev.questplus.core.QuestEvent(event, event, java.util.Set.of(), 1,
					java.util.Map.of("mode", "press")));
			f.expect("machine", 3);
			boolean rejected = false;
			try {
				dev.questplus.core.QuestExtensions.validateObjective(event, new com.google.gson.JsonObject());
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			helper.assertTrue(rejected, "Invalid extension data is rejected");
			var path = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
					.resolve("questplus/quests/java_extension-battery.json");
			String valid = java.nio.file.Files.readString(path);
			var invalid = com.google.gson.JsonParser.parseString(valid).getAsJsonObject();
			invalid.getAsJsonArray("objectives").get(0).getAsJsonObject().add("data", new com.google.gson.JsonObject());
			java.nio.file.Files.writeString(path, invalid.toString());
			try {
				rejected = false;
				try {
					f.runtime.reload();
				} catch (IllegalArgumentException expected) {
					rejected = true;
				}
				helper.assertTrue(rejected, "Reload rejects invalid extension data");
				f.expect("machine", 3);
				QuestPlusApi.submit(f.player, new dev.questplus.core.QuestEvent(event, event, java.util.Set.of(), 1,
						java.util.Map.of("mode", "press")));
				f.expect("machine", 4);
			} finally {
				java.nio.file.Files.writeString(path, valid);
			}
		}
		helper.succeed();
	}

    @GameTest(maxTicks = 300)
    public void targetedApiProgressOnlyChangesTheSelectedObjective(GameTestHelper helper) throws Exception {
        var f = new ObjectiveTestFixture(helper, "api_targeted", """
                [{id:'first',event:'block_break',target:'minecraft:stone',count:5},
                 {id:'second',event:'block_break',target:'minecraft:stone',count:5}]
                """);
        var changes = new java.util.ArrayList<String>();
        var watch = QuestPlusApi.onObjectiveProgress(e -> changes.add(e.objectiveId()));
        try {
            QuestPlusApi.setObjectiveProgress(f.player, f.quest, "first", 2);
            f.expect("first", 2);
            f.expect("second", 0);
            QuestPlusApi.incrementObjectiveProgress(f.player, f.quest, "first", 2);
            f.expect("first", 4);
            boolean rejected = false;
            try {
                QuestPlusApi.setObjectiveProgress(f.player, f.quest, "second", -1);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected, "Negative progress must be rejected");
            helper.assertTrue(changes.equals(java.util.List.of("first", "first")),
                    "Only the selected objective emits events");
        } finally {
            watch.close();
        }
        f.close();
        helper.succeed();
    }

	@GameTest(maxTicks = 300)
	public void trustedApiCompletionAndResetUseServerProgress(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "api_mutation", """
				[{id:'mine',event:'block_break',target:'minecraft:stone',count:2}]
				""");
		QuestPlusApi.completeQuest(f.player, f.quest);
		helper.assertTrue(QuestPlusApi.getProgress(f.player, f.quest).completed(), "Grant completes the quest");
		boolean rejected = false;
		try {
			QuestPlusApi.completeQuest(f.player, f.quest);
		} catch (IllegalStateException expected) {
			rejected = true;
		}
		helper.assertTrue(rejected, "A completed quest cannot be granted twice");
		helper.startSequence().thenWaitUntil(f::idle).thenExecute(() -> {
			try {
				QuestPlusApi.resetQuest(f.player, f.quest);
				helper.assertTrue(!QuestPlusApi.getProgress(f.player, f.quest).started(), "Reset clears progress");
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}).thenExecute(f::close).thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void realDeathsAdvanceAuthoredObjectives(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "death_counter", """
				[{id:'death',event:'player_death',target:'questplus:death',count:2}]
				""");
		try (f) {
			f.expect("death", 0);
			f.player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000);
			helper.assertTrue(f.player.isDeadOrDying(), "Player really died");
			f.expect("death", 1);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void actualItemComponentsSupportExactAndPartialMatching(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "components", """
				[{id:'partial',event:'item_obtained',target:'minecraft:stone',count:20,
				  data:{matchComponents:'partial',components:{'minecraft:custom_data':{grade:2}}}},
				 {id:'exact',event:'item_obtained',target:'minecraft:stone',count:20,
				  data:{matchComponents:'exact',components:{'minecraft:custom_data':{grade:2}}}},
				 {id:'inventory',event:'inventory_count',target:'minecraft:stone',count:20,
				  data:{matchComponents:'partial',components:{'minecraft:custom_data':{grade:2}}}},
				 {id:'craft',event:'item_craft',target:'minecraft:stone',count:20,
				  data:{matchComponents:'partial',components:{'minecraft:custom_data':{grade:2}}}}]
				""");
		f.pickup(new ItemStack(Items.STONE, 4));
		f.expect("partial", 0);
		f.expect("exact", 0);
		var decorated = QuestItemStacks.decode(com.google.gson.JsonParser.parseString(
				"{id:'minecraft:stone',count:3,components:{'minecraft:custom_data':{grade:2,extra:true}}}").getAsJsonObject(),
				f.player.registryAccess());
		f.pickup(decorated.copyWithCount(3));
		f.expect("partial", 3);
		f.expect("exact", 0);
		var exact = QuestItemStacks.decode(com.google.gson.JsonParser.parseString(
				"{id:'minecraft:stone',count:2,components:{'minecraft:custom_data':{grade:2}}}").getAsJsonObject(),
				f.player.registryAccess());
		f.pickup(exact.copyWithCount(2));
		f.expect("partial", 5);
		f.expect("exact", 2);
		var output = new net.minecraft.world.inventory.ResultContainer();
		var crafting = new net.minecraft.world.inventory.TransientCraftingContainer(f.player.inventoryMenu, 2, 2);
		var slot = new net.minecraft.world.inventory.ResultSlot(f.player, crafting, output, 0, 0, 0);
		output.setItem(0, new ItemStack(Items.STONE, 4));
		slot.onTake(f.player, slot.remove(4));
		f.expect("craft", 0);
		var crafted = exact.copyWithCount(4);
		output.setItem(0, crafted);
		slot.onTake(f.player, slot.remove(4));
		f.expect("craft", 4);
		helper.startSequence().thenWaitUntil(() -> f.expect("inventory", 5)).thenExecute(() -> {
			for (int i = 0; i < 36; i++)
				if (f.player.getInventory().getItem(i).has(net.minecraft.core.component.DataComponents.CUSTOM_DATA))
					f.player.getInventory().setItem(i, ItemStack.EMPTY);
		}).thenWaitUntil(() -> f.expect("inventory", 0)).thenExecute(f::close).thenSucceed();
	}

	@GameTest(maxTicks = 300)
	public void realFishingLootAndTrustedTransfers(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "fishing", """
				[{id:'fish',event:'fish_item',target:'minecraft:cod',count:10},
				 {id:'any',event:'questplus:fish_any',target:'questplus:fish',count:10},
				 {id:'fluid',event:'fluid_transfer',target:'minecraft:water',count:10000},
				 {id:'energy',event:'energy_transfer',target:'questplus:fe',count:10000}]
				""");
		try (f) {
			f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
			var empty = new FishingHook(f.player, helper.getLevel(), 0, 0);
			empty.retrieve(f.player.getMainHandItem());
			f.expect("fish", 0);
			f.expect("any", 0);
			var hook = new FishingHook(f.player, helper.getLevel(), 0, 0);
			// Only fast-forward the bite timer; retrieval, loot generation and mixins are real.
			var nibble = FishingHook.class.getDeclaredField("nibble");
			nibble.setAccessible(true);
			nibble.setInt(hook, 20);
			helper.getLevel().addFreshEntity(hook);
			hook.retrieve(f.player.getMainHandItem());
			f.expect("fish", 1);
			f.expect("any", 1);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					f.player.getBoundingBox().inflate(8)).stream().anyMatch(i -> i.getItem().is(Items.COD)),
					"Fishing actually spawned the catch");
			QuestPlusApi.reportFluidTransferred(f.player, "minecraft:lava", 1000);
			f.expect("fluid", 0);
			QuestPlusApi.reportFluidTransferred(f.player, "minecraft:water", 250);
			QuestPlusApi.reportFluidTransferred(f.player, "minecraft:water", 750);
			QuestPlusApi.reportEnergyTransferred(f.player, 1200);
			f.expect("fluid", 1000);
			f.expect("energy", 1200);
			helper.assertTrue(QuestPlusApi.getQuests(f.player).contains(f.quest), "Quest is discoverable");
			var definition = QuestPlusApi.getQuest(f.player, f.quest);
			definition.addProperty("id", "test:modified_copy");
			helper.assertTrue(QuestPlusApi.getQuest(f.player, f.quest).get("id").getAsString().equals(f.quest),
					"Definition is detached");
			helper.assertTrue(QuestPlusApi.getObjectiveProgress(f.player, f.quest, "energy") == 1200,
					"Progress reflects the authoritative counter");
			helper.assertTrue(QuestPlusApi.getProgress(f.player, f.quest).started(), "Progress is started");
			boolean rejected = false;
			try {
				QuestPlusApi.reportEnergyTransferred(f.player, 0);
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			helper.assertTrue(rejected, "No energy credit for unsuccessful transfer");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void deathResetsNoDeathAttemptAndGameplayModesAreFiltered(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "death", """
				[{id:'mine',event:'block_break',target:'minecraft:stone',count:10}]
				""");
		// This changes only the fixture quest, then goes through the same authoritative reload.
		var path = helper.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
				.resolve("questplus/quests/death-battery.json");
		var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
		json.addProperty("noDeath", true);
		java.nio.file.Files.writeString(path, json.toString());
		f.runtime.reload();
		try (f) {
			var pos = helper.absolutePos(new BlockPos(2, 2, 2));
			helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			f.player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			f.player.gameMode.destroyBlock(pos);
			f.expect("mine", 0);
			f.player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			f.player.gameMode.destroyBlock(pos);
			f.expect("mine", 1);
			f.player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 1000);
			helper.assertTrue(f.player.isDeadOrDying(), "Real Minecraft death occurred");
			f.expect("mine", 0);
		}
		helper.succeed();
	}
}
