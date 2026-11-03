package dev.questplus.fabric.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.fabric.client.TabletFaceAnchor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FP PAL first-person item model pose, captured BEFORE it submits its geometry. */
@Pseudo
@Mixin(targets="com.github.razorplay01.fp_pal.firstperson.FpModelRenderer",remap=false)
abstract class TabletFaceCaptureMixin {
    @Inject(method="render",at=@At("HEAD"),remap=false,require=0)
    private static void questplus$capture(PoseStack pose,Object buffer,int light,CallbackInfo ci) {
        TabletFaceAnchor.capture(pose);
    }
}
