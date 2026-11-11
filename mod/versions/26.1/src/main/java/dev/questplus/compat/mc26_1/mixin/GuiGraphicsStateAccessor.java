package dev.questplus.compat.mc26_1.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the 26.1 tablet model submit its own picture-in-picture state like vanilla skin(). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsStateAccessor {
	@Accessor("guiRenderState")
	GuiRenderState questplus$guiRenderState();
}
