package dev.questplus.fabric;

import dev.questplus.platform.Platform;
import dev.questplus.core.QuestEvent;
import dev.questplus.core.QuestRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.stream.Collectors;

public final class AdvancedEvents {
	private AdvancedEvents() {
	}

	private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
	private static final List<Identifier> DISTANCE_STATS = List.of(Stats.WALK_ONE_CM, Stats.CROUCH_ONE_CM,
			Stats.SPRINT_ONE_CM, Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.CLIMB_ONE_CM,
			Stats.FLY_ONE_CM, Stats.SWIM_ONE_CM, Stats.MINECART_ONE_CM, Stats.BOAT_ONE_CM, Stats.PIG_ONE_CM,
			Stats.HORSE_ONE_CM, Stats.HAPPY_GHAST_ONE_CM, Stats.AVIATE_ONE_CM, Stats.STRIDER_ONE_CM,
			Stats.NAUTILUS_ONE_CM);
	private static long ticks;
	private static int cursor;
	private static QuestRegistry sampledRegistry;
	private static boolean samplingEnabled;
	private static final Set<String> SAMPLED_EVENTS = Set.of("dimension_enter", "biome_visit", "structure_visit",
			"travel_distance", "location", "inventory_count", "advancement_complete", "custom_stat", "stage",
			"xp_levels", "xp_points", "equipment_check");

	private static final class Sample {
		long last = Long.MIN_VALUE;
		String dimension = "", biome = "";
		Set<String> structures = Set.of();
		int[] distances;
	}

	public static void register() {
		Platform.onPlayerDisconnect((player, server) -> SAMPLES.remove(player.getUUID()));
		Platform.onServerStopping(server -> {
			SAMPLES.clear();
			cursor = 0;
			ticks = 0;
			sampledRegistry = null;
			samplingEnabled = false;
		});
	}

	public static void tick(MinecraftServer server) {
		var registry = QuestPlusMod.runtime().registry();
		if (registry != sampledRegistry) {
			// A reload can introduce new state objectives. Refresh every player's first
			// sample.
			sampledRegistry = registry;
			samplingEnabled = SAMPLED_EVENTS.stream().anyMatch(registry::hasEvent);
			SAMPLES.clear();
			cursor = 0;
		}
		if (!samplingEnabled)
			return;
		var players = server.getPlayerList().getPlayers();
		ticks++;
		// DISCONNECT already clears samples. Do not rebuild an O(players) UUID set
		// every tick.
		if (players.isEmpty()) {
			cursor = 0;
			return;
		}
		int visited = 0, sampled = 0;
		while (visited++ < players.size() && sampled < 8) {
			var player = players.get(Math.floorMod(cursor++, players.size()));
			var state = SAMPLES.computeIfAbsent(player.getUUID(), id -> new Sample());
			if (state.last != Long.MIN_VALUE && ticks - state.last < 20)
				continue;
			state.last = ticks;
			sample(player, state);
			sampled++;
		}
	}

	private static final class SampleContext {
		private final ServerPlayer player;
		private Map<String, Object> cached;

		SampleContext(ServerPlayer player) {
			this.player = player;
		}

		Map<String, Object> get() {
			if (cached == null)
				cached = QuestPlusMod.context(player);
			return cached;
		}

		Map<String, Object> states(Map<String, Long> values) {
			var result = new HashMap<>(get());
			result.put("stateValues", values);
			return result;
		}
	}

