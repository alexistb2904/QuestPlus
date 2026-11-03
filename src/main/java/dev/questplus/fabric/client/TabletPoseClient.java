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
    private static final Map<UUID,PoseTime> POSES = new HashMap<>();
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
            POSES.values().removeIf(p -> p.finishedClosed(time,animate));
            if (client.player==null || !ClientPlayNetworking.canSend(TabletPoseNetwork.Request.TYPE)) return;
            boolean now=(client.gui.screen() instanceof QuestScreen || QuestClientTabletFlow.thirdPersonHolding())
                && client.player.isAlive() && !client.player.isSpectator();
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
        POSES.computeIfAbsent(id,ignored -> new PoseTime()).set(open,System.nanoTime(),animated());
    }
    record Frame(TabletThirdPersonAnimation.Pose pose,float weight) {}
    private static Frame sample(AvatarRenderState state) {
        var client=Minecraft.getInstance();
        var entity=client.level==null ? null : client.level.getEntity(state.id);
        if (entity==null || !entity.isAlive() || state.isSpectator) return null;
        var pose=POSES.get(entity.getUUID());
        return pose==null ? null : pose.sample(System.nanoTime(),animated());
    }
    public static void apply(PlayerModel model,AvatarRenderState state) {
        var pose=sample(state);
        if (pose==null) return;
        var value=pose.pose(); float weight=pose.weight();
        arm(model.rightArm,value.right(),value.rightPosition(),weight);
        arm(model.leftArm,value.left(),value.leftPosition(),weight);
        model.head.xRot=blend(model.head.xRot,(float)Math.toRadians(value.head().x()),weight);
        model.head.yRot=blend(model.head.yRot,(float)Math.toRadians(value.head().y()),weight);
        model.head.zRot=blend(model.head.zRot,(float)Math.toRadians(value.head().z()),weight);
    }
    private static float blend(float from,float to,float weight) { return from+(to-from)*weight; }
    static void arm(net.minecraft.client.model.geom.ModelPart arm,TabletThirdPersonAnimation.Vector rotation,TabletThirdPersonAnimation.Vector position,float weight) {
        arm.xRot=blend(arm.xRot,(float)Math.toRadians(rotation.x()),weight);
        arm.yRot=blend(arm.yRot,(float)Math.toRadians(rotation.y()),weight);
        arm.zRot=blend(arm.zRot,(float)Math.toRadians(rotation.z()),weight);
        arm.x+=position.x()*weight; arm.y-=position.y()*weight; arm.z+=position.z()*weight;
    }
    static final class PoseTime {
        private static final double BLEND_SECONDS=TabletThirdPersonAnimation.OPEN_SECONDS/6;
        private double from;
        private boolean open;
        private long started;
        private TabletThirdPersonAnimation.Pose transitionFrom;
        void set(boolean next,long now,boolean animate) {
            if (next==open) return;
            Frame previous=sample(now,animate);
            from=progress(now,animate); open=next; started=now;
            transitionFrom=animate && previous!=null ? previous.pose() : null;
        }
        double progress(long now,boolean animate) {
            if (!animate) return open ? 1 : 0;
            double elapsed=Math.max(0,now-started)/1_000_000_000.0/TabletThirdPersonAnimation.OPEN_SECONDS;
            return Math.clamp(from+(open ? elapsed : -elapsed),0,1);
        }
        boolean finishedClosed(long now,boolean animate) { return !open && progress(now,animate)<=0; }
        Frame sample(long now,boolean animate) {
            double value=progress(now,animate);
            if (value<=0 && !open) return null;
            boolean idle=open && value>=1;
            double idleTime=animate ? Math.max(0,(now-started)/1_000_000_000.0-(1-from)*TabletThirdPersonAnimation.OPEN_SECONDS) : 0;
            var pose=TabletThirdPersonAnimation.sample(idle,idle ? idleTime : value*TabletThirdPersonAnimation.OPEN_SECONDS);
            if (transitionFrom!=null && animate) {
                double t=Math.clamp((now-started)/1_000_000_000.0/BLEND_SECONDS,0,1);
                pose=transitionFrom.blend(pose,t*t*(3-2*t));
            }
            float weight=animate ? (float)Math.clamp(value*TabletThirdPersonAnimation.OPEN_SECONDS/BLEND_SECONDS,0,1) : 1;
            weight=weight*weight*(3-2*weight);
            return new Frame(pose,weight);
        }
    }
    private static final class Layer extends RenderLayer<AvatarRenderState,PlayerModel> {
        private final net.minecraft.client.model.Model.Simple model=TabletRenderer.worldModel();
        Layer(AvatarRenderer<?> renderer) { super(renderer); }
        @Override public void submit(PoseStack pose, SubmitNodeCollector nodes, int light, AvatarRenderState state, float yaw, float pitch) {
            var sampled=sample(state);
            if (state.isInvisible || sampled==null || sampled.pose().scale().x()<=0) return;
            var frame=sampled.pose();
            pose.pushPose();
            // Blockbench's player origin is at the feet; Minecraft's model origin is at the head.
            var position=frame.position(); var rotation=frame.rotation(); var scale=frame.scale();
            pose.translate(-position.x()/16.0,(24-18.8-position.y())/16.0,(-10.5+position.z())/16.0);
            pose.rotateDegrees(Axis.ZP,rotation.z());
            pose.rotateDegrees(Axis.YP,-rotation.y());
            pose.rotateDegrees(Axis.XP,-rotation.x());
            pose.scale(-.65f*scale.x(),.65f*scale.y(),.65f*scale.z());
            nodes.submitModel(model,Unit.INSTANCE,pose,TabletRenderer.texture(),light,OverlayTexture.NO_OVERLAY,0);
            pose.popPose();
        }
    }
}
