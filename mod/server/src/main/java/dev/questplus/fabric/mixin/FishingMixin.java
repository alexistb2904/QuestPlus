package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingHook.class)
public abstract class FishingMixin {
	@WrapOperation(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;pullEntity(Lnet/minecraft/world/entity/Entity;)V"))
	private void questplus$hook(FishingHook hook, Entity entity, Operation<Void> original) {
		original.call(hook, entity);
		if (hook.getPlayerOwner() instanceof ServerPlayer player)
			AdvancedEvents.entity(player, "fish_entity", entity);
	}

	@WrapOperation(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean questplus$loot(Level level, Entity entity, Operation<Boolean> original) {
		boolean added = original.call(level, entity);
		if (added && entity instanceof ItemEntity item
				&& ((FishingHook) (Object) this).getPlayerOwner() instanceof ServerPlayer player)
			AdvancedEvents.item(player, "fish_item", item.getItem(), item.getItem().getCount());
		return added;
	}
}
