package dev.questplus.platform;

import dev.questplus.compat.MinecraftTarget;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.CustomValue;

/**
 * Fabric implementation of the loader services. {@code mod/neoforge} declares the same public
 * methods (checked by {@code verifyPlatformApi}). Never touches Minecraft classes: mixin plugins call it.
 */
public final class Loader {
	private Loader() {}

	public static String name() {
		return "fabric";
	}

	public static String minecraftVersion() {
		return version("minecraft");
	}

	public static String apiName() {
		return "Fabric API";
	}

	public static String apiVersion() {
		return version("fabric-api");
	}

	public static int compareVersions(String a, String b) {
		try {
			return Version.parse(a).compareTo(Version.parse(b));
		} catch (VersionParsingException e) {
			throw new IllegalStateException("Unreadable Fabric API version " + a + " or " + b, e);
		}
	}

	public static Map<String, MinecraftTarget.Spec> targets() {
		var metadata = FabricLoader.getInstance().getModContainer("questplus").orElseThrow().getMetadata();
		return specs(metadata.getCustomValue("questplus:targets"));
	}

	/** Loads a target's adapter through its {@code questplus:<id><role>} Fabric entrypoint. */
	public static <T> T adapter(String target, String role, Class<T> type) {
		var key = "questplus:" + target + role;
		var adapters = FabricLoader.getInstance().getEntrypoints(key, type);
		if (adapters.size() != 1)
			throw new IllegalStateException("Expected one " + type.getSimpleName() + " for " + key + ", found " + adapters.size());
		return adapters.getFirst();
	}

	public static boolean isModLoaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	public static String modVersion() {
		return version("questplus");
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static List<Path> modRoots() {
		return FabricLoader.getInstance().getAllMods().stream().flatMap(mod -> mod.getRootPaths().stream()).toList();
	}

	static Map<String, MinecraftTarget.Spec> specs(CustomValue value) {
		if (value == null) throw new IllegalStateException("fabric.mod.json has no questplus:targets block");
		var specs = new LinkedHashMap<String, MinecraftTarget.Spec>();
		for (var entry : value.getAsObject()) {
			var spec = entry.getValue().getAsObject();
			var versions = new ArrayList<String>();
			for (var minecraft : spec.get("minecraft").getAsArray()) versions.add(minecraft.getAsString());
			specs.put(entry.getKey(), new MinecraftTarget.Spec(List.copyOf(versions), spec.get("fabric-api").getAsString()));
		}
		return specs;
	}

	private static String version(String mod) {
		return FabricLoader.getInstance().getModContainer(mod).map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}
}
