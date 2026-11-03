package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
import com.github.razorplay01.fp_pal.accessors.IFpAnimatedPlayer;
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
        return current==expected ? controller.getAnimationTicks()>=expected.length()
            : current==null && controller.hasAnimationFinished();
    }
    static void stop() { FpAnimationSystem.stopFpAnimation(); }
}
