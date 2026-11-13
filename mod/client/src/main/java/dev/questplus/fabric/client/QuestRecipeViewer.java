package dev.questplus.fabric.client;

import dev.questplus.platform.Loader;
import dev.questplus.compat.ClientAdapter;
import dev.questplus.fabric.QuestPlusMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class QuestRecipeViewer {
	record Session(QuestScreen parent, Screen recipes) {
		boolean contains(Screen current) {
			return current != null && (current == parent || current == recipes);
		}
	}

	private static Session session;

	static QuestScreen tabletScreen() {
		var current = ClientAdapter.INSTANCE.screen(Minecraft.getInstance());
		if (session != null) {
			if (session.contains(current))
				return session.parent();
			clear();
		}
		return current instanceof QuestScreen quest ? quest : null;
	}

	static void clear() {
		session = null;
	}

	/** Keeps the tablet held while a screen that {@code parent} just opened is shown. */
	static void hold(QuestScreen parent) {
		var current = ClientAdapter.INSTANCE.screen(Minecraft.getInstance());
		session = current != null && current != parent ? new Session(parent, current) : null;
	}

	record Target(ItemStack stack, int x, int y) {
		boolean contains(double mx, double my, int clipTop, int clipBottom) {
			return mx >= x && mx < x + 16 && my >= Math.max(y, clipTop) && my < Math.min(y + 16, clipBottom);
		}
	}

	private QuestRecipeViewer() {
	}

	static boolean available() {
		return Loader.isModLoaded("roughlyenoughitems") || Loader.isModLoaded("jei");
	}

	static boolean open(ItemStack stack) {
		if (stack.isEmpty())
			return false;
		var client = Minecraft.getInstance();
		var parent = ClientAdapter.INSTANCE.screen(client);
		boolean opened = openViewer(stack);
		session = opened && parent instanceof QuestScreen quest && ClientAdapter.INSTANCE.screen(client) != parent
				&& ClientAdapter.INSTANCE.screen(client) != null ? new Session(quest, ClientAdapter.INSTANCE.screen(client)) : null;
		return opened;
	}

	private static boolean openViewer(ItemStack stack) {
		try {
			if (Loader.isModLoaded("roughlyenoughitems") && Rei.open(stack))
				return true;
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Cannot open REI recipes", error);
		}
		try {
			if (Loader.isModLoaded("jei"))
				return QuestJeiPlugin.open(stack);
		} catch (LinkageError | RuntimeException error) {
			QuestPlusMod.LOGGER.warn("Cannot open JEI recipes", error);
		}
		return false;
	}

	// Loaded only after the mod loader confirms REI is installed.
	private static final class Rei {
		static boolean open(ItemStack stack) {
			return me.shedaniel.rei.api.client.view.ViewSearchBuilder.builder()
					.addRecipesFor(me.shedaniel.rei.api.common.entry.EntryStack
							.of(me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes.ITEM, stack.copy()))
					.open();
		}
	}
}
