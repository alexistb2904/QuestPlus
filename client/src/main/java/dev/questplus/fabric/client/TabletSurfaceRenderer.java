package dev.questplus.fabric.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import java.util.List;

/** Persistent GUI atlas drawn by Minecraft's own backend-independent renderer. */
public final class TabletSurfaceRenderer {
    public static final int WIDTH=500, HEIGHT=300, PIXEL_SCALE=3,
        SCREEN_WIDTH=WIDTH*PIXEL_SCALE, SCREEN_HEIGHT=HEIGHT*PIXEL_SCALE,
        ATLAS_WIDTH=2048, ATLAS_HEIGHT=1024, SCREEN_X=256;
    public static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("questplus","dynamic/tablet_surface");
    private static final Identifier BODY=Identifier.fromNamespaceAndPath("questplus","textures/fp/questplus_first_person.png");
    private static final GuiRenderState STATE=new GuiRenderState();
    private static RenderTarget target;
    private static GuiRenderer renderer;
    private static boolean rendering;
    private TabletSurfaceRenderer() {}
    /** Never hide vanilla HUD when the tablet GUI has gone away.
     * A stalled FP PAL closing animation must not hide the hotbar, crosshair
     * or inventory until the player disconnects.
     */
    public static boolean hidesHud() {
        return QuestClientTabletFlow.nativeHandsActive()
            && Minecraft.getInstance().gui.screen() instanceof QuestScreen;
    }
    static float uiScale() { return (float)QuestPlusClient.settings.scale; }
    static int logicalWidth() { return Math.round(WIDTH/uiScale()); }
    static int logicalHeight() { return Math.round(HEIGHT/uiScale()); }

    public static boolean active() {
        var mc=Minecraft.getInstance();
        return QuestClientTabletFlow.nativeHandsActive()
            && mc.gui.screen() instanceof QuestScreen;
    }
    /** Keep the last rendered UI on the physical display while it is being put away. */
    public static boolean hasSurface() {
        return target!=null && QuestClientTabletFlow.nativeHandsActive()
            && (active() || QuestClientTabletFlow.closing());
    }
    public static RenderTarget target() { return rendering ? target : null; }

    public static void render() {
        if (QuestClientTabletFlow.nativeHandsActive())
            FirstPersonPalBridge.prepareFrame(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (!active()) return;
        var mc=Minecraft.getInstance();
        if (target==null) {
            target=new TextureTarget("Quest Plus tablet",ATLAS_WIDTH,ATLAS_HEIGHT,GpuFormat.RGBA8_UNORM,GpuFormat.D32_FLOAT);
            renderer=new GuiRenderer(STATE,mc.gameRenderer.featureRenderDispatcher(),List.of());
            mc.getTextureManager().register(TEXTURE,new AtlasTexture(target));
        }
        var window=mc.gameRenderer.gameRenderState().windowRenderState;
        int oldWidth=window.width,oldHeight=window.height,oldScale=window.guiScale;
        RenderSystem.backupProjectionMatrix();
        try {
            rendering=true;
            var point=TabletFaceAnchor.pointer(mc.mouseHandler.getScaledXPos(mc.getWindow()),mc.mouseHandler.getScaledYPos(mc.getWindow()));
            int x=point==null ? -10000 : (int)(point.x()/uiScale()), y=point==null ? -10000 : (int)(point.y()/uiScale());
            var graphics=new GuiGraphicsExtractor(mc,STATE,x,y);
            graphics.blit(RenderPipelines.GUI_TEXTURED,BODY,0,0,0,0,256,256,256,256);
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            graphics.pose().translate(SCREEN_X,0);
            graphics.pose().scale(PIXEL_SCALE*uiScale());
            graphics.enableScissor(0,0,logicalWidth(),logicalHeight());
            ((QuestScreen)mc.gui.screen()).extractTablet(graphics,x,y,mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            // Dim only the 3D atlas, not the 2D fallback. Applied in a separate
            // stratum, after the UI, to preserve all authored colors and icons.
            double brightness=TabletShaderCompat.screenBrightness(
                QuestPlusClient.settings.screenBrightness,TabletShaderCompat.illuminatedScreen());
            if(brightness<.999){
                graphics.nextStratum();
                int darkness=(int)Math.round((1-brightness)*255);
                graphics.fill(0,0,logicalWidth(),logicalHeight(),darkness<<24);
            }
            graphics.extractDeferredElements(x,y,mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            graphics.disableScissor();
            graphics.pose().popMatrix();
            window.width=ATLAS_WIDTH; window.height=ATLAS_HEIGHT; window.guiScale=1;
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),new Vector4f(0,0,0,0),target.getDepthTexture(),0);
            renderer.render();
            renderer.endFrame();
        } finally {
            window.width=oldWidth; window.height=oldHeight; window.guiScale=oldScale;
            rendering=false; RenderSystem.restoreProjectionMatrix();
        }
    }
    public static void close() {
        if (renderer!=null) renderer.close();
        if (target!=null) target.destroyBuffers();
        renderer=null; target=null;
    }
    private static final class AtlasTexture extends AbstractTexture {
        AtlasTexture(RenderTarget target) {
            texture=target.getColorTexture(); textureView=target.getColorTextureView();
            sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
        @Override public void close() { }
    }
}
