package dev.questplus.fabric.client;

import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import team.reborn.energy.api.EnergyStorage;

/**
 * Actual rendered tablet actions, network requests and server-side Tech Reborn
 * withdrawals.
 */
final class ResourceClientGameTest {
	static void run(ClientGameTestContext context, TestServerContext server) {
		String quest = "questplus:client_tech_resources";
		String[] category = { null };
		server.runOnServer(instance -> {
			try {
				var runtime = dev.questplus.fabric.QuestPlusMod.runtime();
				category[0] = runtime.categories().getFirst().id();
				Files.writeString(
						instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests/client-tech.json5"),
						"""
								{schemaVersion:2,id:'questplus:client_tech_resources',category:'%s',icon:'techreborn:red_cell_battery',
								 title:{en_us:'Tech Reborn resources'},description:{en_us:'Submit battery energy, then an oil cell.'},
								 progression:'once',objectiveMode:'sequence',objectives:[
								  {id:'energy',event:'energy_submit',target:'questplus:fe',count:200,data:{fePerEnergy:10}},
								  {id:'oil',event:'fluid_submit',target:'techreborn:oil',count:1000}],rewards:[]}
								"""
								.formatted(category[0]));
				runtime.reload();
				var player = instance.getPlayerList().getPlayers().getFirst();
				player.setGameMode(GameType.SURVIVAL);
				var battery = new ItemStack(BuiltInRegistries.ITEM
						.getOptional(Identifier.parse("techreborn:red_cell_battery")).orElseThrow());
				battery.set(EnergyStorage.ENERGY_COMPONENT, 100L);
				player.getInventory().setItem(5, battery);
				player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(
						BuiltInRegistries.ITEM.getOptional(Identifier.parse("techreborn:oil_cell")).orElseThrow()));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		context.waitFor(client -> QuestPlusClient.STATE.quests.containsKey(quest), 300);
		context.setScreen(() -> {
			var screen = new QuestScreen();
			try {
				var selected = QuestScreen.class.getDeclaredField("selected");
				selected.setAccessible(true);
				selected.set(screen, quest);
				var cat = QuestScreen.class.getDeclaredField("category");
				cat.setAccessible(true);
				cat.set(screen, category[0]);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return screen;
		});
		context.waitTicks(8);
		context.takeScreenshot("questplus-tech-resources-before");
		QuestClientGameTest.clickButton(context, "submit");
		server.waitFor(instance -> {
			try {
				var p = instance.getPlayerList().getPlayers().getFirst();
				var state = dev.questplus.fabric.QuestPlusMod.runtime().state(p.getUUID()).quests.get(quest);
				return state != null && state.counters.getOrDefault("energy", 0L) == 200;
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}, 300);
		context.waitFor(client -> !QuestClientState.flag(QuestPlusClient.STATE.progress(quest), "saving"), 300);
		context.waitTicks(5);
		context.takeScreenshot("questplus-tech-resources-energy");
		QuestClientGameTest.clickButton(context, "submit");
		context.waitFor(client -> QuestClientState.flag(QuestPlusClient.STATE.progress(quest), "completed"), 300);
		server.runOnServer(instance -> {
			var player = instance.getPlayerList().getPlayers().getFirst();
			if (player.getInventory().getItem(5).getOrDefault(EnergyStorage.ENERGY_COMPONENT, 0L) != 80
					|| !player.getOffhandItem()
							.is(BuiltInRegistries.ITEM.getOptional(Identifier.parse("techreborn:cell")).orElseThrow()))
				throw new AssertionError("Tablet resource actions did not preserve battery excess and empty cell");
		});
		context.waitTicks(8);
		context.takeScreenshot("questplus-tech-resources-complete");
	}
}
