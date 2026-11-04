package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class ItemConsumeMixin {
    @WrapMethod(method = "finishUsingItem")
    private ItemStack questplus$consume(Level level, LivingEntity user, Operation<ItemStack> original) {
        ItemStack before = ((ItemStack)(Object)this).copyWithCount(1);
        ItemStack result = original.call(level, user);
        if (user instanceof ServerPlayer player) AdvancedEvents.item(player, "item_consume", before, 1);
        return result;
    }
}
