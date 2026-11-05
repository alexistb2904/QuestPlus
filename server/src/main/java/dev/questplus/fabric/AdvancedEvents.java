package dev.questplus.fabric;

import dev.questplus.core.QuestEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
    private AdvancedEvents() {}
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final List<Identifier> DISTANCE_STATS = List.of(Stats.WALK_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SPRINT_ONE_CM,
        Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.CLIMB_ONE_CM, Stats.FLY_ONE_CM, Stats.SWIM_ONE_CM,
        Stats.MINECART_ONE_CM, Stats.BOAT_ONE_CM, Stats.PIG_ONE_CM, Stats.HORSE_ONE_CM, Stats.HAPPY_GHAST_ONE_CM,
        Stats.AVIATE_ONE_CM, Stats.STRIDER_ONE_CM, Stats.NAUTILUS_ONE_CM);
    private static long ticks;
    private static int cursor;
    private static final class Sample {
        long last;
        String dimension = "", biome = "";
        Set<String> structures = Set.of();
        int[] distances;
    }
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SAMPLES.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { SAMPLES.clear(); cursor = 0; ticks = 0; });
    }
    public static void tick(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        ticks++;
        Set<UUID> online = new HashSet<>();
        players.forEach(p -> online.add(p.getUUID()));
        SAMPLES.keySet().retainAll(online);
        if (players.isEmpty()) { cursor = 0; return; }
        int visited = 0, sampled = 0;
        while (visited++ < players.size() && sampled < 8) {
            var player = players.get(Math.floorMod(cursor++, players.size()));
            var state = SAMPLES.computeIfAbsent(player.getUUID(), id -> new Sample());
            if (state.distances != null && ticks - state.last < 20) continue;
            state.last = ticks;
            sample(player, state);
            sampled++;
        }
    }
    private static void sample(ServerPlayer player, Sample state) {
        var registry = QuestPlusMod.runtime().registry();
        String dimension = player.level().dimension().identifier().toString();
        var biomeHolder = player.level().getBiome(player.blockPosition());
        String biome = biomeHolder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
        if (!dimension.equals(state.dimension)) emit(player, "dimension_enter", dimension, Set.of(), 1);
        if (!biome.equals(state.biome) || !dimension.equals(state.dimension))
            emit(player, "biome_visit", biome, biomeHolder.tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1);
        if (registry.hasEvent("structure_visit")) {
            var structures = player.level().registryAccess().lookupOrThrow(Registries.STRUCTURE);
            Set<String> inside = new HashSet<>();
            for (var structure : player.level().structureManager().getAllStructuresAt(player.blockPosition()).keySet()) {
                if (!player.level().structureManager().getStructureAt(player.blockPosition(), structure).isValid()) continue;
                String id = structures.getKey(structure).toString();
                inside.add(id);
                if (!state.structures.contains(id) || !dimension.equals(state.dimension))
                    emit(player, "structure_visit", id, structures.wrapAsHolder(structure).tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1);
            }
            state.structures = inside;
        }
        int[] distances = new int[DISTANCE_STATS.size()];
        long traveled = 0;
        for (int i = 0; i < distances.length; i++) {
            distances[i] = player.getStats().getValue(Stats.CUSTOM.get(DISTANCE_STATS.get(i)));
            if (state.distances != null && dimension.equals(state.dimension) && distances[i] >= state.distances[i])
                traveled += (long) distances[i] - state.distances[i];
        }
        if (traveled > 0) emit(player, "travel_distance", "questplus:travel", Set.of(), traveled);
        emit(player, "location", "questplus:position", Set.of(), 1);
        if (registry.hasEvent("inventory_count")) {
            @SuppressWarnings("unchecked") var inventory = (Map<String,Long>) context(player).get("inventory");
            registry.objectives("inventory_count").stream().map(o -> o.target()).distinct().forEach(target ->
                emit(player, "inventory_count", target.startsWith("#") ? "minecraft:air" : target,
                    target.startsWith("#") ? Set.of(target.substring(1)) : Set.of(), inventory.getOrDefault(target, 0L)));
        }
        if(registry.hasEvent("custom_stat")) {
            // The existing sample loop already limits per-tick work to eight players.
            for (String target:registry.objectives("custom_stat").stream().map(o->o.target()).distinct().toList()) {
                Identifier id=Identifier.parse(target);
                if(!Stats.CUSTOM.getRegistry().containsKey(id))continue;
                long value=player.getStats().getValue(Stats.CUSTOM.get(id));
                var ctx=QuestPlusMod.context(player);
                ctx.put("stateValues",Map.of(target,Math.max(0L,value)));
                QuestPlusMod.runtime().event(player,new QuestEvent("custom_stat",target,Set.of(),1,ctx));
            }
        }
        if(registry.hasEvent("xp_levels")) {
            var ctx=QuestPlusMod.context(player);
            ctx.put("stateValues",Map.of("questplus:levels",(long)player.experienceLevel));
            QuestPlusMod.runtime().event(player,new QuestEvent("xp_levels","questplus:levels",Set.of(),1,ctx));
        }
        // A single authoritative snapshot per sampled player. Gear is never supplied by clients.
        if (registry.hasEvent("equipment_check")) {
            var ctx = QuestPlusMod.context(player);
            ctx.put("gear", gearSnapshot(player));
            QuestPlusMod.runtime().event(player, new QuestEvent(
                "equipment_check", "questplus:gear", Set.of(), 1, ctx));
        }
        state.dimension = dimension; state.biome = biome; state.distances = distances;
    }

    public static Map<String,Object> gearSnapshot(ServerPlayer player) {
        Map<String,Object> gear=new HashMap<>();
        List<Map<String,Object>> inventory=new ArrayList<>();
        for(int i=0;i<36;i++) {
            var stack=player.getInventory().getItem(i);
            if(!stack.isEmpty())inventory.add(gearItem(stack));
        }
        if(!player.getOffhandItem().isEmpty())inventory.add(gearItem(player.getOffhandItem()));
        // Equipped armor (notably Elytra in the chest slot) also counts as owned equipment.
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)) {
            var worn=player.getItemBySlot(slot);
            if(!worn.isEmpty())inventory.add(gearItem(worn));
        }
        gear.put("inventory",inventory);
        for(var entry:Map.of(
            "head",EquipmentSlot.HEAD,"chest",EquipmentSlot.CHEST,
            "legs",EquipmentSlot.LEGS,"feet",EquipmentSlot.FEET,
            "mainhand",EquipmentSlot.MAINHAND,"offhand",EquipmentSlot.OFFHAND).entrySet()) {
            var stack=player.getItemBySlot(entry.getValue());
            gear.put(entry.getKey(),stack.isEmpty()?List.of():List.of(gearItem(stack)));
        }
        return gear;
    }

    private static Map<String,Object> gearItem(ItemStack stack) {
        Map<String,Integer> enchantments=new HashMap<>();
        var enchanted=stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var entry:enchanted.entrySet()) {
            entry.getKey().unwrapKey().ifPresent(key->
                enchantments.put(key.identifier().toString(),entry.getIntValue()));
        }
        return Map.of("item",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            "enchantments",enchantments);
    }

    public static void item(ServerPlayer player, String type, ItemStack stack, int count) {
        if (count <= 0 || stack.isEmpty()) return;
        emit(player, type, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
            stack.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet()), count);
        // Aggregate all four vanilla catches without accidentally counting treasure or junk.
        if (type.equals("fish_item") && Set.of(
                "minecraft:cod","minecraft:salmon","minecraft:pufferfish","minecraft:tropical_fish")
                .contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
            emit(player,"questplus:fish_any","questplus:fish",Set.of(),count);
    }

    public static void block(ServerPlayer player, String type, BlockState state) {
        emit(player, type, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
            state.typeHolder().tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1);
    }

    public static void entity(ServerPlayer player, String type, Entity entity) {
        emit(player, type, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType()).tags().map(t -> t.location().toString()).collect(Collectors.toSet()), 1);
    }

    private static void emit(ServerPlayer player, String type, String target, Set<String> tags, long count) {
        QuestRuntime runtime;
        try { runtime = QuestPlusMod.runtime(); } catch (IllegalStateException unavailable) { return; }
        if (!runtime.registry().hasEvent(type)) return;
        runtime.event(player, new QuestEvent(type, target, tags, count, QuestPlusMod.context(player)));
    }

    public static Map<String,Object> context(ServerPlayer player) {
        Map<String,Long> inventory = new HashMap<>();
        QuestRuntime runtime;
        try { runtime = QuestPlusMod.runtime(); } catch (IllegalStateException unavailable) { runtime = null; }
        if (runtime != null && runtime.registry().hasEvent("inventory_count")) {
            for (int i = 0; i < 36; i++) add(inventory, player.getInventory().getItem(i));
            add(inventory, player.getOffhandItem());
        }
        Map<String,String> equipment = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = player.getItemBySlot(slot);
            equipment.put(slot.getSerializedName(), BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        }
        var biome = player.level().getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("");
        Set<String> effects = player.getActiveEffects().stream().map(e -> e.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("")).collect(Collectors.toSet());
        return Map.of("biome", biome, "position", Map.of("x", player.getX(), "y", player.getY(), "z", player.getZ()),
            "equipment", equipment, "effects", effects, "inventory", inventory);
    }

    private static void add(Map<String,Long> counts, ItemStack stack) {
        if (stack.isEmpty()) return;
        counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (long) stack.getCount(), Long::sum);
        stack.typeHolder().tags().forEach(t -> counts.merge("#" + t.location(), (long) stack.getCount(), Long::sum));
    }
}
