package dev.questplus.compat.mc26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.fabric.client.TabletNativeFirstPersonRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QuestPlus owns only the first-person hand pass while its tablet is visible. */
@Mixin(ItemInHandRenderer.class)
abstract class TabletNativeFirstPersonMixin {
	@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void questplus$submitNativeHands(float partialTicks, PoseStack pose, SubmitNodeCollector collector,
			LocalPlayer player, int light, CallbackInfo ci) {
		if (TabletNativeFirstPersonRenderer.submit(pose, collector, light))
			ci.cancel();
	}
}
