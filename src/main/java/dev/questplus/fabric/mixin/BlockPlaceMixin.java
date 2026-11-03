package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean questplus$place(BlockItem item, BlockPlaceContext context, BlockState state, Operation<Boolean> original) {
        boolean placed = original.call(item, context, state);
        if (placed && context.getPlayer() instanceof ServerPlayer player) AdvancedEvents.block(player, "block_place", state);
        return placed;
    }
}
