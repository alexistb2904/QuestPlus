package dev.questplus.fabric;

import com.google.gson.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

public final class ObjectiveConditionMatrixGameTest {
	private static final String[] IDS = { "and", "or", "not", "quorum", "high", "low", "effect", "equipment",
			"gear", "position", "context", "stage", "dimension" };

	@GameTest(maxTicks = 400)
	public void booleanTreesHealthArmorEffectsSlotsAndPosition(GameTestHelper helper) throws Exception {
		var conditions = JsonParser.parseString("""
				[{and:[{tool:'minecraft:diamond_sword'},{sneaking:true}]},
				 {or:[{tool:'minecraft:diamond_sword'},{effect:'minecraft:strength'}]},
				 {not:{sneaking:true}},
				 {at_least:{count:2,of:[{tool:'minecraft:diamond_sword'},{sneaking:true},{effect:'minecraft:strength'}]}},
				 {healthAtLeast:60},{healthAtMost:10},{effect:'minecraft:strength'},
				 {equipment:{slot:'chest',item:'minecraft:diamond_chestplate'}},
				 {'questplus:gear':{items:[{slot:'mainhand',item:'minecraft:diamond_sword',enchantments:{'minecraft:sharpness':2}},
				   {slot:'offhand',item:'minecraft:shield'}]}},
				 {position:{xMin:0,xMax:0}},
				 {context:{key:'sneaking',equals:true}},{'questplus:stage':'questplus:matrix'},{dimension:'minecraft:overworld'}]
				""").getAsJsonArray();
		var at = helper.absolutePos(new BlockPos(1, 2, 1));
		conditions.get(9).getAsJsonObject().add("position", JsonParser.parseString(
				"{xMin:" + at.getX() + ",xMax:" + (at.getX() + 1) + "}"));
		var goals = new JsonArray();
		for (int i = 0; i < IDS.length; i++) {
			var goal = new JsonObject();
			goal.addProperty("id", IDS[i]);
			goal.addProperty("event", "block_break");
			goal.addProperty("target", "minecraft:stone");
			goal.addProperty("count", 99);
			goal.add("conditions", conditions.get(i));
			goals.add(goal);
		}
		var f = new ObjectiveTestFixture(helper, "condition_matrix", goals.toString());
		var player = f.player;
		breakAndExpect(f, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1);
		var armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
		var modifiers = armor.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
				.withModifierAdded(Attributes.MAX_HEALTH,
						new AttributeModifier(Identifier.parse("questplus-test:hearts"), 80, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.CHEST)
				.withModifierAdded(Attributes.ARMOR,
						new AttributeModifier(Identifier.parse("questplus-test:armor"), 40, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.CHEST);
		armor.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
		player.setItemSlot(EquipmentSlot.CHEST, armor);
		var sword = new ItemStack(Items.DIAMOND_SWORD);
		sword.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
		player.setItemSlot(EquipmentSlot.MAINHAND, sword);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200));
		player.setShiftKeyDown(true);
		QuestStages.set(player, "questplus:matrix", true);
		helper.startSequence().thenExecuteAfter(3, () -> {}).thenWaitUntil(f::idle).thenExecute(() -> {
			helper.assertTrue(player.getMaxHealth() == 100, "Equipped item adds 40 hearts through real attribute application");
			helper.assertTrue(player.getArmorValue() == 30, "Equipment armor modifiers respect Minecraft's effective attribute cap");
			player.setHealth(59);
			// Max health alone is insufficient: this action must still reject the 30-heart threshold.
			breakAndExpect(f, 1, 1, 1, 1, 0, 0, 1, 1, 1, 2, 1, 1, 2);
			player.setHealth(60);
			breakAndExpect(f, 2, 2, 1, 2, 1, 0, 2, 2, 2, 3, 2, 2, 3);
			player.setHealth(10);
			player.removeAllEffects();
			player.setShiftKeyDown(false);
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			QuestStages.set(player, "questplus:matrix", false);
			player.setPos(player.getX() + 2, player.getY(), player.getZ());
		}).thenExecuteAfter(3, () -> {}).thenWaitUntil(f::idle).thenExecute(() -> {
			helper.assertTrue(player.getMaxHealth() == 20, "Removing the armor removes its health modifier");
			breakAndExpect(f, 2, 3, 2, 2, 1, 1, 2, 2, 2, 3, 2, 2, 4);
		}).thenExecute(f::close).thenSucceed();
	}

	private static void breakAndExpect(ObjectiveTestFixture f, long... expected) {
		var pos = f.helper.absolutePos(new BlockPos(2, 2, 2));
		f.helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		f.helper.assertTrue(f.player.gameMode.destroyBlock(pos), "Real block break for condition matrix");
		for (int i = 0; i < IDS.length; i++)
			f.expect(IDS[i], expected[i]);
	}
}
