package dev.questplus.compat.mc26_1;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** 26.1 {@code GuiGraphicsExtractor.skin} only accepts a PlayerModel. This is the same renderer for any model. */
final class TabletModelPip {
	private TabletModelPip() {}

	record State(Model.Simple model, Identifier texture, float rotationX, float rotationY, float pivotY, int x0, int y0,
			int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
			implements PictureInPictureRenderState {
		State(Model.Simple model, Identifier texture, float rotationX, float rotationY, float pivotY, int x0, int y0,
				int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) {
			this(model, texture, rotationX, rotationY, pivotY, x0, y0, x1, y1, scale, scissorArea,
					PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
		}
	}

	/** Copy of 26.1 GuiSkinRenderer, typed for {@link State}. */
	static final class Renderer extends PictureInPictureRenderer<State> {
		Renderer(MultiBufferSource.BufferSource bufferSource) {
			super(bufferSource);
		}

		@Override
		public Class<State> getRenderStateClass() {
			return State.class;
		}

		@Override
		protected void renderToTexture(State state, PoseStack modelStack) {
			var gameRenderer = Minecraft.getInstance().gameRenderer;
			gameRenderer.getLighting().setupFor(Lighting.Entry.PLAYER_SKIN);
			int guiScale = gameRenderer.getGameRenderState().windowRenderState.guiScale;
			var viewStack = RenderSystem.getModelViewStack();
			viewStack.pushMatrix();
			float scale = state.scale() * guiScale;
			viewStack.rotateAround(Axis.XP.rotationDegrees(state.rotationX()), 0, scale * -state.pivotY(), 0);
			modelStack.mulPose(Axis.YP.rotationDegrees(-state.rotationY()));
			modelStack.translate(0, -1.6010001F, 0);
			var renderType = state.model().renderType(state.texture());
			state.model().renderToBuffer(modelStack, bufferSource.getBuffer(renderType), 15728880, OverlayTexture.NO_OVERLAY);
			bufferSource.endBatch();
			viewStack.popMatrix();
		}

		@Override
		protected String getTextureLabel() {
			return "questplus tablet";
		}
	}
}
