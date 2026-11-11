package dev.questplus.platform.neoforge.mixin;

import dev.questplus.platform.neoforge.QuestPlusNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric's PlayerBlockBreakEvents.AFTER: NeoForge's patched removeBlock reports whether the block was removed. */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	@Final
	protected ServerPlayer player;

	@Inject(method = "removeBlock", at = @At("RETURN"))
	private void questplus$afterBreak(BlockPos pos, BlockState state, boolean canHarvest, ItemStack tool,
			CallbackInfoReturnable<Boolean> removed) {
		if (removed.getReturnValueZ()) QuestPlusNeoForge.afterBreak(player, pos, state);
	}
}
