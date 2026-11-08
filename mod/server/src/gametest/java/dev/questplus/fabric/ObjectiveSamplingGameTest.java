package dev.questplus.fabric;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;

public final class ObjectiveSamplingGameTest {
	@GameTest(maxTicks = 1000)
	public void inventoryXpStatsAdvancementsGearAndVisits(GameTestHelper helper) throws Exception {
		helper.setBiome(Biomes.DESERT);
		var f = new ObjectiveTestFixture(helper, "samples", """
				[{id:'inventory',event:'inventory_count',target:'#minecraft:logs',count:64},
				 {id:'levels',event:'xp_levels',target:'questplus:levels',count:100},
				 {id:'points',event:'xp_points',target:'questplus:points',count:10000},
				 {id:'stat',event:'custom_stat',target:'minecraft:jump',count:10},
				 {id:'advancement',event:'advancement_complete',target:'minecraft:story/mine_stone',count:1},
				 {id:'biome',event:'biome_visit',target:'minecraft:desert',count:10},
				 {id:'dimension',event:'dimension_enter',target:'minecraft:the_nether',count:10},
				 {id:'structure',event:'structure_visit',target:'minecraft:desert_pyramid',count:10},
				 {id:'travel',event:'travel_distance',target:'questplus:travel',count:10000},
				 {id:'location',event:'location',target:'questplus:position',count:1000},
				 {id:'gear',event:'equipment_check',target:'questplus:gear',count:100,
				  conditions:{'questplus:gear':{items:[
				    {slot:'chest',item:'minecraft:diamond_chestplate',enchantments:{'minecraft:protection':3}},
				    {slot:'offhand',item:'minecraft:shield'}]}}}]
				""");
		var player = f.player;
		var server = helper.getLevel().getServer();
		var at = player.blockPosition();
		var structure = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
				.getValue(Identifier.parse("minecraft:desert_pyramid"));
		var chunk = helper.getLevel().getChunkAt(at);
		var start = new StructureStart(structure, chunk.getPos(), 0,
				new PiecesContainer(List.of(new DesertPyramidPiece(RandomSource.create(42), at.getX(), at.getZ()))));
		chunk.setStartForStructure(structure, start);
		chunk.addReferenceForStructure(structure, chunk.getPos().pack());
		var box = start.getBoundingBox();
		player.setPos(box.minX() + 1.5, box.minY() + 1, box.minZ() + 1.5);
		// The pyramid can extend into a different chunk than its start.
		helper.getLevel().getChunkAt(player.blockPosition()).addReferenceForStructure(structure, chunk.getPos().pack());
		player.getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 3));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BIRCH_LOG, 2));
		player.giveExperienceLevels(5);
		player.awardStat(Stats.JUMP, 3);
		var advancement = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
		for (String criterion : advancement.value().criteria().keySet())
			player.getAdvancements().award(advancement, criterion);
		var armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
		armor.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.PROTECTION), 2);
		player.setItemSlot(EquipmentSlot.CHEST, armor);
		// Other integration fixtures reload the catalogue during startup; movement baselines reset on reload.
		helper.startSequence().thenExecuteAfter(250, () -> {}).thenWaitUntil(f::idle).thenWaitUntil(() -> {
			f.expect("inventory", 5);
			f.expect("levels", 5);
			f.expect("points", 55);
			f.expect("stat", 3);
			f.expect("advancement", 1);
			helper.assertTrue(f.count("structure") > 0, "Real structure manager detects the registered start");
			helper.assertTrue(f.count("biome") > 0, "Biome visit reads the world's biome");
			helper.assertTrue(f.count("location") > 0, "Location samples actual player position");
			f.expect("gear", 0);
		}).thenExecute(() -> {
			player.getInventory().setItem(0, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			player.giveExperienceLevels(-3);
			player.awardStat(Stats.JUMP, 2);
			for (String criterion : advancement.value().criteria().keySet())
				player.getAdvancements().revoke(advancement, criterion);
			armor.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.PROTECTION), 3);
			player.setOnGround(true);
			player.checkMovementStatistics(2, 0, 0);
		}).thenWaitUntil(() -> {
			f.expect("inventory", 0);
			f.expect("levels", 2);
			f.expect("points", 16);
			f.expect("stat", 5);
			f.expect("advancement", 0);
			f.expect("travel", 200);
			helper.assertTrue(f.count("gear") > 0, "Correct enchanted armor and offhand satisfy gear objective");
		}).thenWaitUntil(f::idle).thenExecute(() -> {
			player.teleportTo(server.getLevel(Level.NETHER), 0.5, 100, 0.5, Set.of(), 0, 0, true);
		}).thenWaitUntil(() -> f.expect("dimension", 1)).thenExecute(() -> {
			player.teleportTo(helper.getLevel(), at.getX() + .5, at.getY(), at.getZ() + .5, Set.of(), 0, 0, true);
		}).thenExecuteAfter(25, () -> f.expect("dimension", 1)).thenExecute(f::close).thenSucceed();
	}
}
