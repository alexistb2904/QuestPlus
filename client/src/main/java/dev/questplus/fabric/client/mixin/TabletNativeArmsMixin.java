package dev.questplus.fabric.client.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import dev.questplus.fabric.client.TabletNativeArms;
import dev.questplus.fabric.client.TabletShaderCompat;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil",remap=false)
abstract class TabletNativeArmsMixin {
    @Inject(method="renderArmForBone",at=@At("HEAD"),cancellable=true,remap=false)
    private static void questplus$render(PoseStack pose,PlayerAnimBone bone,float[] pivot,PlayerModel model,Identifier skin,int light,FpRenderBuffer buffer,CallbackInfo ci) {
        if (!TabletNativeArms.active()) return;
        if (!TabletShaderCompat.translucentHandPass()) TabletNativeArms.render(pose,bone,model,skin,light,buffer);
        ci.cancel();
    }
}
