package dev.questplus.fabric.client.mixin;

import com.github.razorplay01.fp_pal.firstperson.FpGeoModel;
import com.github.razorplay01.fp_pal.firstperson.FpRenderBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.questplus.fabric.client.TabletFaceAnchor;
import dev.questplus.fabric.client.TabletIdleEffects;
import dev.questplus.fabric.client.TabletSurfaceRenderer;
import dev.questplus.fabric.client.QuestPlusClient;
import dev.questplus.fabric.client.TabletShaderCompat;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.github.razorplay01.fp_pal.firstperson.FpModelRenderer",remap=false)
abstract class TabletFaceCaptureMixin {
    @Shadow private static Identifier modelId;
    @Unique private static final Identifier QUESTPLUS_MODEL=Identifier.fromNamespaceAndPath("questplus","questplus_first_person");
    @Unique private static final Identifier TABLET_BODY=Identifier.fromNamespaceAndPath("questplus","textures/fp/questplus_first_person.png");
    private static int questplus$pass;
    @Unique private static TabletIdleEffects.Frame questplus$effects;

    @Unique private static boolean questplus$usesAtlas() {
        return TabletSurfaceRenderer.active() && QUESTPLUS_MODEL.equals(modelId);
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpRenderBuffer;drawGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lcom/github/razorplay01/fp_pal/firstperson/FpRenderBuffer$GeometryRenderer;)V"),remap=false)
    private static void questplus$surfacePass(FpRenderBuffer buffer,PoseStack pose,RenderType type,FpRenderBuffer.GeometryRenderer geometry) {
        if (!questplus$usesAtlas()) { buffer.drawGeometry(pose,type,geometry);return; }
        // With shaders, draw the physical frame from its ORIGINAL static opaque
        // texture, not the live GUI framebuffer atlas. Bliss can interpret a
        // dynamically populated entity atlas as a reflective/glass material.
        boolean shaders=TabletShaderCompat.shadersActive();
        var bodyType=shaders ? RenderTypes.entityCutout(TABLET_BODY) : type;
        buffer.drawGeometry(pose,bodyType,(p,c) -> {
            questplus$pass=1;
            try { geometry.render(p,c); } finally { questplus$pass=0; }
        });
        // Bright by default. When disabled, the screen receives the same world
        // light as any other translucent entity surface.
        // In Iris, a translucent emissive entity is often interpreted by shader
        // packs as glass/mirror (Bliss) or HDR bloom (MakeUp). Use a MATTE
        // cutout plane for the tablet UI instead of shader-dependent materials.
        var settings=QuestPlusClient.settings;
        boolean matte=TabletShaderCompat.matteIllumination(shaders,settings.screenEmissive);
        // No shader: original ON=emissive, OFF=ordinary translucent.
        // Shader: ON=matte fullbright (old shader-safe mode), OFF=ordinary
        // non-emissive surface. The independent shader-safe toggle is removed.
        var screenType=matte ? RenderTypes.entityCutout(TabletSurfaceRenderer.TEXTURE)
            : settings.screenEmissive && !shaders
            ? RenderTypes.entityTranslucentEmissive(TabletSurfaceRenderer.TEXTURE)
            : RenderTypes.entityTranslucent(TabletSurfaceRenderer.TEXTURE);
        buffer.drawGeometry(pose,screenType,(p,c) -> {
            questplus$pass=2;
            try { geometry.render(p,c); } finally { questplus$pass=0; }
        });
        var effects=TabletIdleEffects.frame();
        buffer.drawGeometry(pose,TabletIdleEffects.renderType(),(p,c) -> {
            questplus$pass=3;
            questplus$effects=effects;
            try { geometry.render(p,c); } finally { questplus$pass=0; questplus$effects=null; }
        });
    }

