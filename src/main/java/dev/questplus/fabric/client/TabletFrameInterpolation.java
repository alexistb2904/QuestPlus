package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.github.razorplay01.fp_pal.firstperson.FpRenderState;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

/** Interpolates copies of tick poses; PAL's animation clock and controllers stay untouched. */
public final class TabletFrameInterpolation {
    private static final Snapshots SNAPSHOTS=new Snapshots();
    private TabletFrameInterpolation() {}

    public static void capture(AbstractClientPlayer player) {
        if (player!=Minecraft.getInstance().player) return;
        if (!QuestClientTabletFlow.nativeHandsActive() || !FpRenderState.isFpActive || !(player instanceof IFpAnimatedPlayer animated)) {
            reset(); return;
        }
        var controller=animated.fpPal$getFpAnimationController();
        var id=controller==null ? null : controller.getCurrentFpAnimation();
        if (id==null || !id.getNamespace().equals("questplus") || !id.getPath().startsWith("questplus.")) {
            reset(); return;
        }
        Map<String,PlayerAnimBone> bones=new LinkedHashMap<>();
        for (var bone:core()) bones.put(bone.name,new PlayerAnimBone(bone));
        FpRenderState.getExtraBones().forEach((name,bone) -> bones.put(name,new PlayerAnimBone(bone)));
        var animation=controller.getCurrentAnimationInstance();
        if (animation==null) return;
        SNAPSHOTS.accept(new Snapshot(id,controller.getAnimationTicks(),animation.length(),bones));
    }

    public static void apply(float partial) {
        if (SNAPSHOTS.current==null || !FpRenderState.isFpActive) return;
        // FP PAL stops producing ticks at hold_on_last_frame; retain its exact terminal pose.
        float alpha=SNAPSHOTS.current.ticks()>=SNAPSHOTS.current.endTicks() ? 1 : Math.clamp(partial,0,1);
        for (var bone:core()) apply(bone,alpha);
        FpRenderState.getExtraBones().values().forEach(bone -> apply(bone,alpha));
    }
    private static void apply(PlayerAnimBone destination,float alpha) {
        var current=SNAPSHOTS.current.bones().get(destination.name);
        if (current==null) return;
        var previous=SNAPSHOTS.previous.bones().getOrDefault(destination.name,current);
        lerp(previous,current,destination,alpha);
    }
    static void lerp(PlayerAnimBone from,PlayerAnimBone to,PlayerAnimBone result,float alpha) {
        from.position.lerp(to.position,alpha,result.position);
        from.rotation.lerp(to.rotation,alpha,result.rotation);
        from.scale.lerp(to.scale,alpha,result.scale);
        result.bend=from.bend+(to.bend-from.bend)*alpha;
    }
    private static PlayerAnimBone[] core() {
        return new PlayerAnimBone[]{FpRenderState.fpPlayerArms,FpRenderState.fpRightArm,FpRenderState.fpLeftArm,
            FpRenderState.fpRightItem,FpRenderState.fpLeftItem};
    }
    public static boolean settled(Identifier id,double endTicks) { return SNAPSHOTS.settled(id,endTicks); }
    public static void reset() { SNAPSHOTS.clear(); }

    record Snapshot(Identifier id,double ticks,double endTicks,Map<String,PlayerAnimBone> bones) {}
    static final class Snapshots {
        private Snapshot previous,current;
        void accept(Snapshot next) {
            if (current==null) previous=next;
            else if (current.id().equals(next.id())) previous=current;
            else previous=new Snapshot(next.id(),0,next.endTicks(),current.bones());
            current=next;
        }
        boolean settled(Identifier id,double endTicks) {
            return current!=null && current.id().equals(id) && current.ticks()>=endTicks;
        }
        void clear() { previous=null; current=null; }
    }
}