	private static void sample(ServerPlayer player, Sample state) {
		var registry = QuestPlusMod.runtime().registry();
		var snapshot = new SampleContext(player);
		String dimension = player.level().dimension().identifier().toString();
		String biome = state.biome;
		if (registry.hasEvent("dimension_enter") && !dimension.equals(state.dimension))
			emit(player, "dimension_enter", dimension, Set.of(), 1, snapshot);
		if (registry.hasEvent("biome_visit")) {
			var biomeHolder = player.level().getBiome(player.blockPosition());
			biome = biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
			if (!biome.equals(state.biome) || !dimension.equals(state.dimension))
				emit(player, "biome_visit", biome,
						biomeHolder.tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1, snapshot);
		}
		if (registry.hasEvent("structure_visit")) {
			var structures = player.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
			Set<String> inside = new HashSet<>();
			for (var structure : player.level().structureManager().getAllStructuresAt(player.blockPosition())
					.keySet()) {
				if (!player.level().structureManager().getStructureAt(player.blockPosition(), structure).isValid())
					continue;
				String id = structures.getKey(structure).toString();
				inside.add(id);
				if (!state.structures.contains(id) || !dimension.equals(state.dimension))
					emit(player, "structure_visit", id, structures.wrapAsHolder(structure).tags()
							.map(t -> t.location().toString()).collect(Collectors.toSet()), 1, snapshot);
			}
			state.structures = inside;
		}
		int[] distances = state.distances;
		if (registry.hasEvent("travel_distance")) {
			int[] previous = state.distances;
			distances = new int[DISTANCE_STATS.size()];
			long traveled = 0;
			for (int i = 0; i < distances.length; i++) {
				distances[i] = player.getStats().getValue(Stats.CUSTOM.get(DISTANCE_STATS.get(i)));
				if (previous != null && dimension.equals(state.dimension) && distances[i] >= previous[i])
					traveled += (long) distances[i] - previous[i];
			}
			if (traveled > 0)
				emit(player, "travel_distance", "questplus:travel", Set.of(), traveled, snapshot);
		}
		if (registry.hasEvent("location"))
			emit(player, "location", "questplus:position", Set.of(), 1, snapshot);
		if (registry.hasEvent("inventory_count")) {
			@SuppressWarnings("unchecked")
			var inventory = (Map<String, Long>) snapshot.get().get("inventory");
			for (String target : registry.targets("inventory_count"))
				emit(player, "inventory_count", target.startsWith("#") ? "minecraft:air" : target,
						target.startsWith("#") ? Set.of(target.substring(1)) : Set.of(),
						inventory.getOrDefault(target, 0L), snapshot);
		}
		if (registry.hasEvent("advancement_complete")) {
			for (String target : registry.targets("advancement_complete")) {
				var advancement = player.level().getServer().getAdvancements().get(Identifier.parse(target));
				if (advancement == null)
					continue;
				boolean done = player.getAdvancements().getOrStartProgress(advancement).isDone();
				var ctx = snapshot.states(Map.of(target, done ? 1L : 0L));
				QuestPlusMod.runtime().event(player, new QuestEvent("advancement_complete", target, Set.of(), 1, ctx));
			}
		}
		if (registry.hasEvent("custom_stat")) {
			// Read only authored custom-stat IDs. This avoids scanning every stat for every
			// player.
			for (String target : registry.targets("custom_stat")) {
				// StatType requires the registered value instance, not a newly parsed equal ID.
				Identifier stat = BuiltInRegistries.CUSTOM_STAT.getValue(Identifier.parse(target));
				long value = Math.max(0, player.getStats().getValue(Stats.CUSTOM.get(stat)));
				var ctx = snapshot.states(Map.of(target, value));
				QuestPlusMod.runtime().event(player, new QuestEvent("custom_stat", target, Set.of(), 1, ctx));
			}
		}
		if (registry.hasEvent("stage")) {
			var values = new HashMap<String, Long>();
			var stages = QuestStages.stages(player);
			for (String target : registry.targets("stage"))
				values.put(target, stages.contains(target) ? 1L : 0L);
			var ctx = snapshot.states(values);
			for (String target : values.keySet())
				QuestPlusMod.runtime().event(player, new QuestEvent("stage", target, Set.of(), 1, ctx));
		}
		if (registry.hasEvent("xp_levels")) {
			var ctx = snapshot.states(Map.of("questplus:levels", (long) player.experienceLevel));
			QuestPlusMod.runtime().event(player, new QuestEvent("xp_levels", "questplus:levels", Set.of(), 1, ctx));
		}
		if (registry.hasEvent("xp_points")) {
			long level = player.experienceLevel;
			long total = level <= 16 ? level * level + 6 * level
					: level <= 31 ? (5 * level * level - 81 * level + 720) / 2
							: (9 * level * level - 325 * level + 4440) / 2;
			var ctx = snapshot.states(Map.of("questplus:points",
					Math.max(0, total + (long) (player.experienceProgress * player.getXpNeededForNextLevel()))));
			QuestPlusMod.runtime().event(player, new QuestEvent("xp_points", "questplus:points", Set.of(), 1, ctx));
		}
		// A single authoritative snapshot per sampled player. Gear is never supplied by
		// clients.
		if (registry.hasEvent("equipment_check")) {
			QuestPlusMod.runtime().event(player,
					new QuestEvent("equipment_check", "questplus:gear", Set.of(), 1, snapshot.get()));
		}
		state.dimension = dimension;
		state.biome = biome;
		state.distances = distances;
	}