    // Screen-only full lightmap under shader-safe MATTE rendering. This
    // preserves readability in caves without an emissive HDR/bloom material.
    // The other tablet surfaces keep their real world lighting.
    @ModifyVariable(method="renderCube",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static int questplus$matteScreenLight(int light) {
        return questplus$pass==2 && TabletShaderCompat.illuminatedScreen()
            ? LightCoordsUtil.FULL_BRIGHT : light;
    }

    @Inject(method="renderCube",at=@At("HEAD"),cancellable=true,remap=false)
    private static void questplus$onlyOriginalSurface(PoseStack pose,VertexConsumer consumer,FpGeoModel model,FpGeoModel.Cube cube,int light,CallbackInfo ci) {
        var screen=model.bone("screen");
        boolean surface=screen!=null && screen.cubes().contains(cube);
        if (questplus$pass==3 || questplus$pass==1 && surface || questplus$pass==2 && !surface) ci.cancel();
    }
    @Inject(method="renderBone",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"),remap=false)
    private static void questplus$capture(PoseStack pose,VertexConsumer consumer,FpGeoModel model,FpGeoModel.Bone bone,int light,boolean root,CallbackInfo ci) {
        if (questplus$usesAtlas()) TabletFaceAnchor.capture(pose,bone);
        if (questplus$pass==3 && bone.name().equals("tablet_root")) {
            pose.pushPose();
            pose.translate(bone.pivotX()/16f,bone.pivotY()/16f,bone.pivotZ()/16f);
            pose.scale(.65f,.65f,.65f);
            TabletIdleEffects.draw(pose.last(),consumer,questplus$effects);
            pose.popPose();
        }
    }

    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/rendertype/RenderTypes;entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"),remap=false)
    private static RenderType questplus$texture(Identifier original) {
        return RenderTypes.entityCutout(questplus$usesAtlas() ? TabletSurfaceRenderer.TEXTURE : original);
    }

    @Redirect(method="renderCube",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpGeoModel;textureWidth()I"),remap=false)
    private static int questplus$width(FpGeoModel model) {
        return questplus$usesAtlas() && !(questplus$pass==1 && TabletShaderCompat.shadersActive())
            ? TabletSurfaceRenderer.ATLAS_WIDTH : model.textureWidth();
    }
    @Redirect(method="renderCube",at=@At(value="INVOKE",target="Lcom/github/razorplay01/fp_pal/firstperson/FpGeoModel;textureHeight()I"),remap=false)
    private static int questplus$height(FpGeoModel model) {
        return questplus$usesAtlas() && !(questplus$pass==1 && TabletShaderCompat.shadersActive())
            ? TabletSurfaceRenderer.ATLAS_HEIGHT : model.textureHeight();
    }
    @Inject(method="uv",at=@At("HEAD"),cancellable=true,remap=false)
    private static void questplus$screenUv(FpGeoModel.Cube cube,FpGeoModel model,int face,CallbackInfoReturnable<float[]> cir) {
        var screen=model.bone("screen");
        if (questplus$usesAtlas() && questplus$pass==2 && face==FpGeoModel.FACE_NORTH && screen!=null && screen.cubes().contains(cube))
            cir.setReturnValue(new float[]{TabletSurfaceRenderer.SCREEN_X+TabletSurfaceRenderer.SCREEN_WIDTH,TabletSurfaceRenderer.ATLAS_HEIGHT-TabletSurfaceRenderer.SCREEN_HEIGHT,-TabletSurfaceRenderer.SCREEN_WIDTH,TabletSurfaceRenderer.SCREEN_HEIGHT});
    }
    @Inject(method="uv",at=@At("RETURN"),cancellable=true,remap=false)
    private static void questplus$atlasUv(FpGeoModel.Cube cube,FpGeoModel model,int face,CallbackInfoReturnable<float[]> cir) {
        if (!questplus$usesAtlas() || questplus$pass==1 && TabletShaderCompat.shadersActive()) return;
        var screen=model.bone("screen");
        if (face==FpGeoModel.FACE_NORTH && screen!=null && screen.cubes().contains(cube)) return;
        var uv=cir.getReturnValue();
        cir.setReturnValue(new float[]{uv[0],TabletSurfaceRenderer.ATLAS_HEIGHT-uv[1],uv[2],-uv[3]});
    }
}
