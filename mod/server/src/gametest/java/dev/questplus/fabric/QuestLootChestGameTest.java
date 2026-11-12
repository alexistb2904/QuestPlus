package dev.questplus.fabric;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import dev.questplus.compat.GameTestShims;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import java.nio.file.*;

public final class QuestLootChestGameTest {
	/** crateBosses adds entity IDs or tags as bosses, "!" removes even built-in ones, unknown IDs reject the reload. */
	@GameTest(maxTicks = 100)
	public void crateBossesAreConfigurableAndValidated(GameTestHelper h) throws Exception {
		var rt = QuestPlusMod.runtime();
		var config = h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("questplus/config.json");
		String original = Files.readString(config);
		var spawned = new java.util.ArrayList<LivingEntity>();
		java.util.function.Function<EntityType<? extends LivingEntity>, String> kind = type -> {
			var entity = h.spawn(type, new BlockPos(1, 2, 1));
			spawned.add(entity);
			return rt.crateKind(entity);
		};
		try {
			h.assertTrue(kind.apply(GameTestShims.WARDEN).equals("monster")
					&& kind.apply(GameTestShims.WITHER).equals("boss"), "Absent key keeps built-in bosses");
			var custom = JsonParser.parseString(original).getAsJsonObject();
			custom.add("crateBosses", JsonParser.parseString("['minecraft:warden','#minecraft:skeletons','!minecraft:wither']"));
			Files.writeString(config, custom.toString());
			rt.reload();
			h.assertTrue(kind.apply(GameTestShims.WARDEN).equals("boss"), "Listed entity is a boss");
			h.assertTrue(kind.apply(GameTestShims.SKELETON).equals("boss"), "Listed tag is a boss");
			h.assertTrue(kind.apply(GameTestShims.WITHER).equals("monster"), "Excluded built-in boss");
			h.assertTrue(kind.apply(GameTestShims.ZOMBIE).equals("monster")
					&& kind.apply(GameTestShims.COW).equals("passive"), "Others unchanged");
			for (String bad : new String[] { "['questplus:not_a_mob']", "['#questplus:no_such_tag']", "'minecraft:warden'", "[1]" }) {
				custom.add("crateBosses", JsonParser.parseString(bad));
				Files.writeString(config, custom.toString());
				boolean rejected = false;
				try {
					rt.reload();
				} catch (IllegalArgumentException expected) {
					rejected = true;
				}
				h.assertTrue(rejected && kind.apply(GameTestShims.WARDEN).equals("boss"),
						"Invalid crateBosses " + bad + " is rejected and keeps the previous list");
			}
		} finally {
			Files.writeString(config, original);
			rt.reload();
			spawned.forEach(net.minecraft.world.entity.Entity::discard);
		}
		h.succeed();
	}

	/** A full inventory blocks a claim only with waitForInventorySpace. Otherwise the overflow drops on the ground. */
	@GameTest(maxTicks = 100)
	public void fullInventoryDropsUnlessWaitingIsEnabled(GameTestHelper h) throws Exception {
		var rt = QuestPlusMod.runtime();
		var config = h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("questplus/config.json");
		String original = Files.readString(config);
		try {
			var custom = JsonParser.parseString(original).getAsJsonObject();
			custom.addProperty("waitForInventorySpace", "yes");
			Files.writeString(config, custom.toString());
			boolean rejected = false;
			try {
				rt.reload();
			} catch (IllegalArgumentException expected) {
				rejected = true;
			}
			h.assertTrue(rejected, "A non-boolean waitForInventorySpace rejects the reload");
			custom.addProperty("waitForInventorySpace", true);
			Files.writeString(config, custom.toString());
			rt.reload();
		} finally {
			Files.writeString(config, original);
			rt.reload();
		}
		var actor = QuestWorldBlocksGameTest.player(h);
		var rewards = dev.questplus.core.QuestRegistry.rewards(
				JsonParser.parseString("[{type:'item',item:'minecraft:diamond',count:1}]").getAsJsonArray(), (k, id) -> {
				});
		h.assertTrue(QuestRuntime.fits(actor, rewards, false), "Empty inventory fits");
		for (int i = 0; i < 36; i++)
			actor.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		h.assertTrue(!QuestRuntime.fits(actor, rewards, false), "Waiting mode: a full inventory blocks the claim");
		h.assertTrue(QuestRuntime.fits(actor, rewards, true), "Default mode: the overflow drops instead");
		h.getLevel().getServer().getPlayerList().remove(actor);
		rt.disconnect(actor.getUUID());
		h.succeed();
	}

