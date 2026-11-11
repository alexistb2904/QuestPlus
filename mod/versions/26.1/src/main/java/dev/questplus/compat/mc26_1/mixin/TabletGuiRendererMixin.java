package dev.questplus.compat.mc26_1.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.questplus.fabric.client.TabletSurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 26.1 GuiRenderer draws into Minecraft's main target; the tablet atlas redirects it. */
@Mixin(GuiRenderer.class)
abstract class TabletGuiRendererMixin {
	@Redirect(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getMainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
	private RenderTarget questplus$target(Minecraft minecraft) {
		var target = TabletSurfaceRenderer.target();
		return target == null ? minecraft.getMainRenderTarget() : target;
	}
}
