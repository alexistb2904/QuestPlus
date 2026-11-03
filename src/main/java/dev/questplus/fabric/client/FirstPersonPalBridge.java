package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import com.github.razorplay01.fp_pal.firstperson.FpAnimationResources;
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
    static void stop() { FpAnimationSystem.stopFpAnimation(); }
}
