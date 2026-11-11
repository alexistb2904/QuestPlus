package dev.questplus.compat.mc26_1;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.compat.ClientAdapter;
import dev.questplus.compat.mc26_1.mixin.GameRendererFogAccessor;
import dev.questplus.compat.mc26_1.mixin.GuiGraphicsStateAccessor;
import java.util.List;
import dev.questplus.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ClientAdapterImpl implements ClientAdapter {
	private static final Keys KEYS = new Keys(InputConstants.KEY_J, InputConstants.KEY_F6, InputConstants.KEY_HOME,
			InputConstants.KEY_EQUALS, InputConstants.KEY_ADD, InputConstants.KEY_MINUS, InputConstants.MOUSE_BUTTON_LEFT);

	@Override
	public void initialize() {
		// Same buffer the GUI renderer hands to vanilla picture-in-picture renderers.
		ClientPlatform.pictureInPicture(TabletModelPip.State.class,
				() -> new TabletModelPip.Renderer(Minecraft.getInstance().renderBuffers().bufferSource()));
	}

	@Override
	public Keys keys() {
		return KEYS;
	}

	@Override
	public Screen screen(Minecraft minecraft) {
		return minecraft.screen;
	}

	@Override
	public void setScreen(Minecraft minecraft, Screen screen) {
		minecraft.setScreen(screen);
	}

	@Override
	public void blitTextured(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float u, float v,
			int width, int height, int textureWidth, int textureHeight) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	@Override
	public void blitNearest(GuiGraphicsExtractor graphics, AbstractTexture texture, int x0, int y0, int x1, int y1) {
		graphics.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), x0,
				y0, x1, y1, 0, 1, 0, 1);
	}

	@Override
	public void skin(GuiGraphicsExtractor graphics, Model.Simple model, Identifier texture, float scale,
			float rotationX, float rotationY, float pivotY, int x0, int y0, int x1, int y1) {
		// shortcut: no scissor (26.1 keeps its scissor stack private); add an accessor if the tablet is ever scissored.
		((GuiGraphicsStateAccessor) graphics).questplus$guiRenderState().addPicturesInPictureState(
				new TabletModelPip.State(model, texture, rotationX, rotationY, pivotY, x0, y0, x1, y1, scale, null));
	}

	@Override
	public void entity(GuiGraphicsExtractor graphics, EntityRenderState state, float scale, Vector3f translation,
			Quaternionf rotation, Quaternionf overrideCameraAngle, int x0, int y0, int x1, int y1) {
		graphics.entity(state, scale, translation, rotation, overrideCameraAngle, x0, y0, x1, y1);
	}

	@Override
	public <S> void submitModel(SubmitNodeCollector nodes, Model<? super S> model, S state, PoseStack pose,
			Identifier texture, int light, int overlay, int color) {
		nodes.submitModel(model, state, pose, texture, light, overlay, color, null);
	}

	@Override
	public OffscreenGui offscreenGui(String label, GuiRenderState state, int width, int height, Identifier texture) {
		return new Surface(label, state, width, height, texture);
	}

	private static final class Surface implements OffscreenGui {
		private final TextureTarget target;
		private final GuiRenderer renderer;

		Surface(String label, GuiRenderState state, int width, int height, Identifier texture) {
			var mc = Minecraft.getInstance();
			target = new TextureTarget(label, width, height, true);
			renderer = new GuiRenderer(state, mc.renderBuffers().bufferSource(), mc.gameRenderer.getSubmitNodeStorage(),
					mc.gameRenderer.getFeatureRenderDispatcher(), List.of());
			mc.getTextureManager().register(texture, new AtlasTexture(target));
		}

		@Override
		public RenderTarget target() {
			return target;
		}

		@Override
		public void render() {
			var gameRenderer = Minecraft.getInstance().gameRenderer;
			var window = gameRenderer.getGameRenderState().windowRenderState;
			int oldWidth = window.width, oldHeight = window.height, oldScale = window.guiScale;
			try {
				window.width = target.width;
				window.height = target.height;
				window.guiScale = 1;
				// 26.1 still uses a standard depth range: clear depth to 1, not 0.
				RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(), 0,
						target.getDepthTexture(), 1);
				renderer.render(((GameRendererFogAccessor) gameRenderer).questplus$fogRenderer()
						.getBuffer(FogRenderer.FogMode.NONE));
				renderer.endFrame();
			} finally {
				window.width = oldWidth;
				window.height = oldHeight;
				window.guiScale = oldScale;
			}
		}

		@Override
		public void close() {
			renderer.close();
			target.destroyBuffers();
		}
	}

	private static final class AtlasTexture extends AbstractTexture {
		AtlasTexture(RenderTarget target) {
			texture = target.getColorTexture();
			textureView = target.getColorTextureView();
			sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}

		@Override
		public void close() {
		}
	}
}
