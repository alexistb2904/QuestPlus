package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;

/**
 * Persistent GUI atlas drawn by Minecraft's own backend-independent renderer.
 */
public final class TabletSurfaceRenderer {
	public static final int WIDTH = 500, HEIGHT = 300, SCREEN_X = 256;
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("questplus", "dynamic/tablet_surface");
	private static final Identifier BODY = Identifier.fromNamespaceAndPath("questplus",
			"textures/fp/questplus_first_person.png");
	private static final GuiRenderState STATE = new GuiRenderState();
	private static ClientAdapter.OffscreenGui surface;
	private static boolean rendering;
	private static int pixelScale = 6;

	private TabletSurfaceRenderer() {
	}

	public static int screenWidth() {
		return WIDTH * pixelScale;
	}

	public static int screenHeight() {
		return HEIGHT * pixelScale;
	}

	/** The screen starts on a whole GUI unit, so scissors stay pixel exact at every GUI scale. */
	public static int screenX() {
		return -Math.floorDiv(-SCREEN_X, pixelScale) * pixelScale;
	}

	/** GUI scale of the atlas pass: Minecraft bakes item icons at 16 × this, so it matches the screen density. */
	public static int guiScale() {
		return pixelScale;
	}

	public static int atlasWidth() {
		return screenX() + screenWidth();
	}

	public static int atlasHeight() {
		return Math.max(256, screenHeight());
	}

	/**
	 * Never hide vanilla HUD when the tablet GUI has gone away. A stalled FP PAL
	 * closing animation must not hide the hotbar, crosshair or inventory until the
	 * player disconnects.
	 */
	public static boolean hidesHud() {
		return QuestClientTabletFlow.nativeHandsActive() && screen() != null;
	}

	static float uiScale() {
		return (float) QuestPlusClient.settings.scale;
	}

	static int logicalWidth() {
		return Math.round(WIDTH / uiScale());
	}

	static int logicalHeight() {
		return Math.round(HEIGHT / uiScale());
	}

	public static boolean active() {
		return QuestClientTabletFlow.nativeHandsActive() && screen() != null;
	}

	public static QuestScreen screen() {
		return QuestRecipeViewer.tabletScreen();
	}

	/**
	 * Keep the last rendered UI on the physical display while it is being put away.
	 */
	public static boolean hasSurface() {
		return surface != null && QuestClientTabletFlow.nativeHandsActive()
				&& (active() || QuestClientTabletFlow.closing());
	}

	public static RenderTarget target() {
		return rendering ? surface.target() : null;
	}

	public static void render() {
		if (!active())
			return;
		var mc = Minecraft.getInstance();
		int requestedScale = Math.clamp(QuestPlusClient.settings.treeResolution, 1, 8);
		if (pixelScale != requestedScale) {
			close();
			pixelScale = requestedScale;
		}
		if (surface == null) {
			surface = ClientAdapter.INSTANCE.offscreenGui("Quest Plus tablet", STATE, atlasWidth(), atlasHeight(), TEXTURE);
		}
		RenderSystem.backupProjectionMatrix();
		try {
			rendering = true;
			dev.questplus.fabric.client.ui.QuestSkin.renderScale = pixelScale * uiScale();
			var point = TabletFaceAnchor.pointer(mc.mouseHandler.getScaledXPos(mc.getWindow()),
					mc.mouseHandler.getScaledYPos(mc.getWindow()));
			int x = point == null ? -10000 : (int) (point.x() / uiScale()),
					y = point == null ? -10000 : (int) (point.y() / uiScale());
			if (!(ClientAdapter.INSTANCE.screen(mc) instanceof QuestScreen)) {
				x = -10000;
				y = -10000;
			}
			var graphics = new GuiGraphicsExtractor(mc, STATE, x, y);
			// The pass runs at GUI scale pixelScale: this root keeps every coordinate below in atlas pixels.
			graphics.pose().pushMatrix();
			graphics.pose().scale(1f / pixelScale);
			ClientAdapter.INSTANCE.blitTextured(graphics, BODY, 0, 0, 0, 0, 256, 256, 256, 256);
			graphics.nextStratum();
			graphics.pose().pushMatrix();
			graphics.pose().translate(screenX(), 0);
			graphics.pose().scale(pixelScale * uiScale());
			graphics.enableScissor(0, 0, logicalWidth(), logicalHeight());
			screen().extractTablet(graphics, x, y, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			// Dim only the 3D atlas, not the 2D fallback. Applied in a separate
			// stratum, after the UI, to preserve all authored colors and icons.
			double brightness = TabletShaderCompat.screenBrightness(QuestPlusClient.settings.screenBrightness,
					TabletShaderCompat.illuminatedScreen());
			if (brightness < .999) {
				graphics.nextStratum();
				int darkness = (int) Math.round((1 - brightness) * 255);
				graphics.fill(0, 0, logicalWidth(), logicalHeight(), darkness << 24);
			}
			graphics.extractDeferredElements(x, y, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			graphics.disableScissor();
			graphics.pose().popMatrix();
			graphics.pose().popMatrix();
			surface.render(pixelScale);
		} finally {
			rendering = false;
			dev.questplus.fabric.client.ui.QuestSkin.renderScale = 0;
			RenderSystem.restoreProjectionMatrix();
		}
	}

	public static void close() {
		if (surface != null)
			surface.close();
		surface = null;
	}
}
