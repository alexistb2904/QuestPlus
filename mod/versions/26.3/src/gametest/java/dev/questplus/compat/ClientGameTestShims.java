package dev.questplus.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.MinecraftServer;

/** Client test-only APIs that differ on Minecraft 26.3. Compiled into this target's GameTests only, never shipped. */
public final class ClientGameTestShims {
	public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYBOARD;

	private ClientGameTestShims() {}

	public static void waitForServer(ClientGameTestContext context, TestServerContext server,
			Predicate<MinecraftServer> condition, int timeout) {
		server.waitFor(condition, timeout);
	}

	public static Overlay overlay(Minecraft minecraft) {
		return minecraft.gui.overlay();
	}

	public static ToastManager toasts(Minecraft minecraft) {
		return minecraft.gui.toastManager();
	}

	public static java.nio.file.Path screenshot(ClientGameTestContext context, String name) {
		return context.takeScreenshot(name);
	}
}
