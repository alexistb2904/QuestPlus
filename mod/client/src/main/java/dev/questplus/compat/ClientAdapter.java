package dev.questplus.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Client-only Minecraft APIs whose shape differs between targets. Never loaded on a dedicated server. */
public interface ClientAdapter {
	ClientAdapter INSTANCE = MinecraftTarget.active().adapter("/client", ClientAdapter.class);

	/** Registers target-specific client hooks. Called first by the client entrypoint. */
	default void initialize() {}

	/** Input codes of the running target, never inlined into shared code (GLFW before 26.3, SDL after). */
	Keys keys();

	record Keys(int j, int f6, int home, int equals, int add, int minus, int mouseLeft) {}

	Screen screen(Minecraft minecraft);

	void setScreen(Minecraft minecraft, Screen screen);

	/** {@code graphics.blit(RenderPipelines.GUI_TEXTURED, ...)}. */
	void blitTextured(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float u, float v, int width,
			int height, int textureWidth, int textureHeight);

	/** Draws the whole texture into the rectangle with nearest filtering. */
	void blitNearest(GuiGraphicsExtractor graphics, AbstractTexture texture, int x0, int y0, int x1, int y1);

	/** {@code GuiGraphicsExtractor.skin}: draws a block-bench style model as a GUI picture-in-picture. */
	void skin(GuiGraphicsExtractor graphics, Model.Simple model, Identifier texture, float scale, float rotationX,
			float rotationY, float pivotY, int x0, int y0, int x1, int y1);

	/** {@code GuiGraphicsExtractor.entity}. Its vector parameters changed to read-only JOML types after 26.1. */
	void entity(GuiGraphicsExtractor graphics, EntityRenderState state, float scale, Vector3f translation,
			Quaternionf rotation, Quaternionf overrideCameraAngle, int x0, int y0, int x1, int y1);

	<S> void submitModel(SubmitNodeCollector nodes, Model<? super S> model, S state, PoseStack pose, Identifier texture,
			int light, int overlay, int color);

	/** Creates an offscreen GUI surface whose color texture is registered as {@code texture}. */
	OffscreenGui offscreenGui(String label, GuiRenderState state, int width, int height, Identifier texture);

	/** A color + depth target that its own GuiRenderer draws an extracted {@link GuiRenderState} into. */
	interface OffscreenGui {
		RenderTarget target();

		/** Clears the target and draws the extracted state at the target size with GUI scale 1. */
		void render();

		void close();
	}
}
