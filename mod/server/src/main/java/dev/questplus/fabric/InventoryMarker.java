package dev.questplus.fabric;

import dev.questplus.fabric.mixin.PlayerListSaveInvoker;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.nbt.*;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

final class InventoryMarker {
	private static final String PREFIX = "questplus.inventory.";
	private final MinecraftServer server;
	private final Path directory;
	private final RegistryOps<Tag> nbtOps;
	private final RegistryOps<JsonElement> jsonOps;

	InventoryMarker(MinecraftServer server) {
		this.server = server;
		directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR);
		nbtOps = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
		jsonOps = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
	}

	void verifySavedInventory(UUID player, JsonArray expected) throws IOException {
		try {
			var nbt = NbtIo.readCompressed(directory.resolve(player + ".dat"), NbtAccounter.create(32 * 1024 * 1024));
			var stacks = new ArrayList<ItemStack>(Collections.nCopies(37, ItemStack.EMPTY));
			var seen = new HashSet<Integer>();
			if (nbt.contains("Inventory")) {
				for (var entry : ItemStackWithSlot.CODEC.listOf().parse(nbtOps, nbt.get("Inventory")).getOrThrow()) {
					if (entry.slot() < 0 || entry.slot() >= 36 || !seen.add(entry.slot()))
						throw new IOException("Invalid saved inventory slot");
					stacks.set(entry.slot(), entry.stack());
				}
			}
			if (nbt.contains("equipment"))
				stacks.set(36, EntityEquipment.CODEC.parse(nbtOps, nbt.get("equipment")).getOrThrow()
						.get(EquipmentSlot.OFFHAND));
			var actual = new JsonArray();
			for (var stack : stacks)
				actual.add(ItemStack.OPTIONAL_CODEC.encodeStart(jsonOps, stack).getOrThrow());
			if (!actual.equals(expected))
				throw new IOException("Saved inventory/components differ from the submission plan");
		} catch (RuntimeException error) {
			throw new IOException("Cannot verify saved inventory/components", error);
		}
	}

	static JsonObject experience(ServerPlayer player) {
		var result = new JsonObject();
		result.addProperty("level", player.experienceLevel);
		result.addProperty("progress", player.experienceProgress);
		result.addProperty("total", player.totalExperience);
		return result;
	}

	static void experience(ServerPlayer player, JsonObject value) {
		player.experienceLevel = value.get("level").getAsInt();
		player.experienceProgress = value.get("progress").getAsFloat();
		player.totalExperience = value.get("total").getAsInt();
		player.giveExperienceLevels(0);
	}

	void verifySavedExperience(UUID player, JsonObject expected) throws IOException {
		var nbt = NbtIo.readCompressed(directory.resolve(player + ".dat"), NbtAccounter.create(32 * 1024 * 1024));
		if (nbt.getInt("XpLevel").orElse(-1) != expected.get("level").getAsInt()
				|| Float.compare(nbt.getFloat("XpP").orElse(-1f), expected.get("progress").getAsFloat()) != 0
				|| nbt.getInt("XpTotal").orElse(-1) != expected.get("total").getAsInt())
			throw new IOException("Saved experience differs from the submission plan");
	}

	private static String marker(Collection<String> tags) throws IOException {
		var values = tags.stream().filter(tag -> tag.startsWith(PREFIX)).toList();
		if (values.size() > 1)
			throw new IOException("Multiple inventory markers");
		if (values.isEmpty())
			return "";
		String value = values.getFirst().substring(PREFIX.length());
		try {
			UUID.fromString(value);
		} catch (IllegalArgumentException error) {
			throw new IOException("Invalid inventory marker", error);
		}
		return value;
	}

	String saved(UUID player) throws IOException {
		Path file = directory.resolve(player + ".dat");
		var nbt = NbtIo.readCompressed(file, NbtAccounter.create(32 * 1024 * 1024));
		var tags = new ArrayList<String>();
		for (var tag : nbt.getListOrEmpty("Tags"))
			tags.add(tag.asString().orElseThrow(() -> new IOException("Invalid player tag")));
		return marker(tags);
	}

	String live(ServerPlayer player) throws IOException {
		return marker(player.entityTags());
	}

	void set(ServerPlayer player, String previous, UUID operation) throws IOException {
		if (!live(player).equals(previous))
			throw new IOException("Inventory marker changed");
		if (!previous.isEmpty() && !player.removeTag(PREFIX + previous))
			throw new IOException("Cannot remove old marker");
		if (!player.addTag(PREFIX + operation))
			throw new IOException("Cannot add inventory marker");
	}

	// TODO(Codex P0): inject real power-loss/disk-full faults and measure
	// synchronous disk latency.
	void save(ServerPlayer player, String expected, JsonArray inventory) throws IOException {
		((PlayerListSaveInvoker) server.getPlayerList()).questplus$playerIo().save(player);
		Path file = directory.resolve(player.getUUID() + ".dat");
		try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
			channel.force(true);
		}
		if (!saved(player.getUUID()).equals(expected))
			throw new IOException("Player save marker did not persist");
		verifySavedInventory(player.getUUID(), inventory);
	}
}
