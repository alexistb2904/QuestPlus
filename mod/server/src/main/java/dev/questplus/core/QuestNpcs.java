package dev.questplus.core;

import com.google.gson.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Quest-giver NPCs authored in {@code config.json} under {@code "npcs"}. A
 * quest listed by an NPC only starts once a player accepts it from that NPC.
 */
public final class QuestNpcs {
	private static final String ID = "[a-z0-9_.-]+:[a-z0-9_./-]+";

	public record Spawn(String type, String target, double chance) {
		public boolean tag() {
			return target.startsWith("#");
		}
	}

	/**
	 * How the NPC looks: a villager dressed for {@code villager}'s biome, or the
	 * player model wearing {@code skin} (a 64×64 PNG data URL, empty for the mod's
	 * own skin).
	 */
	public record Look(String model, String villager, String skin) {
		public static final Look DEFAULT = new Look("villager", "plains", "");
	}

	public record Npc(String id, Map<String, String> name, List<String> quests, List<Spawn> spawns, Look look) {
	}

	public static final Set<String> VILLAGER_TYPES = Set.of("desert", "jungle", "plains", "savanna", "snow", "swamp",
			"taiga");
	private static final int MAX_SKIN_BYTES = 16384;

	private QuestNpcs() {
	}

	/**
	 * @param reference validates a structure ({@code "structure_visit"}) or
	 *                  dimension ({@code "dimension"}) target against the live
	 *                  registries
	 */
	public static Map<String, Npc> parse(JsonElement value, Set<String> questIds,
			BiConsumer<String, String> reference) {
		if (value == null)
			return Map.of();
		if (!value.isJsonArray() || value.getAsJsonArray().size() > 256)
			throw new IllegalArgumentException("npcs must be an array of at most 256 NPCs");
		Map<String, Npc> result = new LinkedHashMap<>();
		for (var element : value.getAsJsonArray()) {
			if (!element.isJsonObject())
				throw new IllegalArgumentException("Invalid NPC");
			var npc = element.getAsJsonObject();
			if (!Set.of("id", "name", "quests", "spawns", "model", "villager", "skin").containsAll(npc.keySet())
					|| !npc.keySet().containsAll(Set.of("id", "name", "quests")))
				throw new IllegalArgumentException("NPC requires id, name and quests");
			String id = string(npc.get("id"), "NPC id");
			if (!id.matches("[a-z0-9_.-]{1,64}") || result.containsKey(id))
				throw new IllegalArgumentException("Invalid/duplicate NPC id " + id);
			if (!npc.get("name").isJsonObject())
				throw new IllegalArgumentException("NPC name must be a locale map: " + id);
			Map<String, String> name = new HashMap<>();
			for (var entry : npc.getAsJsonObject("name").entrySet()) {
				String text = string(entry.getValue(), "NPC name");
				if (!entry.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || text.isBlank() || text.length() > 64)
					throw new IllegalArgumentException("Invalid NPC name: " + id);
				name.put(entry.getKey(), text);
			}
			if (!name.containsKey("en_us"))
				throw new IllegalArgumentException("NPC name requires en_us: " + id);
			if (!npc.get("quests").isJsonArray() || npc.getAsJsonArray("quests").size() > 64)
				throw new IllegalArgumentException("NPC offers at most 64 quests: " + id);
			Set<String> quests = new LinkedHashSet<>();
			for (var quest : npc.getAsJsonArray("quests")) {
				String questId = string(quest, "NPC quest");
				if (!questIds.contains(questId) || !quests.add(questId))
					throw new IllegalArgumentException("Unknown/duplicate quest " + questId + " offered by NPC " + id);
			}
			List<Spawn> spawns = new ArrayList<>();
			if (npc.has("spawns")) {
				if (!npc.get("spawns").isJsonArray() || npc.getAsJsonArray("spawns").size() > 64)
					throw new IllegalArgumentException("NPC spawns must be an array of at most 64 rules: " + id);
				for (var rule : npc.getAsJsonArray("spawns")) {
					if (!rule.isJsonObject() || !rule.getAsJsonObject().keySet().equals(Set.of("type", "target", "chance")))
						throw new IllegalArgumentException("NPC spawn requires type, target and chance: " + id);
					var spawn = rule.getAsJsonObject();
					String type = string(spawn.get("type"), "spawn type");
					String target = string(spawn.get("target"), "spawn target");
					if (!spawn.get("chance").isJsonPrimitive() || !spawn.getAsJsonPrimitive("chance").isNumber())
						throw new IllegalArgumentException("NPC spawn chance must be a number: " + id);
					double chance = spawn.get("chance").getAsDouble();
					if (!(chance >= 0 && chance <= 1))
						throw new IllegalArgumentException("NPC spawn chance must be within 0..1: " + id);
					if (type.equals("structure") && target.matches("#?" + ID))
						reference.accept("structure_visit", target);
					else if (type.equals("random") && target.matches(ID))
						reference.accept("dimension", target);
					else
						throw new IllegalArgumentException("Invalid NPC spawn " + type + " " + target + ": " + id);
					spawns.add(new Spawn(type, target, chance));
				}
			}
			result.put(id, new Npc(id, Map.copyOf(name), List.copyOf(quests), List.copyOf(spawns), look(npc, id)));
		}
		return Collections.unmodifiableMap(result);
	}

