package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public abstract class InteractionMixin {
	@WrapOperation(method = "useItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult questplus$item(ItemStack item, Level level, Player player, InteractionHand hand,
			Operation<InteractionResult> original) {
		ItemStack before = item.copyWithCount(1);
		InteractionResult result = original.call(item, level, player, hand);
		if (result.consumesAction() && player instanceof ServerPlayer serverPlayer)
			AdvancedEvents.item(serverPlayer, "item_use", before, 1);
		return result;
	}

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult questplus$blockItem(BlockState state, ItemStack item, Level level, Player player,
			InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
		ItemStack before = item.copyWithCount(1);
		InteractionResult result = original.call(state, item, level, player, hand, hit);
		if (result.consumesAction() && player instanceof ServerPlayer serverPlayer) {
			AdvancedEvents.block(serverPlayer, "block_interact", state);
			if (result instanceof InteractionResult.Success success && success.wasItemInteraction())
				AdvancedEvents.item(serverPlayer, "item_use", before, 1);
		}
		return result;
	}

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;useWithoutItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult questplus$block(BlockState state, Level level, Player player, BlockHitResult hit,
			Operation<InteractionResult> original) {
		InteractionResult result = original.call(state, level, player, hit);
		if (result.consumesAction() && player instanceof ServerPlayer serverPlayer)
			AdvancedEvents.block(serverPlayer, "block_interact", state);
		return result;
	}

	@WrapOperation(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult questplus$itemOn(ItemStack item, UseOnContext context,
			Operation<InteractionResult> original) {
		ItemStack before = item.copyWithCount(1);
		InteractionResult result = original.call(item, context);
		if (result.consumesAction() && context.getPlayer() instanceof ServerPlayer player)
			AdvancedEvents.item(player, "item_use", before, 1);
		return result;
	}
}
