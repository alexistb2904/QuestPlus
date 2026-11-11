package dev.questplus.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The table identifier is an ordinary persistent custom-data component, usable
 * by vanilla loot tables.
 */
final class QuestCrateItem extends Item {
	private QuestCrateItem(Properties properties) {
		super(properties);
	}

	static void register() {
		var key = ResourceKey.create(Registries.ITEM, Identifier.parse("questplus:loot_crate"));
		Registry.register(BuiltInRegistries.ITEM, key,
				new QuestCrateItem(new Item.Properties().setId(key).stacksTo(64)));
	}

	/**
	 * On a block: stage the reveal chest. Anywhere it cannot stand (or in
	 * creative) PASS falls back to {@link #use}, the instant open.
	 */
	@Override
	public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
		var level = context.getLevel();
		var player = context.getPlayer();
		var pos = QuestLootChest.target(level, context.getClickedPos(), context.getClickedFace());
		if (player == null || player.isCreative() || player.isSpectator() || pos == null
				|| !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
			return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer actor)
				|| !(level instanceof net.minecraft.server.level.ServerLevel serverLevel))
			return InteractionResult.SUCCESS;
		try {
			var runtime = QuestPlusMod.runtime();
			var crate = runtime.openCrate(actor, context.getHand(), QuestLootChest.DELIVER);
			if (!QuestLootChest.place(serverLevel, pos, actor, crate))
				runtime.releaseCrateHold(crate.key());
		} catch (Exception error) {
			actor.sendSystemMessage(net.minecraft.network.chat.Component.literal("QuestPlus: " + error.getMessage()));
			return InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer actor) {
			try {
				QuestPlusMod.runtime().openCrate(actor, hand);
			} catch (Exception error) {
				actor.sendSystemMessage(
						net.minecraft.network.chat.Component.literal("QuestPlus: " + error.getMessage()));
				return InteractionResult.FAIL;
			}
		}
		return InteractionResult.SUCCESS;
	}
}
