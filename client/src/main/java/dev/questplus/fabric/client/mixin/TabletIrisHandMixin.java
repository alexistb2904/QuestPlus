package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletNativeArms;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Even two translucent held items must leave a solid pass for FP PAL's opaque tablet. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pathways.HandRenderer",remap=false)
abstract class TabletIrisHandMixin {
    @Inject(method="isHandTranslucent",at=@At("HEAD"),cancellable=true,remap=false)
    private void questplus$opaqueTablet(CallbackInfoReturnable<Boolean> cir) {
        if (FabricLoader.getInstance().isModLoaded("fp_pal") && TabletNativeArms.active()) cir.setReturnValue(false);
    }
}
