package dev.questplus.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;

public final class AdvancedEventsTest {
	@GameTest(maxTicks = 100)
	public void hooks(GameTestHelper helper) throws Exception {
		var level = helper.getLevel();
		var runtime = QuestPlusMod.runtime();
		var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "advanced-test"),
				ClientInformation.createDefault());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.setGameMode(GameType.SURVIVAL);
		var file = level.getServer().getWorldPath(LevelResource.ROOT)
				.resolve("questplus/quests/advanced-integration.json");
		Files.writeString(file,
				"""
						{"schemaVersion":1,"id":"questplus:advanced_test","category":"%s","icon":"minecraft:stone",
						 "title":{"en_us":"Test"},"description":{"en_us":"Test"},"progression":"once","rewards":[],
						 "objectives":[
						   {"id":"place","event":"block_place","target":"minecraft:stone","count":2},
						   {"id":"craft","event":"item_craft","target":"minecraft:oak_planks","count":8},
						   {"id":"smelt","event":"item_smelt","target":"minecraft:iron_ingot","count":8},
						   {"id":"consume","event":"item_consume","target":"minecraft:apple","count":2},
						   {"id":"interact","event":"block_interact","target":"minecraft:lever","count":2},
						   {"id":"tame","event":"tame_animal","target":"minecraft:wolf","count":2},
						   {"id":"breed","event":"breed_animal","target":"minecraft:cow","count":2},
						   {"id":"trade","event":"trade","target":"minecraft:emerald","count":2},
						   {"id":"fish","event":"fish_entity","target":"minecraft:cow","count":2},
						   {"id":"use","event":"item_use","target":"minecraft:fishing_rod","count":2},
						   {"id":"npc","event":"npc_interact","target":"minecraft:wolf","count":2},
						   {"id":"dimension","event":"dimension_enter","target":"minecraft:overworld","count":2},
						   {"id":"distance","event":"travel_distance","target":"questplus:travel","count":1000},
						   {"id":"location","event":"location","target":"questplus:position","count":3},
						   {"id":"inventory","event":"inventory_count","target":"minecraft:stone","count":10}]}
						""".formatted(runtime.categories().getFirst().id()));
		runtime.reload();
		var progress = runtime.player(player.getUUID());
		BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(base, Blocks.COBBLESTONE.defaultBlockState());
		var hit = new BlockHitResult(Vec3.atCenterOf(base), Direction.UP, base, false);
		var stack = new ItemStack(Items.STONE, 2);
		var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit);
		((net.minecraft.world.item.BlockItem) Items.STONE).place(context);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("place") == 1,
				"Successful place counted once");
		var blocked = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, new ItemStack(Items.STONE), hit) {
			@Override
			public boolean canPlace() {
				return false;
			}
		};
		((net.minecraft.world.item.BlockItem) Items.STONE).place(blocked);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("place") == 1,
				"Failed placement not counted");
		var output = new ResultContainer();
		output.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		var crafting = new TransientCraftingContainer(player.inventoryMenu, 2, 2);
		var slot = new ResultSlot(player, crafting, output, 0, 0, 0);
		ItemStack taken = slot.remove(4);
		slot.onTake(player, taken);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("craft") == 4,
				"Craft counts extracted output");
		slot.onQuickCraft(ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS, 4));
		slot.onTake(player, ItemStack.EMPTY);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("craft") == 8,
				"Quick craft counts output once");
		var furnace = new FurnaceResultSlot(player, new SimpleContainer(1), 0, 0, 0);
		furnace.onQuickCraft(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT, 4));
		furnace.onTake(player, ItemStack.EMPTY);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("smelt") == 4,
				"Quick smelt counts output once");
		new ItemStack(Items.APPLE).finishUsingItem(level, player);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("consume") == 1,
				"Finished item use counted once");
		BlockPos chest = helper.absolutePos(new BlockPos(4, 1, 2));
		level.setBlockAndUpdate(chest, Blocks.LEVER.defaultBlockState());
		var chestHit = new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, chestHit);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("interact") == 1,
				"Successful lever interaction counted");
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("interact") == 1,
				"Non-interactive block does not count");
		var wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(5, 2, 2));
		wolf.tame(player);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("tame") == 1,
				"Successful tame counted");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.interactOn(wolf, InteractionHand.MAIN_HAND, Vec3.ZERO);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("npc") == 1,
				"Successful entity interaction counted");
		var cow = helper.spawn(EntityTypes.COW, new BlockPos(6, 2, 2));
		var mate = helper.spawn(EntityTypes.COW, new BlockPos(7, 2, 2));
		cow.setInLove(player);
		mate.setInLove(player);
		cow.spawnChildFromBreeding(level, mate);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("breed") == 1,
				"Successful breeding counted once");
		var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(8, 2, 2));
		villager.setTradingPlayer(player);
		villager.getOffers().clear();
		villager.getOffers().add(new MerchantOffer(new ItemCost(Items.WHEAT), new ItemStack(Items.EMERALD), 10, 1, 0));
		var merchant = new MerchantContainer(villager);
		merchant.setItem(0, new ItemStack(Items.WHEAT));
		var tradeSlot = new MerchantResultSlot(player, villager, merchant, 2, 0, 0);
		tradeSlot.onTake(player, ItemStack.EMPTY);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("trade") == 1,
				"Trade uses offer result even after quick transfer");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
		var hook = new FishingHook(player, level, 0, 0);
		var setHooked = FishingHook.class.getDeclaredMethod("setHookedEntity", Entity.class);
		setHooked.setAccessible(true);
		setHooked.invoke(hook, cow);
		hook.retrieve(player.getMainHandItem());
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("fish") == 1,
				"Retrieving hooked entity counted once");
		player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("use") == 1,
				"Successful item use counted");
		player.gameMode.useItem(player, level, new ItemStack(Items.COBBLESTONE), InteractionHand.MAIN_HAND);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("use") == 1,
				"Unused item does not count");
		var sampleClass = Class.forName("dev.questplus.fabric.AdvancedEvents$Sample");
		var constructor = sampleClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		var sample = constructor.newInstance();
		var sampleMethod = AdvancedEvents.class.getDeclaredMethod("sample", ServerPlayer.class, sampleClass);
		sampleMethod.setAccessible(true);
		sampleMethod.invoke(null, player, sample);
		player.setPos(player.getX() + 1, player.getY(), player.getZ());
		sampleMethod.invoke(null, player, sample);
		helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.get("dimension") == 1,
				"Dimension is counted only on entry");
		helper.assertTrue(!progress.quests.get("questplus:advanced_test").counters.containsKey("distance"),
				"Position change alone is not travel");
		player.setOnGround(true);
		player.checkMovementStatistics(1, 0, 0);
		sampleMethod.invoke(null, player, sample);
		helper.startSequence().thenWaitUntil(() -> {
			helper.assertTrue(
					progress.quests.get("questplus:advanced_test").counters.getOrDefault("distance", 0L) == 100,
					"One block records 100 centimeters after queued events");
			helper.assertTrue(progress.quests.get("questplus:advanced_test").counters.getOrDefault("location", 0L) >= 3,
					"Location sampled at least three times");
		}).thenExecute(() -> {
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
			var snapshot = AdvancedEvents.context(player);
			var inventory = (java.util.Map<?, ?>) snapshot.get("inventory");
			helper.assertTrue(Long.valueOf(3).equals(inventory.get("minecraft:stone")),
					"Inventory context counts actual items");
			try {
				Files.delete(file);
				runtime.reload();
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}).thenSucceed();
	}
}