	private static Look look(JsonObject npc, String id) {
		String model = npc.has("model") ? string(npc.get("model"), "NPC model") : "villager";
		String villager = npc.has("villager") ? string(npc.get("villager"), "NPC villager type") : "plains";
		String skin = npc.has("skin") ? string(npc.get("skin"), "NPC skin") : "";
		if (!Set.of("villager", "player").contains(model))
			throw new IllegalArgumentException("NPC model must be villager or player: " + id);
		if (!VILLAGER_TYPES.contains(villager))
			throw new IllegalArgumentException("Unknown villager type " + villager + ": " + id);
		if (!skin.isEmpty()) {
			if (!model.equals("player"))
				throw new IllegalArgumentException("A skin needs the player model: " + id);
			validateSkin(skin, id);
		}
		return new Look(model, villager, skin);
	}

	/** A Minecraft skin: a 64×64 PNG of at most 16 KiB, as a base64 data URL. */
	static void validateSkin(String skin, String id) {
		String prefix = "data:image/png;base64,";
		byte[] png;
		try {
			if (!skin.startsWith(prefix))
				throw new IllegalArgumentException();
			png = Base64.getDecoder().decode(skin.substring(prefix.length()));
		} catch (IllegalArgumentException invalid) {
			throw new IllegalArgumentException("NPC skin must be a PNG data URL: " + id);
		}
		var header = java.nio.ByteBuffer.wrap(png);
		if (png.length > MAX_SKIN_BYTES || png.length < 24 || header.getLong(0) != 0x89504E470D0A1A0AL
				|| header.getInt(12) != 0x49484452 || header.getInt(16) != 64 || header.getInt(20) != 64)
			throw new IllegalArgumentException("NPC skin must be a 64×64 PNG of at most 16 KB: " + id);
	}

	/**
	 * The structure rule that applies: an exact structure ID wins over a tag, then
	 * the first listed rule.
	 */
	public static Optional<Spawn> structureRule(Npc npc, String structure, Set<String> tags) {
		Spawn tagged = null;
		for (var spawn : npc.spawns()) {
			if (!spawn.type().equals("structure"))
				continue;
			if (spawn.target().equals(structure))
				return Optional.of(spawn);
			if (tagged == null && spawn.tag() && tags.contains(spawn.target().substring(1)))
				tagged = spawn;
		}
		return Optional.ofNullable(tagged);
	}

	private static String string(JsonElement value, String field) {
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
			throw new IllegalArgumentException("Invalid " + field);
		return value.getAsString();
	}
}
