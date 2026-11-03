package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.questplus.fabric.TabletPoseNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Unit;
import java.util.*;

public final class TabletPoseClient {
    private static final Map<UUID,TabletAnimationController> POSES = new HashMap<>();
    private static boolean sent;
    private static int heartbeat;
    private static Object level;
    private TabletPoseClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TabletPoseNetwork.State.TYPE,(packet,ctx) -> ctx.client().execute(() -> {
            changeLevel(ctx.client().level);
            transition(packet.player(),packet.open());
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client) -> { POSES.clear(); level=null; sent=false; heartbeat=0; });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            changeLevel(client.level);
            long time=System.nanoTime(); boolean animate=animated();
            if (!animate) POSES.values().forEach(p -> p.settle(time));
            POSES.values().removeIf(p -> p.finishedClosed(time,animate));
            if (client.player==null || !ClientPlayNetworking.canSend(TabletPoseNetwork.Request.TYPE)) return;
            boolean now=client.gui.screen() instanceof QuestScreen && client.player.isAlive() && !client.player.isSpectator();
            if (now!=sent || (now && ++heartbeat>=100)) {
                if (now!=sent) transition(client.player.getUUID(),now);
                ClientPlayNetworking.send(new TabletPoseNetwork.Request(now)); sent=now; heartbeat=0;
            }
        });
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type,renderer,helper,context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new Layer(avatar));
        });
    }
    private static void changeLevel(Object current) {
        if (level!=current) { POSES.clear(); level=current; sent=false; heartbeat=0; }
    }
    private static boolean animated() {
        var settings=QuestPlusClient.settings;
        return settings!=null && settings.animations && !settings.reducedMotion;
    }
    private static void transition(UUID id, boolean open) {
        if (!open && !POSES.containsKey(id)) return;
        POSES.computeIfAbsent(id,ignored -> new TabletAnimationController()).set(open,System.nanoTime(),animated());
    }
    public static float progress(AvatarRenderState state) {
        var client=Minecraft.getInstance();
        var entity=client.level==null ? null : client.level.getEntity(state.id);
        if (entity==null || !entity.isAlive()) return 0;
        var pose=POSES.get(entity.getUUID());
        return pose==null ? 0 : pose.value(System.nanoTime(),animated());
    }
    private static final class Layer extends RenderLayer<AvatarRenderState,PlayerModel> {
        private final net.minecraft.client.model.Model.Simple model=TabletRenderer.worldModel();
        Layer(AvatarRenderer<?> renderer) { super(renderer); }
        @Override public void submit(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state, float yaw, float pitch) {
            float progress=progress(state);
            if (state.isInvisible || progress<=0) return;
            pose.pushPose();
            double idle=animated() ? Math.sin(System.nanoTime()/1_000_000_000.0 * 1.5)*.004*progress : 0;
            pose.translate(0,1.18+(1-progress)*.3+idle,-.55+(1-progress)*.15);
            pose.rotateDegrees(Axis.YP,180);
            float scale=.65f * progress;
            pose.scale(scale,scale,scale);
            nodes.submitModel(model,Unit.INSTANCE,pose,TabletRenderer.texture(),light,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
    }
}
