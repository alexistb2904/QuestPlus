package dev.questplus.compat.mc26_1.mixin;

import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class TabletHudMixin {
	@Inject(method = { "extractCrosshair", "extractHotbarAndDecorations",
			"extractSelectedItemName" }, at = @At("HEAD"), cancellable = true)
	private void questplus$hideDuringTablet(CallbackInfo ci) {
		if (TabletSurfaceRenderer.hidesHud())
			ci.cancel();
	}
}
