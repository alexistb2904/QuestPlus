package dev.questplus.fabric.client;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class RecipeReturnGameTest {
	static void run(ClientGameTestContext context) {
		try (var world = context.worldBuilder().create()) {
			context.waitFor(client -> QuestPlusClient.STATE.ready, 600);
			context.runOnClient(client -> {
				client.gui.setScreen(null);
				QuestPlusClient.settings.animations = true;
				QuestPlusClient.settings.tablet = true;
				QuestPlusClient.settings.reducedMotion = false;
				QuestPlusClient.settings.blur = false;
				QuestPlusClient.settings.stayOpenOnDamage = true;
				QuestClientTabletFlow.toggle(client);
			});
			context.waitFor(client -> TabletSurfaceRenderer.active(), 160);
			var parent = context.computeOnClient(client -> (QuestScreen) client.gui.screen());
			context.runOnClient(client -> {
				if (!QuestRecipeViewer.open(new ItemStack(Items.CRAFTING_TABLE)))
					throw new AssertionError("Recipes did not open");
				if (client.gui.screen() == parent)
					throw new AssertionError("Recipe viewer left the quest screen active");
			});
			context.waitTicks(20);
			context.runOnClient(client -> {
				if (!QuestClientTabletFlow.nativeHandsActive() || !TabletSurfaceRenderer.hasSurface()
						|| TabletSurfaceRenderer.screen() != parent)
					throw new AssertionError("Recipes released the 3D tablet");
			});
			context.takeScreenshot("recipes-tablet-behind");
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(10);
			context.runOnClient(client -> {
				if (client.gui.screen() != parent || !TabletSurfaceRenderer.active())
					throw new AssertionError("Recipes did not return to the 3D tablet");
				try {
					var attached = QuestScreen.class.getDeclaredField("attachedToScreenFace");
					attached.setAccessible(true);
					if (!attached.getBoolean(parent))
						throw new AssertionError("Returned tablet uses 2D fallback");
				} catch (ReflectiveOperationException error) {
					throw new AssertionError(error);
				}
			});
			context.takeScreenshot("recipes-tablet-return");
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitFor(client -> !QuestClientTabletFlow.nativeHandsActive(), 160);
			context.runOnClient(client -> {
				if (TabletSurfaceRenderer.hidesHud())
					throw new AssertionError("Closing kept the HUD hidden");
				QuestClientTabletFlow.toggle(client);
			});
			context.waitFor(client -> TabletSurfaceRenderer.active(), 160);
			context.setScreen(() -> new Screen(Component.empty()) {
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				if (QuestClientTabletFlow.nativeHandsActive() || TabletSurfaceRenderer.hidesHud())
					throw new AssertionError("An unrelated screen retained the tablet");
			});
		}
	}
}
