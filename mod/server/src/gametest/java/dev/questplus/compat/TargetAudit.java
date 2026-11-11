package dev.questplus.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Runs inside a real game: the running Minecraft picked its own adapters and every enabled mixin applies. */
public final class TargetAudit {
	private TargetAudit() {}

	public static void run(Object... adapters) {
		var target = MinecraftTarget.active();
		var minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata()
				.getVersion().getFriendlyString();
		if (!target.minecraft().equals(minecraft))
			throw new AssertionError("Selected target " + target + " for Minecraft " + minecraft);
		for (var adapter : adapters)
			if (!adapter.getClass().getPackageName().equals("dev.questplus.compat." + target.id()))
				throw new AssertionError(adapter.getClass() + " does not belong to " + target.id());
		// Loads every mixin target now; a missing injection point on this version fails here.
		MixinEnvironment.getCurrentEnvironment().audit();
	}
}