	/** The reveal is cosmetic: the crate is consumed and saved first, delivery waits for the show, the stage cleans up. */
	@GameTest(maxTicks = 600)
	public void stagedCrateIsSavedFirstDeliveredAfterRevealAndCleanedUp(GameTestHelper h) throws Exception {
		var rt = QuestPlusMod.runtime();
		var server = h.getLevel().getServer();
		var metadata = server.getWorldPath(LevelResource.ROOT).resolve("questplus/queststudio-compatibility.json");
		var library = Files.exists(metadata) ? JsonParser.parseString(Files.readString(metadata)).getAsJsonObject()
				: new JsonObject();
		if (!library.has("rewardTables"))
			library.add("rewardTables", new JsonArray());
		library.getAsJsonArray("rewardTables").add(JsonParser.parseString(
				"{name:'stage_crate',crate:true,reward:{type:'questplus:all',data:{options:[{type:'item',item:'minecraft:diamond',count:2,weight:1}]}}}"));
		Files.writeString(metadata, library.toString());
		rt.reload();
		var actor = QuestWorldBlocksGameTest.player(h);
		var level = h.getLevel();
		var floor = h.absolutePos(new BlockPos(1, 1, 1));
		level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
		var crate = QuestItemStacks.decode(JsonParser.parseString(
				"{id:'questplus:loot_crate',components:{'minecraft:custom_data':{questplus_table:'stage_crate'}}}")
				.getAsJsonObject(), server.registryAccess());
		crate.setCount(2);
		actor.setItemInHand(InteractionHand.MAIN_HAND, crate);
		var use = (java.util.function.Function<BlockPos, InteractionResult>) on -> crate.useOn(new UseOnContext(actor,
				InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(on), Direction.UP, on, false)));
		var occupied = h.absolutePos(new BlockPos(3, 1, 1));
		level.setBlock(occupied, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(occupied.above(), Blocks.STONE.defaultBlockState(), 3);
		var chest = floor.above();
		h.startSequence().thenWaitUntil(() -> h.assertTrue(!rt.isBusy(actor.getUUID()), "Await player save"))
				.thenExecute(() -> {
			h.assertTrue(use.apply(occupied) == InteractionResult.PASS && crate.getCount() == 2,
					"No room: falls back to instant open without consuming");
			var result = use.apply(floor);
			h.assertTrue(result instanceof InteractionResult.Success, "Crate staged on the floor: " + result);
			h.assertTrue(level.getBlockState(chest).is(QuestLootChest.BLOCK), "Chest placed");
			h.assertTrue(actor.getMainHandItem().getCount() == 1, "Exactly one crate consumed");
			var stage = (QuestLootChest.Stage) level.getBlockEntity(chest);
			h.assertTrue(stage.items.size() == 1 && stage.items.getFirst().is(Items.DIAMOND)
					&& stage.items.getFirst().getCount() == 2, "Stage shows the server-rolled loot");
			try {
				h.assertTrue(rt.player(actor.getUUID()).entitlements.values().stream()
						.anyMatch(g -> g.questId().equals("questplus:crate")), "Rewards durably entitled before the show");
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}).thenExecuteAfter(QuestLootChest.OPEN, () -> h.assertTrue(
				actor.getInventory().countItem(Items.DIAMOND) == 0, "Delivery waits for the reveal"))
				.thenWaitUntil(() -> h.assertTrue(actor.getInventory().countItem(Items.DIAMOND) == 2,
						"Delivered once after the reveal"))
				.thenWaitUntil(() -> h.assertTrue(level.getBlockState(chest).isAir(), "Stage removes itself"))
				.thenExecute(() -> {
					h.assertTrue(actor.getInventory().countItem(Items.DIAMOND) == 2, "No duplicate delivery");
					try {
						// Shared with tests running in parallel: drop only our table, never the file.
						var current = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
						current.getAsJsonArray("rewardTables").asList()
								.removeIf(t -> t.getAsJsonObject().get("name").getAsString().equals("stage_crate"));
						Files.writeString(metadata, current.toString());
						rt.reload();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					server.getPlayerList().remove(actor);
					rt.disconnect(actor.getUUID());
				}).thenSucceed();
	}
}
