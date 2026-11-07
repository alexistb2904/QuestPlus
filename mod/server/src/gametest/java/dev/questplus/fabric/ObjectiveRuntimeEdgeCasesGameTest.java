package dev.questplus.fabric;

import dev.questplus.core.QuestEvent;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;

public final class ObjectiveRuntimeEdgeCasesGameTest {
	@GameTest(maxTicks = 400)
	public void sampledConditionsCannotKeepStaleInventoryOrXp(GameTestHelper helper) throws Exception {
		var f = new ObjectiveTestFixture(helper, "stale_state", """
				[{id:'items',event:'inventory_count',target:'minecraft:stone',count:5,conditions:{sneaking:true}},
				 {id:'xp',event:'xp_levels',target:'questplus:levels',count:5,conditions:{sneaking:true}}]
				""");
		f.player.setShiftKeyDown(true);
		f.player.getInventory().setItem(4, new ItemStack(Items.STONE, 5));
		f.player.giveExperienceLevels(5);
		helper.startSequence().thenWaitUntil(() -> {
			f.expect("items", 5);
			f.expect("xp", 5);
		}).thenExecute(() -> f.player.setShiftKeyDown(false)).thenWaitUntil(() -> {
			f.expect("items", 0);
			f.expect("xp", 0);
		}).thenExecute(() -> {
			try {
				f.runtime.interactObjective(f.player, f.quest, "guard");
				helper.assertTrue(!f.runtime.state(f.player.getUUID()).quests.get(f.quest).completed,
						"Manual acknowledgement cannot complete a quest using stale conditional state");
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}).thenExecute(f::close).thenSucceed();
	}

	@GameTest(maxTicks = 100)
	public void creativeAndSpectatorCannotAdvanceAnyGameplayTrigger(GameTestHelper helper) throws Exception {
		com.google.gson.JsonObject coverage;
		try (var reader = new java.io.InputStreamReader(
				Objects.requireNonNull(getClass().getResourceAsStream("/objective-coverage.json")),
				java.nio.charset.StandardCharsets.UTF_8)) {
			coverage = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
		}
		var goals = new com.google.gson.JsonArray();
		for (var entry : coverage.entrySet())
			for (var value : entry.getValue().getAsJsonArray()) {
				String event = value.getAsString();
				String target = switch (event) {
					case "custom_stat" -> "minecraft:jump";
					case "advancement_complete" -> "minecraft:story/mine_stone";
					case "travel_distance" -> "questplus:travel";
					case "location" -> "questplus:position";
					case "player_death" -> "questplus:death";
					default -> switch (dev.questplus.core.QuestExtensions.event(event).registry()) {
						case "block", "item" -> "minecraft:stone";
						case "entity" -> "minecraft:zombie";
						case "fluid" -> "minecraft:water";
						case "dimension" -> "minecraft:overworld";
						case "biome" -> "minecraft:plains";
						case "structure" -> "minecraft:desert_pyramid";
						default -> "questplus:fe";
					};
				};
				var goal = new com.google.gson.JsonObject();
				goal.addProperty("id", event.replace(':', '_'));
				goal.addProperty("event", event);
				goal.addProperty("target", target);
				goal.addProperty("count", 10);
				goals.add(goal);
			}
		try (var f = new ObjectiveTestFixture(helper, "mode_filter", goals.toString())) {
			for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
				f.player.setGameMode(mode);
				var progress = f.runtime.player(f.player.getUUID());
				long revision = progress.revision;
				for (var entry : coverage.entrySet())
					for (var value : entry.getValue().getAsJsonArray()) {
						String type = value.getAsString();
						if (type.equals("manual"))
							continue;
						for (var objective : f.runtime.registry().objectives(type)) {
							String target = objective.target();
							f.runtime.event(f.player, new QuestEvent(type,
									target.startsWith("#") ? "minecraft:air" : target,
									target.startsWith("#") ? Set.of(target.substring(1)) : Set.of(), 1_000_000_000,
									Map.of("inventory", Map.of(target, 1_000_000_000L),
											"stateValues", Map.of(target, 1_000_000_000L))));
						}
					}
				helper.assertTrue(progress.revision == revision, mode + " did not mutate any gameplay progress");
			}
			f.runtime.interactObjective(f.player, f.quest, "manual");
			f.expect("manual", 1);
		}
		helper.succeed();
	}
}
