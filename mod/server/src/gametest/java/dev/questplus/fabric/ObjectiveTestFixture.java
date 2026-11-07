package dev.questplus.fabric;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;

/** Isolated real player and authored quest, shared by the objective batteries. */
final class ObjectiveTestFixture implements AutoCloseable {
	final GameTestHelper helper;
	final QuestRuntime runtime = QuestPlusMod.runtime();
	final ServerPlayer player;
	final String quest;
	private final Path file;
	private final List<Entity> entities = new ArrayList<>();
	private boolean closed;

	ObjectiveTestFixture(GameTestHelper helper, String name, String objectives) throws Exception {
		this.helper = helper;
		quest = "questplus:test_" + name;
		file = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
				.resolve("questplus/quests/" + name + "-battery.json");
		var data = new JsonObject();
		data.addProperty("schemaVersion", 2);
		data.addProperty("id", quest);
		data.addProperty("category", runtime.categories().getFirst().id());
		data.addProperty("icon", "minecraft:stone");
		data.add("title", JsonParser.parseString("{en_us:'Objective battery'}"));
		data.addProperty("progression", "once");
		var goals = JsonParser.parseString(objectives).getAsJsonArray();
		// Keep the quest open so state counters can be tested decreasing as well.
		goals.add(JsonParser.parseString("{id:'guard',event:'manual',target:'questplus:test_guard',count:1}"));
		data.add("objectives", goals);
		data.add("rewards", new JsonArray());
		Files.writeString(file, data.toString(), StandardOpenOption.CREATE_NEW);
		runtime.reload();
		var server = helper.getLevel().getServer();
		player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name),
				ClientInformation.createDefault());
		var connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player,
				CommonListenerCookie.createInitial(player.getGameProfile(), false));
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		player.setNoGravity(true);
		var at = helper.absolutePos(new BlockPos(1, 2, 1));
		player.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
		player.getInventory().clearContent();
		player.getFoodData().setFoodLevel(17);
		// Embedded connections have no network tick to run the player's normal equipment/physics update.
		helper.onEachTick(() -> {
			if (!closed && player.isAlive())
				player.doTick();
		});
		var infoField = GameTestHelper.class.getDeclaredField("testInfo");
		infoField.setAccessible(true);
		((GameTestInfo) infoField.get(helper)).addListener(new GameTestListener() {
			public void testStructureLoaded(GameTestInfo info) {}
			public void testAddedForRerun(GameTestInfo old, GameTestInfo next, GameTestRunner runner) {}
			public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
			public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
		});
	}

	long count(String objective) {
		try {
			var progress = runtime.state(player.getUUID()).quests.get(quest);
			return progress == null ? 0 : progress.counters.getOrDefault(objective, 0L);
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	void expect(String objective, long value) {
		helper.assertTrue(count(objective) == value,
				quest + "/" + objective + ": expected " + value + ", got " + count(objective));
	}

	void idle() {
		helper.assertTrue(!runtime.isBusy(player.getUUID()), "Await objective battery save");
	}

	<T extends Entity> T spawn(EntityType<T> type) {
		T entity = helper.spawn(type, new BlockPos(4, 2, 4));
		if (entity instanceof Mob mob)
			mob.setNoAi(true);
		entities.add(entity);
		return entity;
	}

	void pickup(ItemStack stack) {
		var item = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
		item.setNoPickUpDelay();
		player.level().addFreshEntity(item);
		entities.add(item);
		item.playerTouch(player);
	}

	@Override
	public void close() {
		if (closed)
			return;
		closed = true;
		try {
			entities.forEach(Entity::discard);
			player.level().getServer().getPlayerList().remove(player);
			runtime.disconnect(player.getUUID());
			Files.deleteIfExists(file);
			runtime.reload();
		} catch (Exception e) {
			throw new RuntimeException("Objective battery cleanup failed", e);
		}
	}
}
