package dev.questplus.fabric;

import dev.questplus.fabric.api.QuestPlusApi;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import team.reborn.energy.api.EnergyStorage;

public final class ResourceCompatibilityGameTest {
	private static ItemStack item(String id, int count) {
		return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(), count);
	}

	private static EnergyStorage energy(ServerPlayer player) {
		return Objects.requireNonNull(ContainerItemContext.ofPlayerHand(player, InteractionHand.MAIN_HAND)
				.find(EnergyStorage.ITEM), "Tech Reborn energy provider missing");
	}

	private static void charge(ServerPlayer player, long amount) {
		try (var tx = Transaction.openOuter()) {
			long filled = 0;
			for (int i = 0; i < 100 && filled < amount; i++)
				filled += energy(player).insert(amount - filled, tx);
			if (filled != amount)
				throw new AssertionError("Could not charge real Tech Reborn battery: " + filled);
			tx.commit();
		}
	}

	private static void rejected(Runnable action) {
		try {
			action.run();
		} catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError("Expected resource operation to be rejected");
	}

	private static Runnable submit(ObjectiveTestFixture f, String id) {
		return () -> {
			try {
				f.runtime.submitItem(f.player, f.quest, id);
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		};
	}

	@GameTest(maxTicks = 300)
	public void realCellsAndBatteriesPreserveExcessAndRejectInsufficientResources(GameTestHelper helper) throws Exception {
		try (var f = new ObjectiveTestFixture(helper, "tech_resources", """
				[{id:'oil',event:'fluid_submit',target:'techreborn:oil',count:1000},
				 {id:'half',event:'fluid_submit',target:'techreborn:oil',count:500},
				 {id:'gated',event:'energy_submit',target:'questplus:fe',count:100,conditions:{sneaking:true}},
				 {id:'energy',event:'energy_submit',target:'questplus:fe',count:500,data:{fePerEnergy:10}}]
				""")) {
			var player = f.player;
			player.setItemInHand(InteractionHand.MAIN_HAND, item("techreborn:red_cell_battery", 1));
			rejected(submit(f, "energy"));
			f.expect("energy", 0);
			charge(player, 100);
			helper.assertTrue(energy(player).getAmount() == 100, "Real battery charged via EnergyStorage");
			rejected(submit(f, "gated"));
			helper.assertTrue(energy(player).getAmount() == 100, "Failed objective condition consumes no battery energy");
			long limit;
			try (var tx = Transaction.openOuter()) { limit = energy(player).extract(50, tx); }
			helper.assertTrue(f.runtime.submitItem(player, f.quest, "energy") == limit * 10,
					"Submission respects the real battery's per-operation output limit");
			while (f.count("energy") < 500)
				f.runtime.submitItem(player, f.quest, "energy");
			helper.assertTrue(energy(player).getAmount() == 50, "Only 50 native energy consumed; excess preserved");
			rejected(submit(f, "energy"));
			helper.assertTrue(energy(player).getAmount() == 50, "Duplicate energy submission consumes nothing");
			player.getInventory().setItem(4, item("techreborn:water_cell", 2));
			rejected(submit(f, "oil"));
			player.setItemInHand(InteractionHand.OFF_HAND, item("techreborn:oil_cell", 1));
			rejected(submit(f, "half"));
			helper.assertTrue(player.getOffhandItem().is(item("techreborn:oil_cell", 1).getItem()),
					"Indivisible cell is preserved when only half a bucket is requested");
			helper.assertTrue(f.runtime.submitItem(player, f.quest, "oil") == 1000, "Modded fluid submitted from offhand");
			helper.assertTrue(player.getOffhandItem().is(item("techreborn:cell", 1).getItem()), "Empty Tech Reborn cell retained");
			helper.assertTrue(player.getInventory().getItem(4).getCount() == 2, "Wrong-fluid cells untouched");
			f.expect("oil", 1000);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void stackedCellsFullInventoryAndPartialProgressAreAtomic(GameTestHelper helper) throws Exception {
		try (var f = new ObjectiveTestFixture(helper, "tech_overflow", """
				[{id:'oil',event:'fluid_submit',target:'techreborn:oil',count:1500}]
				""")) {
			var player = f.player;
			for (int i = 0; i < 36; i++)
				player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 64));
			player.setItemInHand(InteractionHand.MAIN_HAND, item("techreborn:oil_cell", 3));
			rejected(submit(f, "oil"));
			f.expect("oil", 0);
			helper.assertTrue(player.getMainHandItem().getCount() == 3, "No cell lost without space for the empty container");
			player.getInventory().setItem(3, ItemStack.EMPTY);
			helper.assertTrue(f.runtime.submitItem(player, f.quest, "oil") == 1000, "Only one indivisible cell consumed");
			f.expect("oil", 1000);
			helper.assertTrue(player.getMainHandItem().getCount() == 2, "Two full cells remain");
			helper.assertTrue(player.getInventory().getItem(3).is(item("techreborn:cell", 1).getItem()), "Return cell inserted in free slot");
			rejected(submit(f, "oil"));
			f.expect("oil", 1000);
			helper.assertTrue(player.getMainHandItem().getCount() == 2, "Unfulfillable fractional remainder consumes nothing");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 300)
	public void realTankTransactionsCreditOnlyCommittedTransfers(GameTestHelper helper) throws Exception {
		try (var f = new ObjectiveTestFixture(helper, "tech_transfers", """
				[{id:'fluid',event:'fluid_transfer',target:'techreborn:oil',count:5000},
				 {id:'energy',event:'energy_transfer',target:'questplus:fe',count:5000}]
				""")) {
			var level = helper.getLevel();
			var pos = helper.absolutePos(new BlockPos(3, 1, 3));
			var tank = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("techreborn:basic_tank_unit")).orElseThrow();
			level.setBlockAndUpdate(pos, tank.defaultBlockState());
			var storage = Objects.requireNonNull(FluidStorage.SIDED.find(level, pos, Direction.UP), "Real tank provider missing");
			var oil = FluidVariant.of(BuiltInRegistries.FLUID.getOptional(Identifier.parse("techreborn:oil")).orElseThrow());
			f.player.setItemInHand(InteractionHand.MAIN_HAND, item("techreborn:oil_cell", 1));
			var source = ContainerItemContext.ofPlayerHand(f.player, InteractionHand.MAIN_HAND).find(FluidStorage.ITEM);
			try (var tx = Transaction.openOuter()) {
				helper.assertTrue(StorageUtil.move(source, storage, v -> v.equals(oil), FluidConstants.BUCKET, tx) == FluidConstants.BUCKET,
						"Actual fluid movement can be simulated");
			}
			f.expect("fluid", 0);
			helper.assertTrue(storage.iterator().next().getAmount() == 0, "Aborted transaction left tank empty");
			long moved;
			try (var tx = Transaction.openOuter()) {
				moved = StorageUtil.move(source, storage, v -> v.equals(oil), FluidConstants.BUCKET, tx);
				tx.commit();
			}
			helper.assertTrue(moved == FluidConstants.BUCKET, "Real cell emptied into actual Tech Reborn tank");
			QuestPlusApi.reportFluidTransferred(f.player, "techreborn:oil", moved * 1000 / FluidConstants.BUCKET);
			f.expect("fluid", 1000);
			f.player.setItemInHand(InteractionHand.MAIN_HAND, item("techreborn:red_cell_battery", 1));
			charge(f.player, 100);
			try (var tx = Transaction.openOuter()) { energy(f.player).extract(20, tx); }
			f.expect("energy", 0);
			helper.assertTrue(energy(f.player).getAmount() == 100, "Aborted battery extraction rolls back");
			long extracted;
			try (var tx = Transaction.openOuter()) {
				extracted = energy(f.player).extract(20, tx);
				tx.commit();
			}
			QuestPlusApi.reportEnergyTransferred(f.player, extracted);
			f.expect("energy", 20);
			helper.assertTrue(energy(f.player).getAmount() == 80, "Committed energy actually left the battery");
		}
		helper.succeed();
	}
}
