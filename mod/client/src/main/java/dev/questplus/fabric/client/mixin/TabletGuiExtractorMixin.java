package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphicsExtractor.class)
abstract class TabletGuiExtractorMixin {
	@Inject(method = "guiWidth", at = @At("HEAD"), cancellable = true)
	private void questplus$width(CallbackInfoReturnable<Integer> result) {
		if (TabletSurfaceRenderer.target() != null)
			result.setReturnValue(TabletSurfaceRenderer.atlasWidth());
	}

	@Inject(method = "guiHeight", at = @At("HEAD"), cancellable = true)
	private void questplus$height(CallbackInfoReturnable<Integer> result) {
		if (TabletSurfaceRenderer.target() != null)
			result.setReturnValue(TabletSurfaceRenderer.atlasHeight());
	}
}
