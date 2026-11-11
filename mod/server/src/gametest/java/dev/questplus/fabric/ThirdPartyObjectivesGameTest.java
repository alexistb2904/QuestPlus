package dev.questplus.fabric;

import dev.questplus.compat.GameTestShims;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Only registered by -PobjectiveCompatibility. Missing mods fail instead of skipping. */
public final class ThirdPartyObjectivesGameTest {
	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
	}

	@GameTest(maxTicks = 300)
	public void farmersDelightBlocksAndItems(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "thirdparty_items", """
				[{id:'place',event:'block_place',target:'farmersdelight:oak_cabinet',count:10},
				 {id:'place_tag',event:'block_place',target:'#minecraft:mineable/axe',count:10},
				 {id:'break',event:'block_break',target:'farmersdelight:oak_cabinet',count:10},
				 {id:'interact',event:'block_interact',target:'farmersdelight:oak_cabinet',count:10},
				 {id:'pickup',event:'item_obtained',target:'farmersdelight:tomato',count:20},
				 {id:'craft',event:'item_craft',target:'farmersdelight:cooked_bacon',count:20},
				 {id:'smelt',event:'item_smelt',target:'farmersdelight:cooked_bacon',count:20},
				 {id:'eat',event:'item_consume',target:'farmersdelight:tomato',count:20},
				 {id:'inventory',event:'inventory_count',target:'farmersdelight:tomato',count:20}]
				""");
		try (f) {
			var player = f.player;
			var level = helper.getLevel();
			var pos = helper.absolutePos(new BlockPos(2, 1, 2));
			level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
			var cabinet = new ItemStack(item("farmersdelight:oak_cabinet"), 2);
			player.setItemInHand(InteractionHand.MAIN_HAND, cabinet);
			var result = ((BlockItem) cabinet.getItem()).place(new BlockPlaceContext(level, player,
					InteractionHand.MAIN_HAND, cabinet, hit));
			helper.assertTrue(result.consumesAction(), "Real modded cabinet placement succeeds");
			f.expect("place", 1);
			f.expect("place_tag", 1);
			var placed = pos.above();
			var cabinetHit = new BlockHitResult(Vec3.atCenterOf(placed), Direction.UP, placed, false);
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, cabinetHit);
			f.expect("interact", 1);
			player.closeContainer();
			helper.assertTrue(player.gameMode.destroyBlock(placed), "Real modded cabinet break succeeds");
			f.expect("break", 1);
			f.pickup(new ItemStack(item("farmersdelight:tomato"), 3));
			f.expect("pickup", 3);
			f.pickup(new ItemStack(Items.APPLE, 2));
			f.expect("pickup", 3);
			var output = new ResultContainer();
			output.setItem(0, new ItemStack(item("farmersdelight:cooked_bacon"), 4));
			var crafting = new TransientCraftingContainer(player.inventoryMenu, 2, 2);
			var slot = new ResultSlot(player, crafting, output, 0, 0, 0);
			var taken = slot.remove(4);
			slot.onTake(player, taken);
			f.expect("craft", 4);
			var furnace = new FurnaceResultSlot(player, new SimpleContainer(1), 0, 0, 0);
			furnace.onQuickCraft(ItemStack.EMPTY, new ItemStack(item("farmersdelight:cooked_bacon"), 3));
			furnace.onTake(player, ItemStack.EMPTY);
			f.expect("smelt", 3);
			new ItemStack(item("farmersdelight:tomato")).finishUsingItem(level, player);
			f.expect("eat", 1);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void moddedBlockAndEntityObservation(GameTestHelper helper) throws Exception {
		ObjectiveObservationGameTest.verify(helper, "farmersdelight:oak_cabinet", "graveyard:ghoul", "observe_modded");
	}

	@GameTest(maxTicks = 300)
	public void graveyardMobsWeaponsProjectilesAndMultiphaseBoss(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "thirdparty_combat", """
				[{id:'mob',event:'entity_kill',target:'graveyard:ghoul',count:10},
				 {id:'weapon',event:'entity_kill',target:'graveyard:ghoul',count:10,
				  conditions:{tool:'magistuarmory:iron_bastardsword'}},
				 {id:'boss',event:'entity_kill',target:'graveyard:lich',count:10}]
				""");
		try (f) {
			var ghoulType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("graveyard:ghoul")).orElseThrow();
			var ghoul = (LivingEntity) f.spawn(ghoulType);
			ghoul.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(f.player), 10000);
			f.expect("mob", 1);
			f.expect("weapon", 0);
			f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("magistuarmory:iron_bastardsword")));
			ghoul = (LivingEntity) f.spawn(ghoulType);
			ghoul.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(f.player), 10000);
			f.expect("mob", 2);
			f.expect("weapon", 1);
			var arrow = f.spawn(GameTestShims.ARROW);
			arrow.setOwner(f.player);
			ghoul = (LivingEntity) f.spawn(ghoulType);
			ghoul.hurtServer(helper.getLevel(), helper.getLevel().damageSources().arrow(arrow, f.player), 10000);
			f.expect("mob", 3);
			// Environmental deaths have no responsible player.
			ghoul = (LivingEntity) f.spawn(ghoulType);
			ghoul.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), 10000);
			f.expect("mob", 3);
			var boss = (LivingEntity) f.spawn(BuiltInRegistries.ENTITY_TYPE
					.getOptional(Identifier.parse("graveyard:lich")).orElseThrow());
			helper.assertTrue(boss.getMaxHealth() > 20, "Real third-party boss attributes are loaded");
			// Advance actual boss phases. Phase transitions must never be credited as deaths.
			for (int phase = 0; phase < 8 && !boss.isDeadOrDying(); phase++) {
				boss.getClass().getMethod("setInvulTimer", int.class).invoke(boss, 0);
				boss.getClass().getMethod("setPhaseInvulTimer", int.class).invoke(boss, 0);
				GameTestShims.clearInvulnerableTime(boss);
				boss.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(f.player), 100000);
				f.expect("boss", boss.isDeadOrDying() ? 1 : 0);
			}
			helper.assertTrue(boss.isDeadOrDying(), "Boss reached its real final death");
			f.expect("boss", 1);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 600)
	public void epicKnightsArmorHealthAndEnchantedEquipment(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "thirdparty_gear", """
				[{id:'gear',event:'equipment_check',target:'questplus:gear',count:100,
				  conditions:{and:[{healthAtLeast:60},{'questplus:gear':{items:[
				    {slot:'chest',item:'magistuarmory:knight_chestplate'},
				    {slot:'mainhand',item:'magistuarmory:iron_bastardsword',enchantments:{'minecraft:sharpness':3}}]}}]}},
				 {id:'inventory',event:'inventory_count',target:'farmersdelight:tomato',count:64}]
				""");
		var armor = new ItemStack(item("magistuarmory:knight_chestplate"));
		// Exercise modded armor with the standard component mechanism used by attribute-adding mods.
		armor.set(DataComponents.ATTRIBUTE_MODIFIERS,
				armor.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
						.withModifierAdded(Attributes.MAX_HEALTH,
								new AttributeModifier(Identifier.parse("questplus-test:compat_health"), 80,
										AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST));
		f.player.setItemSlot(EquipmentSlot.CHEST, armor);
		var sword = new ItemStack(item("magistuarmory:iron_bastardsword"));
		sword.enchant(f.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
		f.player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		f.player.getInventory().setItem(5, new ItemStack(item("farmersdelight:tomato"), 7));
		helper.startSequence().thenExecuteAfter(3, () -> {
			helper.assertTrue(f.player.getMaxHealth() == 100, "Modded armor grants 40 extra hearts through equipment ticks");
			f.player.setHealth(60);
		}).thenExecuteAfter(25, () -> {
			f.expect("inventory", 7);
			f.expect("gear", 0);
			sword.enchant(f.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.SHARPNESS), 3);
			f.player.setHealth(59);
		}).thenExecuteAfter(25, () -> {
			f.expect("gear", 0);
			f.player.setHealth(60);
		}).thenWaitUntil(() -> helper.assertTrue(f.count("gear") > 0,
				"Correct modded equipment, enchantment and current health advance objective"))
				.thenExecute(f::close).thenSucceed();
	}
}
