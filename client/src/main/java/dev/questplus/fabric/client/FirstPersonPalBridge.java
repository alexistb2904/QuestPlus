package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
import com.zigythebird.playeranimcore.enums.State;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** This class is loaded only when optional fp_pal is present. */
final class FirstPersonPalBridge {
    private FirstPersonPalBridge() {}
    static boolean play(String name) {
        var player=Minecraft.getInstance().player;
        var id=Identifier.fromNamespaceAndPath("questplus",name);
        if (player==null || !FpAnimationResources.hasAnimation(id)) return false;
        return FpAnimationSystem.playFpAnimation(player,id);
    }
    static boolean loaded(String name) {
        return FpAnimationResources.hasAnimation(Identifier.fromNamespaceAndPath("questplus",name));
    }
    static boolean finished(String name) {
        var player=Minecraft.getInstance().player;
        if (!(player instanceof IFpAnimatedPlayer animated)) return false;
        var controller=animated.fpPal$getFpAnimationController();
        var id=Identifier.fromNamespaceAndPath("questplus",name);
        if (controller==null || controller.isPendingPlay() || !id.equals(controller.getCurrentFpAnimation())) return false;
        var expected=FpAnimationResources.getAnimation(id);
        if (expected==null) return false;
        var current=controller.getCurrentAnimationInstance();
        boolean held=current==expected && controller.getAnimationState()==State.PAUSED;
        boolean ended=current==expected ? terminalPose(controller.getAnimationTicks(),expected.length(),held)
            : current==null && controller.hasAnimationFinished();
        return ended && (held || TabletFrameInterpolation.settled(id,expected.length()));
    }
    // PAL pauses a held final frame; render partials can then report a time just below its length.
    static boolean terminalPose(double ticks,double length,boolean held) { return held || ticks>=length; }
    static void prepareFrame(float partialTick) { TabletFrameInterpolation.apply(partialTick); }
    static void stop() {
        TabletFrameInterpolation.reset();
        FpAnimationSystem.stopFpAnimation();
        if (Minecraft.getInstance().player instanceof IFpAnimatedPlayer animated) {
            var controller=animated.fpPal$getFpAnimationController();
            if (controller!=null) controller.setActive(false);
        }
    }
}
