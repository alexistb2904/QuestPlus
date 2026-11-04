package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Small emissive details in the standalone Blockbench tablet's coordinates. */
public final class TabletIdleEffects {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("questplus","textures/entity/tablet_light.png");
    public record Frame(double seconds,boolean animated) {
        float glow() { return animated ? (float)(.26+.14*Math.sin(seconds*Math.PI/3)) : .26f; }
        float phase(int index) { return (float)((seconds/3.6+index/4.0)%1); }
        float opacity(int index) { return animated ? (float)(.65*Math.pow(Math.sin(Math.PI*phase(index)),2)) : 0; }
    }
    private TabletIdleEffects() {}
    public static RenderType renderType() {
        return TabletShaderCompat.safeRendering() ? RenderTypes.entityTranslucent(TEXTURE)
            : RenderTypes.entityTranslucentEmissive(TEXTURE);
    }
    public static Frame frame() {
        var mc=Minecraft.getInstance();
        var settings=QuestPlusClient.settings;
        double seconds=mc.level==null ? 0 : (mc.level.getGameTime()+mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))/20.0;
        return new Frame(seconds,settings!=null && settings.animations && !settings.reducedMotion);
    }
    public static void draw(PoseStack.Pose pose,VertexConsumer consumer,Frame frame) {
        // Engraved book lines, rather than a bright rectangle hiding the medallion.
        for (int side:new int[]{-1,1}) for (int row=0;row<4;row++)
            quad(pose,consumer,side*.49f,7.275f+row*.17f,-2.095f,row==3 ? .29f : .46f,.045f,0xcba4ff,frame.glow());
        for (int side:new int[]{-1,1}) for (int top:new int[]{-1,1})
            quad(pose,consumer,side*11.15f,top*7.15f,-1.94f,.12f,.12f,0xffcc83,frame.glow()*.65f);
        for (int i=0;i<4;i++) {
            float t=frame.phase(i),alpha=frame.opacity(i);
            if (alpha<.01f) continue;
            float x=(i%2==0 ? -1 : 1)*(.7f+.5f*t)+(float)Math.sin(t*Math.PI*2+i)*.12f;
            quad(pose,consumer,x,8.1f+1.8f*t,-2.2f-.4f*t,.11f,.11f,0xd9bcff,alpha);
        }
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer consumer,float x,float y,float z,float width,float height,int rgb,float alpha) {
        int color=(Math.round(alpha*255)<<24)|rgb;
        float l=(x-width/2)/16,r=(x+width/2)/16,b=(y-height/2)/16,t=(y+height/2)/16,depth=z/16;
        vertex(pose,consumer,l,b,depth,color); vertex(pose,consumer,l,t,depth,color);
        vertex(pose,consumer,r,t,depth,color); vertex(pose,consumer,r,b,depth,color);
    }
    private static void vertex(PoseStack.Pose pose,VertexConsumer consumer,float x,float y,float z,int color) {
        consumer.addVertex(pose,x,y,z).setColor(color).setUv(.5f,.5f)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose,0,0,-1);
    }
}
