package dev.questplus.compat.mc26_2.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRenderer.class)
abstract class TabletGuiRendererMixin {
	@Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget questplus$target(GameRenderer renderer) {
		var target = TabletSurfaceRenderer.target();
		return target == null ? renderer.mainRenderTarget() : target;
	}
}
