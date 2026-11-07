package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletNativeArms;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AvatarRenderer.class, priority = 1200)
abstract class TabletVanillaHandsMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void questplus$hideVanillaHand(CallbackInfo ci) {
		if (TabletNativeArms.active())
			ci.cancel();
	}
}
