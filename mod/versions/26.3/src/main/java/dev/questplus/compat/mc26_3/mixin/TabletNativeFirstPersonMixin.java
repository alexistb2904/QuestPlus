package dev.questplus.compat.mc26_3.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.fabric.client.TabletNativeFirstPersonRenderer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QuestPlus owns only the first-person hand pass while its tablet is visible. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class TabletNativeFirstPersonMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void questplus$submitNativeHands(float partialTicks, PoseStack pose,
			SubmitNodeCollector collector, PlayerRenderState state,
			FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci) {
		if (state.avatarRenderState != null
				&& TabletNativeFirstPersonRenderer.submit(pose, collector, state.avatarRenderState.lightCoords))
			ci.cancel();
	}
}
