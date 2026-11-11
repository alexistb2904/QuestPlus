package dev.questplus.compat;

import dev.questplus.platform.Loader;
import java.util.List;
import java.util.Map;

/**
 * The validated Minecraft target of the running game. Targets come from the loader metadata that
 * Gradle generates from {@code mod/versions/*} ({@code questplus:targets} in fabric.mod.json,
 * {@code META-INF/questplus-targets.json} on NeoForge). Each one owns the adapter package
 * {@code dev.questplus.compat.<id>}.
 */
public record MinecraftTarget(String id, String minecraft) {
	/** Minecraft versions and minimum loader API (Fabric API or NeoForge) one target was tested with. */
	public record Spec(List<String> minecraft, String api) {}

	private static MinecraftTarget active;

	public static synchronized MinecraftTarget active() {
		if (active == null)
			active = select(Loader.targets(), Loader.minecraftVersion(), Loader.apiVersion());
		return active;
	}

	/** Picks the target that lists this exact Minecraft version. Never guesses a neighbour. */
	static MinecraftTarget select(Map<String, Spec> targets, String minecraft, String api) {
		for (var target : targets.entrySet()) {
			if (!target.getValue().minecraft().contains(minecraft)) continue;
			var minimum = target.getValue().api();
			if (api != null && Loader.compareVersions(api, minimum) < 0)
				throw new IllegalStateException("Quest Plus for Minecraft " + minecraft + " requires " + Loader.apiName()
						+ " " + minimum + " or newer (found " + api + ")");
			return new MinecraftTarget(target.getKey(), minecraft);
		}
		throw new IllegalStateException("Quest Plus has no validated adapter for Minecraft " + minecraft
				+ "; validated versions: " + targets.values().stream().flatMap(t -> t.minecraft().stream()).toList());
	}

	/** Loads this target's adapter for {@code role} ("" or "/client"). */
	public <T> T adapter(String role, Class<T> type) {
		return Loader.adapter(id, role, type);
	}
}
