package dev.questplus.compat.mc26_1.mixin;

import dev.questplus.fabric.client.TabletSurfaceRenderer;
import dev.questplus.fabric.client.QuestPlusClient;
import dev.questplus.fabric.client.TabletShaderCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class TabletGameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void questplus$blurWorldBeforeHands(CallbackInfo ci) {
		var settings = QuestPlusClient.settings;
		if (TabletSurfaceRenderer.active() && settings.blurIntensity > 0 && TabletShaderCompat.backgroundBlurEnabled())
			Minecraft.getInstance().gameRenderer.processBlurEffect();
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
	private void questplus$renderSurface(CallbackInfo ci) {
		TabletSurfaceRenderer.render();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void questplus$closeSurface(CallbackInfo ci) {
		TabletSurfaceRenderer.close();
	}
}
