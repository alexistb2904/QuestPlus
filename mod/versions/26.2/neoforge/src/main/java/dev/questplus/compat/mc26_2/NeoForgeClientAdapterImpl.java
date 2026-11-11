package dev.questplus.compat.mc26_2;

import dev.questplus.platform.neoforge.NeoForgeClientAdapter;
import java.util.function.Supplier;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

public final class NeoForgeClientAdapterImpl implements NeoForgeClientAdapter {
	@Override
	public <S extends PictureInPictureRenderState> void registerPictureInPicture(RegisterPictureInPictureRenderersEvent event, Class<S> state,
			Supplier<PictureInPictureRenderer<S>> factory) {
		event.register(state, factory);
	}
}
