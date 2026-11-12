package dev.questplus.compat.mc26_3.mixin;

import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mods such as Xaero or JourneyMap draw waypoints and name tags in the HUD pass, which lands on top of the
 * 3D tablet drawn in the hand pass. Only skipping the whole HUD lets the tablet occlude them. */
@Mixin(Hud.class)
abstract class TabletHudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void questplus$hideDuringTablet(CallbackInfo ci) {
		if (TabletSurfaceRenderer.hidesHud())
			ci.cancel();
	}
}
