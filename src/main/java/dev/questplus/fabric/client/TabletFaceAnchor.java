package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.firstperson.FpArmRenderUtil;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Projects the actual Blockbench 'screen' cuboid from the same first-person model
 * matrix FP PAL uses. No hand-tuned GUI offsets and no fabricated second tablet.
 *
 * The projected 2D rectangle is an interim presentation adapter: widgets are
 * clipped to the true screen bounds, but this does NOT create an offscreen GPU
 * texture or perspective-correct UV mapping on the 3D polygon.
 */
public final class TabletFaceAnchor {
    record Rectangle(int left,int top,int right,int bottom) {
        int width() { return right-left; }
        int height() { return bottom-top; }
    }
    record Surface(float minX,float minY,float maxX,float maxY,float z,
                   float pivotX,float pivotY,float pivotZ) {
        float width() { return maxX-minX; }
        float height() { return maxY-minY; }
    }
    private static final Surface SCREEN=loadScreen();
    private static volatile Rectangle last;
    private static volatile long lastCapture;
    private TabletFaceAnchor() {}

    static Surface sourceScreen() { return SCREEN; }
    static void reset() { last=null;lastCapture=0; }
    static Rectangle current(int windowWidth,int windowHeight) {
        if (System.nanoTime()-lastCapture>300_000_000L) return null;
        Rectangle result=last;
        if (result==null || result.left()<0 || result.top()<0
            || result.right()>windowWidth || result.bottom()>windowHeight
            || result.width()<220 || result.height()<125) return null;
        return result;
    }

    /**
     * Called inside FP PAL's model pass with its calibrated camera/hand pose.
     * The 'tablet_root' bone transform is applied exactly once.
     */
    public static void capture(PoseStack modelFrame) {
        var mc=Minecraft.getInstance();
        // Capture during OPENING too: the first QuestScreen.init runs immediately
        // after the clip, and must already have a valid face projection.
        if (mc.player==null || !QuestClientTabletFlow.nativeHandsActive()) return;
        var bone=FpRenderState.getExtraBone("tablet_root");
        if (bone==null) return;

        int w=mc.getWindow().getGuiScaledWidth();
        int h=mc.getWindow().getGuiScaledHeight();
        double fov=Math.toRadians(mc.options.fov().get());
        if (w<=0 || h<=0 || !Double.isFinite(fov) || fov<=0) return;
        double focal=h/(2*Math.tan(fov/2));
        if (!Double.isFinite(focal)) return;

        modelFrame.pushPose();
        try {
            FpArmRenderUtil.applyBoneTransform(modelFrame,bone,
                new float[]{-SCREEN.pivotX(),SCREEN.pivotY(),SCREEN.pivotZ()});
            var transform=modelFrame.last().pose();
            float[][] corners={
                {-SCREEN.maxX()/16f,SCREEN.minY()/16f,SCREEN.z()/16f},
                {-SCREEN.minX()/16f,SCREEN.minY()/16f,SCREEN.z()/16f},
                {-SCREEN.maxX()/16f,SCREEN.maxY()/16f,SCREEN.z()/16f},
                {-SCREEN.minX()/16f,SCREEN.maxY()/16f,SCREEN.z()/16f}
            };
            double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY;
            double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            for(float[] c:corners) {
                Vector3f point=transform.transformPosition(new Vector3f(c[0],c[1],c[2]));
                if (point.z>=-.04f || !Float.isFinite(point.z)) return;
                double x=w*.5+point.x/(-point.z)*focal;
                double y=h*.5-point.y/(-point.z)*focal;
                if (!Double.isFinite(x) || !Double.isFinite(y)) return;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
            Rectangle projected=new Rectangle((int)Math.floor(minX),(int)Math.floor(minY),
                (int)Math.ceil(maxX),(int)Math.ceil(maxY));
            // Reject faces that are too tilted, tiny or behind the camera.
            double ratio=projected.width()/(double)Math.max(1,projected.height());
            if (ratio<1.2 || ratio>2.2 || projected.width()<220 || projected.height()<125
                || projected.width()>w*.95 || projected.height()>h*.85) return;
            last=projected;lastCapture=System.nanoTime();
        } finally { modelFrame.popPose(); }
    }

    private static Surface loadScreen() {
        try (var stream=TabletFaceAnchor.class.getResourceAsStream(
            "/assets/questplus/fp_models/questplus_first_person.geo.json")) {
            if (stream==null) throw new IllegalStateException("Missing first-person geometry");
            JsonObject geo=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry")
                .get(0).getAsJsonObject();
            JsonObject root=null,screen=null;
            for (var e:geo.getAsJsonArray("bones")) {
                JsonObject bone=e.getAsJsonObject();
                if ("tablet_root".equals(bone.get("name").getAsString())) root=bone;
                if ("screen".equals(bone.get("name").getAsString())) screen=bone;
            }
            if (root==null || screen==null || !screen.has("cubes")) throw new IllegalStateException("No screen/tablet root bones");
            var cube=screen.getAsJsonArray("cubes").get(0).getAsJsonObject();
            var origin=cube.getAsJsonArray("origin");var size=cube.getAsJsonArray("size");
            var pivot=root.getAsJsonArray("pivot");
            float x=origin.get(0).getAsFloat(),y=origin.get(1).getAsFloat(),z=origin.get(2).getAsFloat();
            float width=size.get(0).getAsFloat(),height=size.get(1).getAsFloat();
            if(width<1 || height<1)throw new IllegalStateException("Invalid screen face");
            // Front of the Bedrock surface: choose its north (-Z) face.
            return new Surface(x,y,x+width,y+height,z-.015f,
                pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat());
        } catch(Exception e) {throw new IllegalStateException("Cannot parse Blockbench screen face",e);}
    }
}
