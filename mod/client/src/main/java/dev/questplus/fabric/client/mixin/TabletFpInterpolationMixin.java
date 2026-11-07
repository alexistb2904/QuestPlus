package dev.questplus.fabric.client.mixin;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationController;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.enums.State;
import dev.questplus.fabric.client.TabletFrameInterpolation;
import dev.questplus.fabric.client.TabletNativeArms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem", remap = false)
abstract class TabletFpInterpolationMixin {
	// FP PAL otherwise discards PAUSED hold_on_last_frame poses on the next render.
	@Inject(method = "lambda$init$1", at = @At("HEAD"), cancellable = true, remap = false)
	private static void questplus$retainHeldPose(AnimationController controller, AnimationData data,
			AnimationController.AnimationSetter setter, CallbackInfoReturnable<PlayState> ci) {
		if (Minecraft.getInstance().player instanceof IFpAnimatedPlayer player
				&& player.fpPal$getFpAnimationController() == controller
				&& controller instanceof FpAnimationController fp && fp.isRunning() && !fp.isStopRequested()
				&& !fp.isPendingPlay() && fp.getAnimationState() == State.PAUSED && TabletNativeArms.active())
			ci.setReturnValue(PlayState.CONTINUE);
	}

	@Inject(method = "updateRenderState", at = @At("TAIL"), remap = false)
	private static void questplus$capture(AbstractClientPlayer player, CallbackInfo ci) {
		TabletFrameInterpolation.capture(player);
	}
}
