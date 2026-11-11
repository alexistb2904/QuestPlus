package dev.questplus.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * The validated Minecraft target of the running game. Targets come from the
 * {@code questplus:targets} block that Gradle generates into fabric.mod.json from
 * {@code mod/versions/*}; each one owns the adapter package {@code dev.questplus.compat.<id>}.
 */
public record MinecraftTarget(String id, String minecraft) {
	/** Minecraft versions and minimum Fabric API one target was tested with. */
	public record Spec(List<String> minecraft, String fabricApi) {}

	private static MinecraftTarget active;

	public static synchronized MinecraftTarget active() {
		if (active == null) {
			var loader = FabricLoader.getInstance();
			var metadata = loader.getModContainer("questplus").orElseThrow().getMetadata();
			active = select(specs(metadata.getCustomValue("questplus:targets")), version(loader, "minecraft"),
					version(loader, "fabric-api"));
		}
		return active;
	}

	/** Picks the target that lists this exact Minecraft version; never guesses a neighbour. */
	static MinecraftTarget select(Map<String, Spec> targets, String minecraft, String fabricApi) {
		for (var target : targets.entrySet()) {
			if (!target.getValue().minecraft().contains(minecraft)) continue;
			var minimum = target.getValue().fabricApi();
			if (fabricApi != null && compare(fabricApi, minimum) < 0)
				throw new IllegalStateException("Quest Plus for Minecraft " + minecraft + " requires Fabric API "
						+ minimum + " or newer (found " + fabricApi + ")");
			return new MinecraftTarget(target.getKey(), minecraft);
		}
		throw new IllegalStateException("Quest Plus has no validated adapter for Minecraft " + minecraft
				+ "; validated versions: " + targets.values().stream().flatMap(t -> t.minecraft().stream()).toList());
	}

	/** Loads this target's adapter for {@code role} ("" or "/client") through its Fabric entrypoint. */
	public <T> T adapter(String role, Class<T> type) {
		var key = "questplus:" + id + role;
		var adapters = FabricLoader.getInstance().getEntrypoints(key, type);
		if (adapters.size() != 1)
			throw new IllegalStateException("Expected one " + type.getSimpleName() + " for " + key + ", found " + adapters.size());
		return adapters.getFirst();
	}

	static Map<String, Spec> specs(CustomValue value) {
		if (value == null) throw new IllegalStateException("fabric.mod.json has no questplus:targets block");
		var specs = new LinkedHashMap<String, Spec>();
		for (var entry : value.getAsObject()) {
			var spec = entry.getValue().getAsObject();
			var versions = new java.util.ArrayList<String>();
			for (var minecraft : spec.get("minecraft").getAsArray()) versions.add(minecraft.getAsString());
			specs.put(entry.getKey(), new Spec(List.copyOf(versions), spec.get("fabric-api").getAsString()));
		}
		return specs;
	}

	private static String version(FabricLoader loader, String mod) {
		return loader.getModContainer(mod).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	private static int compare(String found, String minimum) {
		try {
			return Version.parse(found).compareTo(Version.parse(minimum));
		} catch (VersionParsingException e) {
			throw new IllegalStateException("Unreadable Fabric API version " + found + " or " + minimum, e);
		}
	}
}
