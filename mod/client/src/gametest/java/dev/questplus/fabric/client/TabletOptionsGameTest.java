package dev.questplus.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.input.KeyEvent;
import java.nio.file.Files;

/** Exercises the actual FP PAL pose, framebuffer and 2D fallback. */
final class TabletOptionsGameTest {

	static void run(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(client -> {
				try {
					var file = FabricLoader.getInstance().getConfigDir().resolve("questplus-client.json");
					QuestPlusClient.settings.save();
					var json = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
					json.remove("treeResolution");
					json.addProperty("scale", 1.25);
					Files.writeString(file, json.toString());
					if (ClientSettings.load().treeResolution != 6 || ClientSettings.load().scale != 1.25)
						throw new AssertionError("Old config lost preferences or new resolution default");
					for (int value : new int[] { 0, 99 }) {
						json.addProperty("treeResolution", value);
						Files.writeString(file, json.toString());
						if (ClientSettings.load().treeResolution != Math.clamp(value, 1, 8)
								|| ClientSettings.load().scale != 1.25)
							throw new AssertionError("Invalid resolution lost existing preferences");
					}
				} catch (java.io.IOException error) {
					throw new AssertionError(error);
				}
			});
			for (boolean tablet : new boolean[] { true, false }) {
				context.runOnClient(client -> {
					client.gui.setScreen(null);
					QuestPlusClient.settings.tablet = tablet;
					QuestPlusClient.settings.animations = false;
					QuestPlusClient.settings.tabletTransitions = true;
					client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					QuestClientTabletFlow.toggle(client);
					if (!(client.gui.screen() instanceof QuestScreen) || TabletSurfaceRenderer.active() != tablet)
						throw new AssertionError("Animations disabled changed 3D selection: " + tablet);
					if (client.options.getCameraType() != (tablet ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK))
						throw new AssertionError("Incorrect camera for tablet mode " + tablet);
				});
				context.waitTicks(5);
				context.takeScreenshot("tablet-options-" + (tablet ? "3d" : "2d"));
				context.runOnClient(client -> {
					client.gui.screen().onClose();
					if (client.gui.screen() != null || QuestClientTabletFlow.nativeHandsActive()
							|| client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
						throw new AssertionError("Instant close retained screen, hands or camera");
				});
			}
			context.runOnClient(client -> {
				QuestPlusClient.settings.tablet = true;
				QuestPlusClient.settings.animations = true;
				QuestPlusClient.settings.reducedMotion = true;
				QuestClientTabletFlow.toggle(client);
				if (!TabletSurfaceRenderer.active())
					throw new AssertionError("Reduced motion removed the 3D tablet");
			});
			context.waitTicks(5);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.runOnClient(client -> {
				QuestPlusClient.settings.reducedMotion = false;
				QuestPlusClient.settings.tabletTransitions = false;
				QuestPlusClient.settings.scale = 1.25;
				QuestClientTabletFlow.toggle(client);
				if (!TabletSurfaceRenderer.active())
					throw new AssertionError("Disabling transitions removed the 3D tablet");
			});
			context.waitTicks(5);
			for (int resolution : new int[] { 1, 3, 6, 8, 2 }) {
				context.runOnClient(client -> QuestPlusClient.settings.treeResolution = resolution);
				context.waitTicks(3);
				context.runOnClient(client -> {
					if (TabletSurfaceRenderer.screenWidth() != 500 * resolution
							|| TabletSurfaceRenderer.screenHeight() != 300 * resolution
							|| client.gui.screen().width != TabletSurfaceRenderer.logicalWidth())
						throw new AssertionError("Resolution changed logical layout or framebuffer size");
				});
				QuestClientGameTest.clickButton(context, "settings");
				context.takeScreenshot("tablet-resolution-" + resolution + "-settings");
				QuestClientGameTest.clickButton(context, "tree_resolution", resolution);
				context.runOnClient(client -> {
					int expected = resolution == 8 ? 1 : resolution + 1;
					if (QuestPlusClient.settings.treeResolution != expected || ClientSettings.load().treeResolution != expected)
						throw new AssertionError("Resolution button or persistence failed");
				});
				context.runOnClient(client -> QuestPlusClient.settings.treeResolution = resolution);
				context.waitTicks(3);
				QuestClientGameTest.clickButton(context, "quests");
				context.takeScreenshot("tablet-resolution-" + resolution + "-tree");
				context.runOnClient(client -> {
					for (int i = 0; i < 5; i++)
						client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_MINUS, 0, 0));
				});
				context.waitTicks(2);
				context.takeScreenshot("tablet-resolution-" + resolution + "-zoomed-out");
				context.runOnClient(client -> {
					for (int i = 0; i < 5; i++)
						client.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_EQUALS, 0, 0));
				});
			}
			context.runOnClient(client -> {
				client.gui.screen().onClose();
				if (QuestClientTabletFlow.nativeHandsActive())
					throw new AssertionError("Disabling transitions retained the close pose");
			});
			context.runOnClient(client -> {
				QuestPlusClient.settings.tabletTransitions = true;
				QuestClientTabletFlow.toggle(client);
				if (client.gui.screen() != null || !QuestClientTabletFlow.nativeHandsActive())
					throw new AssertionError("Enabled transitions skipped the opening animation");
			});
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.runOnClient(client -> {
				client.gui.screen().onClose();
				if (!QuestClientTabletFlow.closing())
					throw new AssertionError("Enabled transitions skipped the closing animation");
			});
			context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 120);
		}
	}
}
