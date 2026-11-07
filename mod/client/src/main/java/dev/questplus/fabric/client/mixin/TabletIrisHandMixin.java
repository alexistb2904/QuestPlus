package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletNativeArms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
abstract class TabletIrisHandMixin {
	@Inject(method = "isHandTranslucent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void questplus$opaqueTablet(CallbackInfoReturnable<Boolean> cir) {
		if (TabletNativeArms.active()) cir.setReturnValue(false);
	}
}
