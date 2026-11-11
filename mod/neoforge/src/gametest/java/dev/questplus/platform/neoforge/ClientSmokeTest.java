package dev.questplus.platform.neoforge;

import com.google.gson.JsonObject;
import dev.questplus.compat.ClientAdapter;
import dev.questplus.compat.CommonAdapter;
import dev.questplus.compat.TargetAudit;
import dev.questplus.fabric.QuestGiver;
import dev.questplus.fabric.QuestWorldBlocks;
import dev.questplus.fabric.client.QuestPlusClient;
import dev.questplus.fabric.client.QuestScreen;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * NeoForge client smoke test ({@code ./gradlew :neoforge-<mc>:clientSmokeTest}, needs a window). A real
 * client starts, applies every mixin, creates a singleplayer world, receives the quest catalogue over
 * the network, renders the tablet screen, the three utility blocks and a Quest Giver, then quits and
 * writes the result for Gradle. Fabric covers the same ground with its client GameTests.
 */
@EventBusSubscriber(modid = "questplus", value = Dist.CLIENT)
public final class ClientSmokeTest {
	private static final String OUTPUT = System.getProperty("questplus.clientSmokeTest");
	private static final String WORLD = "questplus-smoke";
	private enum Step { TITLE, JOIN, TABLET, WORLD_BLOCKS, SAVE_SCREENSHOT, DONE }
	private static Step step = Step.TITLE;
	private static int ticks;
	private static final List<String> passed = new ArrayList<>();
	private static final List<BlockPos> placed = new ArrayList<>();

	private ClientSmokeTest() {}

	@SubscribeEvent
	static void tick(ClientTickEvent.Post event) {
		if (OUTPUT == null || step == Step.DONE) return;
		var client = Minecraft.getInstance();
		try {
			if (++ticks > 2400) throw new AssertionError("Timed out in step " + step + " on screen " + ClientAdapter.INSTANCE.screen(client));
			switch (step) {
				case TITLE -> {
					if (!(ClientAdapter.INSTANCE.screen(client) instanceof TitleScreen)) return;
					TargetAudit.run(CommonAdapter.INSTANCE, ClientAdapter.INSTANCE);
					pass("startup: target " + dev.questplus.compat.MinecraftTarget.active().id() + ", every mixin applied");
					var settings = new LevelSettings(WORLD, GameType.SURVIVAL,
							new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
					client.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0, false, false),
							registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
									.createWorldDimensions(), new TitleScreen());
					next(Step.JOIN);
				}
				case JOIN -> {
					if (client.player == null || !QuestPlusClient.STATE.ready) return;
					if (QuestPlusClient.STATE.quests.isEmpty()) throw new AssertionError("Empty initial catalogue");
					pass("network: " + QuestPlusClient.STATE.quests.size() + " quests synced from the integrated server");
					ClientAdapter.INSTANCE.setScreen(client, new QuestScreen());
					next(Step.TABLET);
				}
				case TABLET -> {
					if (ticks < 60) return;
					if (!(ClientAdapter.INSTANCE.screen(client) instanceof QuestScreen))
						throw new AssertionError("Tablet screen closed itself: " + ClientAdapter.INSTANCE.screen(client));
					screenshot(client, "questplus-smoke-tablet.png");
					pass("tablet screen rendered for 60 ticks");
					ClientAdapter.INSTANCE.setScreen(client, null);
					placeWorldBlocks(client);
					next(Step.WORLD_BLOCKS);
				}
				case WORLD_BLOCKS -> {
					if (ticks < 80) return;
					var level = client.level;
					var blocks = List.of(QuestWorldBlocks.STATION, QuestWorldBlocks.RELAY, QuestWorldBlocks.BOARD);
					for (int i = 0; i < blocks.size(); i++)
						if (!level.getBlockState(placed.get(i)).is(blocks.get(i)) || level.getBlockEntity(placed.get(i)) == null)
							throw new AssertionError("Client never received " + BuiltInRegistries.BLOCK.getKey(blocks.get(i)));
					if (level.getEntitiesOfClass(QuestGiver.class, client.player.getBoundingBox().inflate(16)).isEmpty())
						throw new AssertionError("Client never received the Quest Giver");
					if (!dev.questplus.fabric.client.SmokeTestAccess.carryingTablet())
						throw new AssertionError("Held tablet is not carried by the native first-person renderer");
					screenshot(client, "questplus-smoke-world.png");
					pass("Quest Board, Submission Station, Quest Relay and Quest Giver rendered with the tablet in hand");
					next(Step.SAVE_SCREENSHOT);
				}
				// Screenshots are written on an IO thread: let it finish before quitting.
				case SAVE_SCREENSHOT -> {
					if (ticks >= 20) finish(client, null);
				}
				case DONE -> {}
			}
		} catch (Throwable error) {
			finish(client, error);
		}
	}

	private static void placeWorldBlocks(Minecraft client) {
		var server = client.getSingleplayerServer();
		var id = client.player.getUUID();
		var facing = client.player.getDirection();
		var origin = client.player.blockPosition().relative(facing, 4);
		placed.addAll(List.of(origin, origin.relative(facing.getClockWise(), 2), origin.relative(facing.getCounterClockWise(), 3).above()));
		var giver = origin.relative(facing.getOpposite(), 2);
		server.execute(() -> {
			var player = server.getPlayerList().getPlayer(id);
			var level = player.level();
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("questplus:tablet"))));
			level.setBlock(placed.get(0), QuestWorldBlocks.STATION.defaultBlockState(), 3);
			level.setBlock(placed.get(1), QuestWorldBlocks.RELAY.defaultBlockState(), 3);
			level.setBlock(placed.get(2), QuestWorldBlocks.BOARD.defaultBlockState(), 3);
			QuestGiver.TYPE.spawn(level, giver, EntitySpawnReason.COMMAND);
		});
	}

	private static void pass(String check) {
		passed.add(check);
		dev.questplus.fabric.QuestPlusMod.LOGGER.info("Client smoke test: {}", check);
	}

	private static void next(Step value) {
		step = value;
		ticks = 0;
	}

	private static void screenshot(Minecraft client, String name) {
		Screenshot.grab(client.gameDirectory, name, ClientSmokeShims.mainTarget(client), 1, message -> {});
	}

	private static void finish(Minecraft client, Throwable error) {
		step = Step.DONE;
		var result = new JsonObject();
		result.addProperty("ok", error == null);
		result.addProperty("minecraft", dev.questplus.platform.Loader.minecraftVersion());
		var checks = new com.google.gson.JsonArray();
		passed.forEach(checks::add);
		result.add("passed", checks);
		if (error != null) {
			result.addProperty("error", error.toString());
			dev.questplus.fabric.QuestPlusMod.LOGGER.error("Client smoke test failed", error);
		}
		try {
			Files.writeString(Path.of(OUTPUT), result.toString(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			dev.questplus.fabric.QuestPlusMod.LOGGER.error("Cannot write client smoke result", e);
		}
		client.stop();
	}
}
