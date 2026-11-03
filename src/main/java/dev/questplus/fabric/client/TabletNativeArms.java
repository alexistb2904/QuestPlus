package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Draws the player's real skin parts in the same bone frame as the Blockbench model. */
public final class TabletNativeArms {
    private static final float[] RIGHT=pivot("rightArm"), LEFT=pivot("leftArm");
    private TabletNativeArms() {}

    public static boolean active() {
        var player=Minecraft.getInstance().player;
        if (!(player instanceof IFpAnimatedPlayer animated)) return false;
        var controller=animated.fpPal$getFpAnimationController();
        var id=controller==null ? null : controller.getCurrentFpAnimation();
        return id!=null && id.getNamespace().equals("questplus")
            && (id.getPath().equals("questplus.open") || id.getPath().equals("questplus.idle") || id.getPath().equals("questplus.close"));
    }

    public static void render(PoseStack pose,PlayerAnimBone bone,PlayerModel model,Identifier skin,int light,FpRenderBuffer buffer) {
        boolean right=bone.getName().equals("rightArm");
        float[] pivot=right ? RIGHT : LEFT;
        ModelPart arm=right ? model.rightArm : model.leftArm;
        ModelPart sleeve=right ? model.rightSleeve : model.leftSleeve;
        pose.pushPose();
        try {
            FpArmRenderUtil.applyBoneTransform(pose,bone,pivot);
            pose.translate(pivot[0]/16f,pivot[1]/16f,pivot[2]/16f);
            // Vanilla player parts use the mirrored X/downward Y frame of AvatarRenderer.
            pose.scale(-1,-1,1);
            buffer.drawGeometry(pose,RenderTypes.entityCutout(skin),(matrix,vertices) -> {
                var armPose=arm.storePose(); var sleevePose=sleeve.storePose();
                try {
                    arm.resetPose(); arm.setPos(0,0,0); arm.setRotation(0,0,0);
                    sleeve.resetPose(); sleeve.setPos(0,0,0); sleeve.setRotation(0,0,0);
                    arm.render(matrix,vertices,light,OverlayTexture.NO_OVERLAY);
                    sleeve.render(matrix,vertices,light,OverlayTexture.NO_OVERLAY);
                } finally { arm.loadPose(armPose); sleeve.loadPose(sleevePose); }
            });
        } finally { pose.popPose(); }
    }

    private static float[] pivot(String name) {
        try (var stream=TabletNativeArms.class.getResourceAsStream("/assets/questplus/fp_models/questplus_first_person.geo.json")) {
            if (stream==null) throw new IllegalStateException("Missing first-person geometry");
            var bones=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            for (var element:bones) {
                var bone=element.getAsJsonObject();
                if (name.equals(bone.get("name").getAsString())) {
                    var p=bone.getAsJsonArray("pivot");
                    return new float[]{-p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()};
                }
            }
            throw new IllegalStateException("Missing first-person arm bone "+name);
        } catch (Exception error) { throw new IllegalStateException("Cannot read first-person arm pivot "+name,error); }
    }
}
