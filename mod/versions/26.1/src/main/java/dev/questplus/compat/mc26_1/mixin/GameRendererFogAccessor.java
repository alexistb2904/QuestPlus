package dev.questplus.compat.mc26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.1 GuiRenderer.render needs the frame's fog buffer, which GameRenderer keeps private. */
@Mixin(GameRenderer.class)
public interface GameRendererFogAccessor {
	@Accessor("fogRenderer")
	FogRenderer questplus$fogRenderer();
}
