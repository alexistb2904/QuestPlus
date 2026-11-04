package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scope the native menu blur to this screen without changing the user's Minecraft options. */
@Mixin(Options.class)
abstract class TabletBlurMixin {
    @Inject(method="getMenuBackgroundBlurriness",at=@At("RETURN"),cancellable=true)
    private void questplus$blur(CallbackInfoReturnable<Integer> result) {
        if (Minecraft.getInstance().gui.screen() instanceof QuestScreen screen && QuestPlusClient.settings!=null) {
            if(TabletShaderCompat.safeRendering()){result.setReturnValue(0);return;}
            var settings=QuestPlusClient.settings;
            result.setReturnValue(settings.blur ? (int)Math.round(8*settings.blurIntensity*screen.animationProgress()) : 0);
        }
    }
}
