package dev.questplus.compat;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.MinecraftServer;

/** Client test-only APIs that differ on Minecraft 26.1. Compiled into this target's GameTests only, never shipped. */
public final class ClientGameTestShims {
	public static final InputConstants.Type KEYBOARD = InputConstants.Type.KEYSYM;

	private ClientGameTestShims() {}

	public static void waitForServer(ClientGameTestContext context, TestServerContext server,
			Predicate<MinecraftServer> condition, int timeout) {
		// Fabric API for 26.1 has no TestServerContext.waitFor: poll once per tick from the test thread.
		for (int tick = 0; !server.computeOnServer(condition::test); tick++) {
			if (tick >= timeout)
				throw new AssertionError("Server condition not met within " + timeout + " ticks");
			context.waitTick();
		}
	}

	public static Overlay overlay(Minecraft minecraft) {
		return minecraft.getOverlay();
	}

	public static ToastManager toasts(Minecraft minecraft) {
		return minecraft.getToastManager();
	}

	/**
	 * Fabric API's capture renders an extra frame; on 26.1 that frame reaches Sodium after its
	 * terrain uniforms were released ("Global terrain uniforms have not been updated"), so read the
	 * frame Minecraft rendered itself on the next tick instead.
	 */
	public static java.nio.file.Path screenshot(ClientGameTestContext context, String name) {
		context.waitTick();
		var saved = new java.util.concurrent.CompletableFuture<java.nio.file.Path>();
		context.runOnClient(client -> net.minecraft.client.Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
			try (image) {
				var directory = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("screenshots");
				java.nio.file.Files.createDirectories(directory);
				var file = directory.resolve(String.format("%04d_%s.png", SCREENSHOTS.getAndIncrement(), name));
				image.writeToFile(file);
				saved.complete(file);
			} catch (Throwable error) {
				saved.completeExceptionally(error);
			}
		}));
		while (!saved.isDone())
			context.waitTick();
		return saved.join();
	}

	private static final java.util.concurrent.atomic.AtomicInteger SCREENSHOTS = new java.util.concurrent.atomic.AtomicInteger();
}
