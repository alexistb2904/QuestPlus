package dev.questplus.fabric.mixin;

import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultMixin {
    @Shadow private int removeCount;
    @Shadow @Final private Player player;
    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void questplus$smelt(ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) AdvancedEvents.item(serverPlayer, "item_smelt", stack, removeCount);
    }
}

