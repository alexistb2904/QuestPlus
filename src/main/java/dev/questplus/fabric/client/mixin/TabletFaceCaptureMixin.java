package dev.questplus.fabric.client.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpGeoModel;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.questplus.fabric.client.TabletFaceAnchor;
import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.github.razorplay01.fp_pal.firstperson.FpModelRenderer",remap=false)
abstract class TabletFaceCaptureMixin {
    @Shadow private static Identifier modelId;
    @Unique private static final Identifier QUESTPLUS_MODEL=Identifier.fromNamespaceAndPath("questplus","questplus_first_person");
    private static int questplus$pass;

    @Unique private static boolean questplus$usesAtlas() {
        return TabletSurfaceRenderer.active() && QUESTPLUS_MODEL.equals(modelId);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpRenderBuffer;drawGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/github/razorplay01/fp_pal/firstperson/FpRenderBuffer$GeometryRenderer;)V"),remap=false)
    private static void questplus$surfacePass(FpRenderBuffer buffer,PoseStack pose,RenderType type,FpRenderBuffer.GeometryRenderer geometry) {
        if (!questplus$usesAtlas()) { buffer.drawGeometry(pose,type,geometry);return; }
        buffer.drawGeometry(pose,type,(p,c) -> {
            questplus$pass=1;
            try { geometry.render(p,c); } finally { questplus$pass=0; }
        });
        buffer.drawGeometry(pose,RenderTypes.entityTranslucentEmissive(TabletSurfaceRenderer.TEXTURE),(p,c) -> {
            questplus$pass=2;
            try { geometry.render(p,c); } finally { questplus$pass=0; }
        });
    }

    @Inject(method="renderCube",at=@At("HEAD"),cancellable=true,remap=false)
    private static void questplus$onlyOriginalSurface(PoseStack pose,VertexConsumer consumer,FpGeoModel model,FpGeoModel.Cube cube,int light,CallbackInfo ci) {
        var screen=model.bone("screen");
        boolean surface=screen!=null && screen.cubes().contains(cube);
        if (questplus$pass==1 && surface || questplus$pass==2 && !surface) ci.cancel();
    }
    @Inject(method="renderBone",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"),remap=false)
    private static void questplus$capture(PoseStack pose,VertexConsumer consumer,FpGeoModel model,FpGeoModel.Bone bone,int light,boolean root,CallbackInfo ci) {
        if (questplus$usesAtlas()) TabletFaceAnchor.capture(pose,bone);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),remap=false)
    private static RenderType questplus$texture(Identifier original) {
        return RenderTypes.entityCutout(questplus$usesAtlas() ? TabletSurfaceRenderer.TEXTURE : original);
    }

    @Redirect(method="renderCube",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpGeoModel;textureWidth()I"),remap=false)
    private static int questplus$width(FpGeoModel model) {
        return questplus$usesAtlas() ? TabletSurfaceRenderer.ATLAS_WIDTH : model.textureWidth();
    }
    @Redirect(method="renderCube",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpGeoModel;textureHeight()I"),remap=false)
    private static int questplus$height(FpGeoModel model) {
        return questplus$usesAtlas() ? TabletSurfaceRenderer.ATLAS_HEIGHT : model.textureHeight();
    }
    @Inject(method="uv",at=@At("HEAD"),cancellable=true,remap=false)
    private static void questplus$screenUv(FpGeoModel.Cube cube,FpGeoModel model,int face,CallbackInfoReturnable<float[]> cir) {
        var screen=model.bone("screen");
        if (questplus$usesAtlas() && face==FpGeoModel.FACE_NORTH && screen!=null && screen.cubes().contains(cube))
            cir.setReturnValue(new float[]{TabletSurfaceRenderer.SCREEN_X+TabletSurfaceRenderer.WIDTH,TabletSurfaceRenderer.ATLAS_HEIGHT-TabletSurfaceRenderer.HEIGHT,-TabletSurfaceRenderer.WIDTH,TabletSurfaceRenderer.HEIGHT});
    }
    @Inject(method="uv",at=@At("RETURN"),cancellable=true,remap=false)
    private static void questplus$atlasUv(FpGeoModel.Cube cube,FpGeoModel model,int face,CallbackInfoReturnable<float[]> cir) {
        if (!questplus$usesAtlas()) return;
        var screen=model.bone("screen");
        if (face==FpGeoModel.FACE_NORTH && screen!=null && screen.cubes().contains(cube)) return;
        var uv=cir.getReturnValue();
        cir.setReturnValue(new float[]{uv[0],TabletSurfaceRenderer.ATLAS_HEIGHT-uv[1],uv[2],-uv[3]});
    }
}
