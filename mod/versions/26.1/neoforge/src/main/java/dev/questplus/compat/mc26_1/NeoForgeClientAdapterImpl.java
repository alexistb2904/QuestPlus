package dev.questplus.compat.mc26_1;

import dev.questplus.platform.neoforge.NeoForgeClientAdapter;
import java.util.function.Supplier;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

public final class NeoForgeClientAdapterImpl implements NeoForgeClientAdapter {
	@Override
	public <S extends PictureInPictureRenderState> void registerPictureInPicture(RegisterPictureInPictureRenderersEvent event, Class<S> state,
			Supplier<PictureInPictureRenderer<S>> factory) {
		// NeoForge 26.1.2 passes the GUI buffer source. The factory already uses the same one.
		event.register(state, buffers -> factory.get());
	}
}
