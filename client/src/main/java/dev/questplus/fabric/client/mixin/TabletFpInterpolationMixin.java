package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletFrameInterpolation;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem",remap=false)
abstract class TabletFpInterpolationMixin {
    @Inject(method="updateRenderState",at=@At("TAIL"),remap=false)
    private static void questplus$capture(AbstractClientPlayer player,CallbackInfo ci) {
        TabletFrameInterpolation.capture(player);
    }
}
