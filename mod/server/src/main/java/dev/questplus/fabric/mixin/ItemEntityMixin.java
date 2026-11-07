package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.questplus.fabric.QuestPlusMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean questplus$pickup(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
		int before = stack.getCount();
		ItemStack item = stack.copyWithCount(1);
		boolean result = original.call(inventory, stack);
		if (inventory.player instanceof ServerPlayer player)
			QuestPlusMod.pickedUp(player, item, before - stack.getCount());
		return result;
	}
}
