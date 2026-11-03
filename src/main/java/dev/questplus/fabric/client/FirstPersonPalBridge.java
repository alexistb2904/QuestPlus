package dev.questplus.fabric.client;

import com.github.razorplay01.fp_pal.firstperson.FpAnimationSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** This class is loaded only when optional fp_pal is present. */
final class FirstPersonPalBridge {
    private FirstPersonPalBridge() {}
    static boolean play(String name) {
        var player=Minecraft.getInstance().player;
        return player!=null && FpAnimationSystem.playFpAnimation(player,Identifier.fromNamespaceAndPath("questplus",name));
    }
    static void stop() { FpAnimationSystem.stopFpAnimation(); }
}
