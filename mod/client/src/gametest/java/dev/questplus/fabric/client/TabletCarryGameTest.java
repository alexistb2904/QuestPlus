package dev.questplus.fabric.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class TabletCarryGameTest {
	static void run(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			context.runOnClient(client -> {
				client.gui.setScreen(null);
				QuestPlusClient.settings.animations = true;
				QuestPlusClient.settings.tablet = true;
				QuestPlusClient.settings.tabletTransitions = true;
				QuestPlusClient.settings.reducedMotion = false;
				QuestPlusClient.settings.blur = false;
				client.options.fov().set(70);
				client.options.guiScale().set(2);
			});
			context.getInput().resizeWindow(1280, 720);
			world.getServer().runCommand("gamemode creative @p");
			context.waitTicks(5);
			context.runOnClient(client -> {
				var screen = new CreativeModeInventoryScreen(client.player, client.level.enabledFeatures(), true);
				client.gui.setScreen(screen);
				var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse("questplus:quest"));
				if (tab == null || !((FabricCreativeModeInventoryScreen) screen).setSelectedTab(tab)
						|| !tab.contains(BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:tablet"))
								.getDefaultInstance())
						|| !tab.contains(BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:loot_crate"))
								.getDefaultInstance()))
					throw new AssertionError("Quest creative tab is missing its items");
			});
			context.waitTicks(4);
			context.takeScreenshot("questplus-creative-quest");
			context.runOnClient(client -> client.gui.setScreen(null));
			terminalFrameTrial(context);
			emptyHandTrial(context);
			world.getServer().runCommand("item replace entity @p weapon.mainhand with questplus:tablet");
			context.waitTicks(5);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("questplus-local-carried-third-person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			carryResetTrial(context);
			firstPersonItemTrial(context);
			world.getServer().runCommand(
					"execute at @p run summon minecraft:villager ~ ~ ~2 {NoAI:1b,Invulnerable:1b,Tags:[\"tablet_test\"],VillagerData:{profession:\"minecraft:farmer\",level:2,type:\"minecraft:plains\"}}");
			context.getInput().lookAt(0, 0);
			context.waitFor(client -> client.hitResult instanceof net.minecraft.world.phys.EntityHitResult, 60);
			context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 60);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitFor(client -> QuestClientTabletFlow.carrying(), 60);
			world.getServer().runCommand("kill @e[tag=tablet_test]");
			try (var files = java.nio.file.Files
					.list(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("shaderpacks"))) {
				for (String shader : files.map(path -> path.getFileName().toString())
						.filter(name -> name.endsWith(".zip")).sorted().toList()) {
					context.runOnClient(client -> TabletCompatibilityTest.shader(shader));
					context.waitTicks(30);
					context.runOnClient(client -> {
						if (!TabletShaderCompat.shadersActive())
							throw new AssertionError("Shader did not activate: " + shader);
					});
					world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
					context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 60);
					emptyHandTrial(context);
					world.getServer().runCommand("item replace entity @p weapon.mainhand with questplus:tablet");
					carryResetTrial(context);
					firstPersonItemTrial(context);
					dev.questplus.fabric.QuestPlusMod.LOGGER.info("TABLET ITEM SHADER PASS {}", shader);
				}
				context.runOnClient(client -> TabletCompatibilityTest.shader(""));
				context.waitTicks(5);
			} catch (java.io.IOException error) {
				throw new AssertionError(error);
			}
			world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");
			context.waitTicks(5);
			QuestClientGameTest.remotePoseTrial(context, world.getServer());
			dev.questplus.fabric.QuestPlusMod.LOGGER.info("TABLET THIRD PERSON CARRY AND CREATIVE TAB PASS");
		}
	}

	private static void terminalFrameTrial(ClientGameTestContext context) {
		context.runOnClient(QuestClientTabletFlow::toggle);
		context.waitFor(client -> TabletSurfaceRenderer.active(), 60);
		context.runOnClient(client -> client.gui.screen().onClose());
		context.waitTicks(19);
		context.runOnClient(client -> {
			if (!TabletNativeAnimation.active() || !"questplus.close".equals(TabletNativeAnimation.clip()))
				throw new AssertionError("Native closing animation lost its final pose");
		});
		context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 5);
	}

	private static void emptyHandTrial(ClientGameTestContext context) {
		for (int cycle = 0; cycle < 4; cycle++) {
			context.runOnClient(QuestClientTabletFlow::toggle);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.runOnClient(client -> TabletArmRenderingTest.verify(client, true));
			context.runOnClient(client -> client.gui.screen().onClose());
			if (cycle == 0) {
				context.waitTicks(5);
				var before = context.computeOnClient(client -> TabletNativeAnimation.bone("tablet_root", "position"));
				context.waitTicks(6);
				context.runOnClient(client -> {
					if (!QuestClientTabletFlow.closing() || !TabletSurfaceRenderer.hasSurface()
							|| distanceSquared(before, TabletNativeAnimation.bone("tablet_root", "position")) < 1e-7)
						throw new AssertionError("Empty-hand close animation did not advance");
				});
				context.takeScreenshot("tablet-empty-hand-closing");
			}
			try {
				context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 35);
			} catch (AssertionError error) {
				context.runOnClient(client -> dev.questplus.fabric.QuestPlusMod.LOGGER.error(
						"STUCK NATIVE CLOSE clip={} seconds={} active={}", TabletNativeAnimation.clip(),
						TabletNativeAnimation.seconds(), TabletNativeAnimation.active()));
				context.takeScreenshot("tablet-stuck-close");
				throw error;
			}
			context.runOnClient(client -> TabletArmRenderingTest.verify(client, false));
		}
	}

	private static void carryResetTrial(ClientGameTestContext context) {
		context.waitFor(client -> QuestClientTabletFlow.carrying(), 60);
		context.waitTicks(5);
		var expected = context.computeOnClient(client -> TabletNativeAnimation.bone("tablet_root", "position"));
		for (int cycle = 0; cycle < 3; cycle++) {
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_2);
			context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 30);
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1);
			context.waitFor(client -> QuestClientTabletFlow.carrying(), 30);
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_E);
			context.waitTicks(3);
			context.runOnClient(client -> {
				if (QuestClientTabletFlow.nativeHandsActive())
					throw new AssertionError("Inventory retained the carry animation");
			});
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
			context.waitFor(client -> QuestClientTabletFlow.carrying(), 30);
			context.waitTicks(5);
			context.runOnClient(client -> {
				if (!TabletNativeArms.active()
						|| distanceSquared(expected, TabletNativeAnimation.bone("tablet_root", "position")) > 1e-5)
					throw new AssertionError("Picking up the tablet changed its carry pose");
			});
		}
		context.takeScreenshot("tablet-carried-after-slot-and-inventory");
	}

	private static double distanceSquared(TabletFirstPersonAnimation.Vector a, TabletFirstPersonAnimation.Vector b) {
		double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
		return x * x + y * y + z * z;
	}


	private static void firstPersonItemTrial(ClientGameTestContext context) {
		context.getInput().lookAt(0, -45);
		context.waitFor(
				client -> QuestClientTabletFlow.carrying() && TabletNativeArms.active()
						&& TabletNativeAnimation.bone("tablet_root", "position") != null,
				120);
		context.runOnClient(client -> client.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
		context.waitTicks(5);
		context.runOnClient(client -> {
			if (!QuestClientTabletFlow.carrying() || !TabletNativeArms.active())
				throw new AssertionError("Chat interrupted the tablet carry pose");
			client.gui.setScreen(null);
		});
		context.waitTicks(2);
		var before = context.computeOnClient(client -> TabletNativeAnimation.bone("tablet_root", "position"));
		context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(1);
		context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
		context.waitFor(client -> QuestClientTabletFlow.striking(), 30);
		context.waitTicks(2);
		context.runOnClient(client -> {
			if (distanceSquared(before, TabletNativeAnimation.bone("tablet_root", "position")) < .1)
				throw new AssertionError("Left click did not move the custom tablet");
		});
		context.takeScreenshot("questplus-tablet-attack");
		context.waitFor(client -> QuestClientTabletFlow.carrying(), 60);
		for (int cycle = 0; cycle < 2; cycle++) {
			context.runOnClient(QuestClientTabletFlow::toggle);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 120);
			context.runOnClient(client -> client.gui.screen().onClose());
			for (int tick = 0; tick < 25; tick++) {
				context.waitTicks(1);
				context.runOnClient(client -> {
					if (!QuestClientTabletFlow.nativeHandsActive() || !TabletNativeArms.active()
							|| !TabletNativeAnimation.active())
						throw new AssertionError("Closing exposed a generic item frame");
				});
			}
			if (!context.computeOnClient(client -> QuestClientTabletFlow.carrying()))
				throw new AssertionError("Close did not return to the carry pose");
		}
		for (boolean useItem : new boolean[] { true, false }) {
			context.runOnClient(QuestClientTabletFlow::toggle);
			context.waitFor(client -> TabletSurfaceRenderer.active(), 60);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(2);
			if (useItem) {
				context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
				context.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
			} else
				context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_J);
			context.waitFor(client -> {
				if (!QuestClientTabletFlow.nativeHandsActive()
						|| !TabletNativeAnimation.active())
					throw new AssertionError("Reopening released the native tablet pose");
				return TabletSurfaceRenderer.active();
			}, 60);
			context.takeScreenshot("tablet-reopened-" + (useItem ? "click" : "key"));
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitFor(client -> QuestClientTabletFlow.carrying(), 60);
		}
		context.getInput().lookAt(0, 0);
		dev.questplus.fabric.QuestPlusMod.LOGGER.info("TABLET STRIKE AND SEAMLESS CLOSE PASS");
	}
}
