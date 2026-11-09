package dev.questplus.fabric;

import dev.questplus.core.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.*;
import java.util.*;

/**
 * Server-owned six-block observation raycast. Idle players are probed at a
 * staggered low frequency; once looking at an authored objective, every tick is
 * verified. ObservationCounter still requires consecutive validated ticks for
 * dwell rewards.
 */
final class ObservationEvents {
	private static final int IDLE_SAMPLE_TICKS = 3;
	private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();
	private static QuestRegistry lastRegistry;
	private static Map<String, Set<String>> authoredTargets = Map.of();

	private static final class Tracker {
		final ObservationCounter counter = new ObservationCounter();
		final Map<String, Map<String, Long>> published = new HashMap<>();
		boolean observing;
	}

	private ObservationEvents() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACKERS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TRACKERS.clear();
			lastRegistry = null;
			authoredTargets = Map.of();
		});
	}

	static void tick(MinecraftServer server) {
		var runtime = QuestPlusMod.runtime();
		var registry = runtime.registry();
		if (registry != lastRegistry) {
			TRACKERS.clear();
			lastRegistry = registry;
			authoredTargets = Map.of("observe_block", Set.copyOf(registry.targets("observe_block")),
					"observe_entity", Set.copyOf(registry.targets("observe_entity")));
		}
		boolean blocks = registry.hasEvent("observe_block"), entities = registry.hasEvent("observe_entity");
		if (!blocks && !entities)
			return;
		int now = server.getTickCount();
		for (var player : server.getPlayerList().getPlayers()) {
			var tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
			if (player.isCreative() || player.isSpectator()) {
				tracker.counter.update(null, now);
				tracker.observing = false;
				tracker.published.clear();
				continue;
			}
			// Hash staggering avoids a synchronized raycast burst at large player counts.
			if (!tracker.observing && Math.floorMod(now + player.getUUID().hashCode(), IDLE_SAMPLE_TICKS) != 0)
				continue;
			String identity = null, type = null, target = null;
			Set<String> tags = Set.of();
			var eye = player.getEyePosition();
			var view = player.getViewVector(1).scale(6);
			var end = eye.add(view);
			var block = player.pick(6, 1, false);
			double limit = block.getType() == HitResult.Type.MISS ? 36 : eye.distanceToSqr(block.getLocation());
			var entity = ProjectileUtil.getEntityHitResult(player, eye, end,
					player.getBoundingBox().expandTowards(view).inflate(1),
					candidate -> !candidate.isSpectator() && candidate.isPickable(), limit);
			if (entity != null) {
				var found = entity.getEntity();
				type = "observe_entity";
				target = BuiltInRegistries.ENTITY_TYPE.getKey(found.getType()).toString();
				tags = found.getType().builtInRegistryHolder().tags().map(t -> t.location().toString())
						.collect(java.util.stream.Collectors.toSet());
				identity = player.level().dimension().identifier() + ":entity:" + found.getUUID();
			} else if (blocks && block instanceof BlockHitResult hit && block.getType() == HitResult.Type.BLOCK) {
				var state = player.level().getBlockState(hit.getBlockPos());
				type = "observe_block";
				target = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
				tags = state.typeHolder().tags().map(t -> t.location().toString())
						.collect(java.util.stream.Collectors.toSet());
				identity = player.level().dimension().identifier() + ":block:" + hit.getBlockPos().asLong() + ":"
						+ target;
			}
			long ticks = tracker.counter.update(identity, now);
			tracker.observing = false;
			Map<String, Object> baseContext = null;
			for (String event : List.of("observe_block", "observe_entity")) {
				var authored = authoredTargets.get(event);
				if (authored.isEmpty())
					continue;
				var previous = tracker.published.get(event);
				Map<String, Long> values = new HashMap<>();
				// First sample clears persisted counters; later samples reset only the old gaze.
				for (String old : previous == null ? authored : previous.keySet())
					values.put(old, 0L);
				if (event.equals(type)) {
					if (authored.contains(target))
						values.put(target, ticks);
					for (String tag : tags)
						if (authored.contains("#" + tag))
							values.put("#" + tag, ticks);
				}
				Map<String, Long> positive = new HashMap<>();
				List<String> changed = new ArrayList<>();
				for (var entry : values.entrySet()) {
					if (entry.getValue() > 0)
						positive.put(entry.getKey(), entry.getValue());
					if (previous == null || previous.getOrDefault(entry.getKey(), 0L).longValue() != entry.getValue())
						changed.add(entry.getKey());
				}
				tracker.observing |= !positive.isEmpty();
				if (changed.isEmpty())
					continue;
				if (runtime.isBusy(player.getUUID()))
					continue; // Never cache events rejected while saving.
				if (baseContext == null)
					baseContext = QuestPlusMod.context(player);
				var context = new HashMap<>(baseContext);
				context.put("stateValues", new ObservationValues(authored, values));
				for (String changedTarget : changed) {
					runtime.event(player, new QuestEvent(event, changedTarget.startsWith("#") ? "minecraft:air" : changedTarget,
							changedTarget.startsWith("#") ? Set.of(changedTarget.substring(1)) : Set.of(), 1, context));
				}
				if (!runtime.isBusy(player.getUUID()))
					tracker.published.put(event, positive);
			}
		}
	}
}
