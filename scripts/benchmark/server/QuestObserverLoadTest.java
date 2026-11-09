package dev.questplus.fabric;

import com.google.gson.*;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

public final class QuestObserverLoadTest {
	private final List<Map<String, Object>> results = new ArrayList<>();
	private final ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
	private volatile Object sink;

	@GameTest(maxTicks = 200)
	public void samplingCadence(GameTestHelper h) throws Exception {
		new ObjectiveSamplingGameTest().samplingCadenceDoesNotDependOnDistanceBaseline(h);
	}

	@GameTest(maxTicks = 200)
	public void sharedRanking(GameTestHelper h) throws Exception {
		new QuestWorldBlocksGameTest().sharedRankingIsReusedAndInvalidatedWithinTheTick(h);
	}

	@GameTest(maxTicks = 1200)
	public void benchmark(GameTestHelper h) throws Exception {
		threads.setThreadAllocatedMemoryEnabled(true);
		var targets = new ArrayList<>(BuiltInRegistries.BLOCK.keySet().stream()
				.filter(id -> !Set.of("minecraft:air", "minecraft:stone").contains(id.toString()))
				.sorted(Comparator.comparing(Object::toString)).limit(999).toList());
		targets.add(net.minecraft.resources.Identifier.parse("minecraft:stone"));
		var objectives = new JsonArray();
		for (int i = 0; i < targets.size(); i++) {
			var o = new JsonObject();
			o.addProperty("id", "watch" + i);
			o.addProperty("event", "observe_block");
			o.addProperty("target", targets.get(i).toString());
			o.addProperty("count", 1000000);
			objectives.add(o);
		}
		var files = new ArrayList<Path>();
		try (var f = new ObjectiveTestFixture(h, "observer_load", "[]")) {
			var player = f.player;
			var server = h.getLevel().getServer();
			var directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
					.resolve("questplus/quests");
			for (int start = 0; start < objectives.size(); start += 32) {
				var data = new JsonObject();
				data.addProperty("schemaVersion", 2);
				data.addProperty("id", "questplus:watch_load_" + start);
				data.addProperty("category", f.runtime.categories().getFirst().id());
				data.addProperty("icon", "minecraft:stone");
				data.add("title", JsonParser.parseString("{en_us:'Observer load'}"));
				data.addProperty("progression", "once");
				var batch = new JsonArray();
				for (int i = start; i < Math.min(start + 32, objectives.size()); i++)
					batch.add(objectives.get(i));
				data.add("objectives", batch);
				data.add("rewards", new JsonArray());
				var file = directory.resolve("watch-load-" + start + ".json");
				Files.writeString(file, data.toString());
				files.add(file);
			}
			var stationFile = directory.resolve("station-load.json");
			files.add(stationFile);
			Files.writeString(stationFile, """
					{"schemaVersion":2,"id":"questplus:station_load","category":"%s","icon":"minecraft:stone",
					 "title":{"en_us":"Station load"},"progression":"once","objectives":[
					 {"id":"items","event":"item_turn_in","target":"minecraft:stone","count":1000000}],"rewards":[]}
					""".formatted(f.runtime.categories().getFirst().id()));
			f.runtime.reload();
			for (int i = 0; i < 36; i++)
				player.getInventory().setItem(i, new ItemStack(Items.DIAMOND_PICKAXE));
			measure("full_player_context", 1, () -> sink = QuestPlusMod.context(player));
			// Keep the world/server tick fixed: this measures the raycast/publication path,
			// not sustained dwell progress or a real population's tick budget.
			var at = player.blockPosition().offset(0, 1, 4);
			h.getLevel().setBlock(at, Blocks.STONE.defaultBlockState(), 3);
			player.setYRot(0);
			player.setYHeadRot(0);
			player.setXRot(0);
			// Idle staggering may defer the first probe; advance the private counter once
			// through a real server tick before measuring by using an authored block below.
			var trackers = ObservationEvents.class.getDeclaredField("TRACKERS");
			trackers.setAccessible(true);
			ObservationEvents.tick(server);
			var tracker = ((Map<?, ?>) trackers.get(null)).get(player.getUUID());
			var observing = tracker.getClass().getDeclaredField("observing");
			observing.setAccessible(true);
			observing.setBoolean(tracker, true);
			measure("observation_active_" + targets.size() + "_targets", 1, () -> ObservationEvents.tick(server));
			var tickCount = net.minecraft.server.MinecraftServer.class.getDeclaredField("tickCount");
			tickCount.setAccessible(true);
			int originalTick = server.getTickCount();
			try {
				measure("observation_advancing_dwell_" + targets.size() + "_targets", 1, () -> {
					tickCount.setInt(server, server.getTickCount() + 1);
					ObservationEvents.tick(server);
				});
				try {
					var oldTick = Class.forName("dev.questplus.fabric.BeforeObservationEvents")
							.getDeclaredMethod("tick", net.minecraft.server.MinecraftServer.class);
					oldTick.setAccessible(true);
					measure("baseline_observation_advancing_dwell_" + targets.size() + "_targets", 1, () -> {
						tickCount.setInt(server, server.getTickCount() + 1);
						oldTick.invoke(null, server);
					});
				} catch (ClassNotFoundException absent) {
					System.out.println("No baseline observer overlay supplied");
				}
			} finally {
				tickCount.setInt(server, originalTick);
			}
			player.setXRot(-90);
			observing.setBoolean(tracker, true);
			ObservationEvents.tick(server);
			// Force the idle probe for every call, independent of UUID phase.
			measure("observation_miss_" + targets.size() + "_targets", 1, () -> {
				observing.setBoolean(tracker, true);
				ObservationEvents.tick(server);
			});
			var boards = new ArrayList<QuestUtilityEntity>();
			for (int i = 0; i < 32; i++) {
				var pos = h.absolutePos(new BlockPos(i % 8, 5, i / 8));
				h.getLevel().setBlock(pos, QuestWorldBlocks.BOARD.defaultBlockState(), 3);
				var be = (QuestUtilityEntity) h.getLevel().getBlockEntity(pos);
				be.claim(player.getUUID());
				boards.add(be);
			}
			// Model offline histories without network/vanilla entity simulation.
			for (int i = 0; i < 300; i++) {
				var p = f.runtime.player(new UUID(909, i));
				p.lastKnownName = "Bench" + i;
				p.quests.putAll(f.runtime.state(player.getUUID()).copyForAutosave().quests);
			}
			measure("boards_32_shared_ranking_300_histories", 32, () -> {
				QuestWorldBlocks.invalidate(player.getUUID());
				for (var be : boards)
					be.refresh();
				sink = boards.getLast().leaders;
			});
			for (var be : boards)
				h.getLevel().removeBlock(be.getBlockPos(), false);
			var relays = new ArrayList<QuestUtilityEntity>();
			var stations = new ArrayList<QuestUtilityEntity>();
			for (int i = 0; i < 32; i++) {
				var pos = h.absolutePos(new BlockPos(i % 8, 9, i / 8));
				h.getLevel().setBlock(pos, QuestWorldBlocks.RELAY.defaultBlockState(), 3);
				var be = (QuestUtilityEntity) h.getLevel().getBlockEntity(pos);
				be.claim(player.getUUID());
				relays.add(be);
				pos = pos.above(3);
				h.getLevel().setBlock(pos, QuestWorldBlocks.STATION.defaultBlockState(), 3);
				be = (QuestUtilityEntity) h.getLevel().getBlockEntity(pos);
				be.claim(player.getUUID());
				be.serverTick();
				stations.add(be);
			}
			measure("relays_32_completed_refresh", 32, () -> relays.forEach(QuestUtilityEntity::refresh));
			measure("stations_32_idle_tick", 32, () -> stations.forEach(QuestUtilityEntity::serverTick));
			for (var be : stations)
				be.config = QuestWorldBlocksGameTest.config("questplus:station_load", "items");
			measure("stations_8_durable_item_submissions", 8, () -> {
				long before = f.runtime.state(player.getUUID()).quests.containsKey("questplus:station_load")
						? f.runtime.state(player.getUUID()).quests.get("questplus:station_load").counters
								.getOrDefault("items", 0L)
						: 0;
				for (var be : stations.subList(0, 8)) {
					try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
						if (be.items.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(Items.STONE), 1,
								tx) != 1)
							throw new AssertionError("Station rejected benchmark input");
						tx.commit();
					}
					be.refresh();
					if (!be.isEmpty())
						throw new AssertionError("Station did not debit its input");
				}
				long after = f.runtime.state(player.getUUID()).quests.get("questplus:station_load").counters
						.get("items");
				if (after - before != 8)
					throw new AssertionError("Submission progress mismatch");
			});
			for (var be : relays)
				h.getLevel().removeBlock(be.getBlockPos(), false);
			for (var be : stations)
				h.getLevel().removeBlock(be.getBlockPos(), false);
		} finally {
			for (var file : files)
				Files.deleteIfExists(file);
			QuestPlusMod.runtime().reload();
			var root = Path.of(System.getProperty("questplus.benchmark.root"));
			Files.writeString(
					root.resolve("server-" + System.getProperty("questplus.benchmark.label") + "-results.json"),
					new GsonBuilder().setPrettyPrinting().create().toJson(results));
		}
		h.succeed();
	}

	@FunctionalInterface
	private interface Work {
		void run() throws Exception;
	}

	private void measure(String name, int count, Work work) throws Exception {
		long until = System.nanoTime() + 500_000_000L;
		do {
			work.run();
		} while (System.nanoTime() < until);
		var samples = new ArrayList<Double>();
		long allocated = threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
		until = System.nanoTime() + 1_500_000_000L;
		do {
			long start = System.nanoTime();
			work.run();
			samples.add((System.nanoTime() - start) / 1e6);
		} while (System.nanoTime() < until && samples.size() < 100000);
		long bytes = threads.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
		Collections.sort(samples);
		var result = Map.<String, Object>of("scenario", name, "subjects", count, "samples", samples.size(), "p50_ms",
				samples.get(samples.size() / 2), "p99_ms", samples.get((int) Math.ceil(samples.size() * .99) - 1),
				"caller_bytes_per_op", (double) bytes / samples.size());
		results.add(result);
		System.out.println("SERVER BENCH " + result);
	}
}
