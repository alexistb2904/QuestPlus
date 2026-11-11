package dev.questplus.fabric.client;

import dev.questplus.compat.ClientAdapter;
import dev.questplus.fabric.QuestPlusMod;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * GPU regression matrix in an isolated run directory, selected by the
 * compatibility launcher.
 */
public final class TabletCompatibilityTest {
	static void run(ClientGameTestContext context) {
		var packs = new ArrayList<String>();
		packs.add("");
		try (var files = Files.list(FabricLoader.getInstance().getGameDir().resolve("shaderpacks"))) {
			packs.addAll(files.filter(p -> p.toString().endsWith(".zip")).map(p -> p.getFileName().toString()).sorted()
					.toList());
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			context.waitTicks(20);
			context.runOnClient(client -> {
				ClientAdapter.INSTANCE.setScreen(client, null);
				QuestPlusClient.settings.animations = true;
				QuestPlusClient.settings.tablet = true;
				QuestPlusClient.settings.reducedMotion = false;
				QuestPlusClient.settings.blur = false;
				client.options.fov().set(70);
				client.options.guiScale().set(2);
			});
			context.getInput().resizeWindow(1280, 720);
			for (String pack : packs) {
				String label = pack.isEmpty() ? "vanilla" : pack.replace(".zip", "");
				context.runOnClient(client -> shader(pack));
				context.waitTicks(40);
				context.runOnClient(client -> {
					if (TabletShaderCompat.shadersActive() != !pack.isEmpty())
						throw new AssertionError("Shader failed to activate: " + pack);
				});
				carryTrial(context, world.getServer(), label);
				for (boolean illuminated : List.of(true, false)) {
					String trial = label + (illuminated ? "-lit" : "-unlit");
					world.getServer().runCommand(
							"item replace entity @p weapon.mainhand with minecraft:" + (illuminated ? "air" : "glass"));
					world.getServer().runCommand(
							"item replace entity @p weapon.offhand with minecraft:" + (illuminated ? "air" : "glass"));
					context.waitTicks(3);
					context.runOnClient(client -> {
						QuestPlusClient.settings.screenEmissive = illuminated;
						QuestClientTabletFlow.toggle(client);
					});
					context.waitTicks(8);
					dev.questplus.compat.ClientGameTestShims.screenshot(context, trial + "-opening");
					context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
					context.waitTicks(4);
					context.runOnClient(client -> assertCameraOwnership(true));
					context.runOnClient(client -> TabletArmRenderingTest.verify(client, true));
					assertMatteScreen(dev.questplus.compat.ClientGameTestShims.screenshot(context, trial + "-held"));
					context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).onClose());
					context.waitTicks(5);
					var before = context.computeOnClient(client -> {
						if (!QuestClientTabletFlow.nativeHandsActive() || !TabletSurfaceRenderer.hasSurface()
								|| !"questplus.close".equals(TabletNativeAnimation.clip()))
							throw new AssertionError("Native close pose disappeared: " + trial);
						return TabletNativeAnimation.bone("tablet_root", "position");
					});
					dev.questplus.compat.ClientGameTestShims.screenshot(context, trial + "-closing-5");
					context.waitTicks(6);
					context.runOnClient(client -> {
						if (!QuestClientTabletFlow.nativeHandsActive()
								|| before.equals(TabletNativeAnimation.bone("tablet_root", "position")))
							throw new AssertionError("Native close pose did not advance: " + trial);
					});
					dev.questplus.compat.ClientGameTestShims.screenshot(context, trial + "-closing-11");
					context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 35);
					context.runOnClient(client -> {
						if (ClientAdapter.INSTANCE.screen(client) != null || TabletSurfaceRenderer.hidesHud())
							throw new AssertionError("HUD or screen retained after close: " + trial);
						assertCameraOwnership(false);
						TabletArmRenderingTest.verify(client, false);
					});
					dev.questplus.compat.ClientGameTestShims.screenshot(context, trial + "-closed");
					QuestPlusMod.LOGGER.info("TABLET COMPAT PASS {}", trial);
				}
			}
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
			world.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:air");
			context.waitTicks(3);
			context.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
			context.waitTicks(5);
			context.runOnClient(QuestClientTabletFlow::toggle);
			context.waitTicks(8);
			context.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_W);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).onClose());
			context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 35);
			QuestClientGameTest.remotePoseTrial(context, world.getServer());
			if (FabricLoader.getInstance().isModLoaded("punchy_moves"))
				rebindTrial(context);
			QuestPlusMod.LOGGER.info("TABLET MOVEMENT AND REMOTE POSE PASS");
		}
	}

	private static void carryTrial(ClientGameTestContext context,
			net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, String label) {
		server.runCommand("item replace entity @p weapon.mainhand with questplus:tablet");
		context.waitFor(client -> QuestClientTabletFlow.carrying() && TabletNativeArms.active(), 120);
		context.waitTicks(5);
		context.runOnClient(client -> {
			if (ClientAdapter.INSTANCE.screen(client) != null || TabletSurfaceRenderer.hidesHud() || TabletSurfaceRenderer.hasSurface())
				throw new AssertionError("Carry pose opened the menu or retained its surface");
			assertCameraOwnership(true);
		});
		dev.questplus.compat.ClientGameTestShims.screenshot(context, label + "-item-carried");
		server.runCommand("execute at @p run setblock ~ ~1 ~2 minecraft:chest");
		context.runOnClient(client -> {
			client.player.setYRot(0);
			client.player.setXRot(0);
		});
		context.waitTicks(3);
		context.runOnClient(client -> {
			if (client.hitResult == null || client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK)
				throw new AssertionError("Tablet block-use fixture is not targeted");
		});
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
		context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).onClose());
		context.waitFor(client -> QuestClientTabletFlow.carrying(), 120);
		dev.questplus.compat.ClientGameTestShims.screenshot(context, label + "-item-carried-after-close");
		server.runCommand("execute at @p run setblock ~ ~1 ~2 minecraft:air");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
		context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 60);
		context.runOnClient(client -> assertCameraOwnership(false));
		QuestPlusMod.LOGGER.info("TABLET ITEM CARRY PASS {}", label);
	}

	private static void rebindTrial(ClientGameTestContext context) {
		var old = context.computeOnClient(client -> {
			try {
				var handler = Class.forName("punchymoves.client.emote.EmoteKeybindManager").getMethod("handleKey",
						net.minecraft.client.input.KeyEvent.class);
				var j = new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_J, 0, 0);
				if (Boolean.TRUE.equals(handler.invoke(null, j)))
					throw new AssertionError("Punchy consumed the tablet shortcut");
				var key = java.util.Arrays.stream(client.options.keyMappings)
						.filter(k -> k.getName().equals("questplus.client.open")).findFirst().orElseThrow();
				var previous = com.mojang.blaze3d.platform.InputConstants.getKey(key.saveString());
				key.setKey(dev.questplus.compat.ClientGameTestShims.KEYBOARD
						.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_K));
				net.minecraft.client.KeyMapping.resetMapping();
				if (!Boolean.TRUE.equals(handler.invoke(null, j)))
					throw new AssertionError("Rebinding did not restore the Punchy shortcut");
				return previous;
			} catch (ReflectiveOperationException e) {
				throw new AssertionError(e);
			}
		});
		try {
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_K);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.runOnClient(client -> ClientAdapter.INSTANCE.screen(client).onClose());
			context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 60);
			QuestPlusMod.LOGGER.info("TABLET PUNCHY REBIND PASS");
		} finally {
			context.runOnClient(client -> {
				java.util.Arrays.stream(client.options.keyMappings)
						.filter(k -> k.getName().equals("questplus.client.open")).findFirst().orElseThrow().setKey(old);
				net.minecraft.client.KeyMapping.resetMapping();
			});
		}
	}

	private static void assertCameraOwnership(boolean ownsHands) {
		if (!FabricLoader.getInstance().isModLoaded("firstperson"))
			return;
		try {
			var api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
			var handlers = (List<?>) api.getMethod("getActivationHandlers").invoke(null);
			var hook = handlers.stream().filter(h -> h.toString().equals("QuestPlus tablet")).findFirst().orElseThrow();
			var handler = Class.forName("dev.tr7zw.firstperson.api.ActivationHandler");
			if (!Boolean.valueOf(ownsHands).equals(handler.getMethod("preventFirstperson").invoke(hook)))
				throw new AssertionError("First Person Model did not hand over and restore the camera");
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private static void assertMatteScreen(java.nio.file.Path screenshot) {
		try {
			var image = javax.imageio.ImageIO.read(screenshot.toFile());
			int parchment = 0, total = 0;
			// Centre of the held display at the fixed test camera, away from arms and world
			// geometry. The parchment UI is warm when lit and neutral grey when world-lit; sky or
			// grass behind a missing display is blue or green, and a specular glare is saturated white.
			for (int y = image.getHeight() * 35 / 100; y < image.getHeight() * 60 / 100; y++)
				for (int x = image.getWidth() * 35 / 100; x < image.getWidth() * 65 / 100; x++) {
					int rgb = image.getRGB(x, y), r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
					if (r >= b && g <= r + 15 && Math.min(r, Math.min(g, b)) < 235)
						parchment++;
					total++;
				}
			if (parchment < total / 2)
				throw new AssertionError("Tablet display is missing or reflective: " + screenshot + " (parchment pixels "
						+ parchment + "/" + total + ")");
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	public static void shader(String pack) {
		try {
			var iris = Class.forName("net.irisshaders.iris.Iris");
			var config = iris.getMethod("getIrisConfig").invoke(null);
			config.getClass().getMethod("setShaderPackName", String.class).invoke(config, pack);
			config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, !pack.isEmpty());
			config.getClass().getMethod("save").invoke(config);
			iris.getMethod("reload").invoke(null);
			if (!pack.isEmpty() && Boolean.TRUE.equals(iris.getMethod("isFallback").invoke(null)))
				throw new AssertionError("Iris fell back to vanilla rendering: " + pack);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Cannot switch Iris shader", e);
		}
	}
}