	public static Map<String, Object> gearSnapshot(ServerPlayer player) {
		Map<String, Object> gear = new HashMap<>();
		List<Map<String, Object>> inventory = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			var stack = player.getInventory().getItem(i);
			if (!stack.isEmpty())
				inventory.add(gearItem(stack));
		}
		if (!player.getOffhandItem().isEmpty())
			inventory.add(gearItem(player.getOffhandItem()));
		// Equipped armor (notably Elytra in the chest slot) also counts as owned
		// equipment.
		for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
			var worn = player.getItemBySlot(slot);
			if (!worn.isEmpty())
				inventory.add(gearItem(worn));
		}
		gear.put("inventory", inventory);
		for (var entry : Map
				.of("head", EquipmentSlot.HEAD, "chest", EquipmentSlot.CHEST, "legs", EquipmentSlot.LEGS, "feet",
						EquipmentSlot.FEET, "mainhand", EquipmentSlot.MAINHAND, "offhand", EquipmentSlot.OFFHAND)
				.entrySet()) {
			var stack = player.getItemBySlot(entry.getValue());
			gear.put(entry.getKey(), stack.isEmpty() ? List.of() : List.of(gearItem(stack)));
		}
		return gear;
	}

	private static Map<String, Object> gearItem(ItemStack stack) {
		Map<String, Integer> enchantments = new HashMap<>();
		var enchanted = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (var entry : enchanted.entrySet()) {
			entry.getKey().unwrapKey()
					.ifPresent(key -> enchantments.put(key.identifier().toString(), entry.getIntValue()));
		}
		return Map.of("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "enchantments", enchantments);
	}

	public static void item(ServerPlayer player, String type, ItemStack stack, int count) {
		if (count <= 0 || stack.isEmpty())
			return;
		var runtime = QuestPlusMod.runtime();
		var registry = runtime.registry();
		// No item-component serialization or inventory/gear snapshots for unrelated
		// items.
		if (!registry.hasEvent(type) && !(type.equals("fish_item") && registry.hasEvent("questplus:fish_any")))
			return;
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if (registry.hasEvent(type)) {
			Set<String> tags = stack.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet());
			if (registry.hasCandidate(type, itemId, tags)) {
				var context = QuestPlusMod.context(player);
				context.put("itemComponents", QuestItemStacks.components(stack, player.registryAccess()));
				runtime.event(player, new QuestEvent(type, itemId, tags, count, context));
			}
		}
		// Aggregate the four vanilla catches, without examining any unrelated quest
		// event.
		if (type.equals("fish_item")
				&& Set.of("minecraft:cod", "minecraft:salmon", "minecraft:pufferfish", "minecraft:tropical_fish")
						.contains(itemId))
			emit(player, "questplus:fish_any", "questplus:fish", Set.of(), count);
	}

	public static void block(ServerPlayer player, String type, BlockState state) {
		if (!QuestPlusMod.runtime().registry().hasEvent(type))
			return;
		emit(player, type, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
				state.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1);
	}

	public static void entity(ServerPlayer player, String type, Entity entity) {
		if (!QuestPlusMod.runtime().registry().hasEvent(type))
			return;
		emit(player, type, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
				BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).tags().map(t -> t.location().toString())
						.collect(Collectors.toSet()),
				1);
	}

	private static void emit(ServerPlayer player, String type, String target, Set<String> tags, long count,
			SampleContext snapshot) {
		var runtime = QuestPlusMod.runtime();
		if (runtime.registry().hasCandidate(type, target, tags))
			runtime.event(player, new QuestEvent(type, target, tags, count, snapshot.get()));
	}

	private static void emit(ServerPlayer player, String type, String target, Set<String> tags, long count) {
		QuestRuntime runtime;
		try {
			runtime = QuestPlusMod.runtime();
		} catch (IllegalStateException unavailable) {
			return;
		}
		if (!runtime.registry().hasCandidate(type, target, tags))
			return;
		runtime.event(player, new QuestEvent(type, target, tags, count, QuestPlusMod.context(player)));
	}

	public static Map<String, Object> context(ServerPlayer player) {
		Map<String, Long> inventory = new HashMap<>();
		QuestRuntime runtime;
		try {
			runtime = QuestPlusMod.runtime();
		} catch (IllegalStateException unavailable) {
			runtime = null;
		}
		if (runtime != null && runtime.registry().hasEvent("inventory_count")) {
			for (int i = 0; i < 36; i++)
				add(inventory, player.getInventory().getItem(i));
			add(inventory, player.getOffhandItem());
		}
		Map<String, String> equipment = new HashMap<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack item = player.getItemBySlot(slot);
			equipment.put(slot.getSerializedName(), BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
		}
		var biome = player.level().getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().toString())
				.orElse("");
		Set<String> effects = player.getActiveEffects().stream()
				.map(e -> e.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse(""))
				.collect(Collectors.toSet());
		var result = new HashMap<String, Object>(
				Map.of("biome", biome, "position", Map.of("x", player.getX(), "y", player.getY(), "z", player.getZ()),
						"equipment", equipment, "effects", effects, "inventory", inventory));
		if (runtime != null
				&& runtime.registry().objectives("inventory_count").stream().anyMatch(o -> !o.data().equals("{}")))
			result.put("itemStacks", QuestItemStacks.snapshot(player));
		return result;
	}

	private static void add(Map<String, Long> counts, ItemStack stack) {
		if (stack.isEmpty())
			return;
		counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (long) stack.getCount(), Long::sum);
		stack.typeHolder().tags().forEach(t -> counts.merge("#" + t.location(), (long) stack.getCount(), Long::sum));
	}
}
