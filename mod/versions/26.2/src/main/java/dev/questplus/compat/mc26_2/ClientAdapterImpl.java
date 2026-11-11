package dev.questplus.compat.mc26_2;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import dev.questplus.compat.ClientAdapter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class ClientAdapterImpl implements ClientAdapter {
	private static final Keys KEYS = new Keys(InputConstants.KEY_J, InputConstants.KEY_F6, InputConstants.KEY_HOME,
			InputConstants.KEY_EQUALS, InputConstants.KEY_ADD, InputConstants.KEY_MINUS, InputConstants.MOUSE_BUTTON_LEFT);

	@Override
	public Keys keys() {
		return KEYS;
	}

	@Override
	public Screen screen(Minecraft minecraft) {
		return minecraft.gui.screen();
	}

	@Override
	public void setScreen(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
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
		graphics.skin(model, texture, scale, rotationX, rotationY, pivotY, x0, y0, x1, y1);
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
			target = new TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM);
			renderer = new GuiRenderer(state, mc.gameRenderer.featureRenderDispatcher(), List.of());
			mc.getTextureManager().register(texture, new AtlasTexture(target));
		}

		@Override
		public RenderTarget target() {
			return target;
		}

		@Override
		public void render() {
			var window = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState;
			int oldWidth = window.width, oldHeight = window.height, oldScale = window.guiScale;
			try {
				window.width = target.width;
				window.height = target.height;
				window.guiScale = 1;
				RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(target.getColorTexture(),
						new Vector4f(0, 0, 0, 0), target.getDepthTexture(), 0);
				renderer.render();
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
