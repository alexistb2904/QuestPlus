package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletPoseClient;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class TabletPlayerPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void questplus$pose(AvatarRenderState state, CallbackInfo ci) {
        float progress=TabletPoseClient.progress(state);
        if (progress<=0 || state.isSpectator) return;
        var model=(PlayerModel)(Object)this;
        model.rightArm.xRot+=( -1.15f-model.rightArm.xRot)*progress; model.leftArm.xRot+=(-1.15f-model.leftArm.xRot)*progress;
        model.rightArm.yRot+=(-.2f-model.rightArm.yRot)*progress; model.leftArm.yRot+=(.2f-model.leftArm.yRot)*progress;
        model.rightArm.zRot+=(.15f-model.rightArm.zRot)*progress; model.leftArm.zRot+=(-.15f-model.leftArm.zRot)*progress;
        model.rightSleeve.loadPose(model.rightArm.storePose()); model.leftSleeve.loadPose(model.leftArm.storePose());
    }
}
