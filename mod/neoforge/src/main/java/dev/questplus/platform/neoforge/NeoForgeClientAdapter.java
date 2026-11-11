package dev.questplus.platform.neoforge;

import java.util.function.Supplier;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/**
 * Client NeoForge APIs whose shape differs between NeoForge builds. Each NeoForge target implements it
 * as {@code dev.questplus.compat.<id>.NeoForgeClientAdapterImpl} in {@code versions/<mc>/neoforge}.
 */
public interface NeoForgeClientAdapter {
	NeoForgeClientAdapter INSTANCE = dev.questplus.compat.MinecraftTarget.active().adapter("/neoforge-client", NeoForgeClientAdapter.class);

	/** NeoForge 26.1.2 takes a buffer-source function, 26.2+ a supplier. */
	<S extends PictureInPictureRenderState> void registerPictureInPicture(RegisterPictureInPictureRenderersEvent event, Class<S> state,
			Supplier<PictureInPictureRenderer<S>> factory);
}
