package dev.questplus.compat;

import dev.questplus.platform.Loader;
import org.spongepowered.asm.mixin.MixinEnvironment;

/** Runs inside a real game: the running Minecraft picked its own adapters and every enabled mixin applies. */
public final class TargetAudit {
	private TargetAudit() {}

	public static void run(Object... adapters) {
		var target = MinecraftTarget.active();
		var minecraft = Loader.minecraftVersion();
		if (!target.minecraft().equals(minecraft))
			throw new AssertionError("Selected target " + target + " for Minecraft " + minecraft);
		for (var adapter : adapters)
			if (!adapter.getClass().getPackageName().equals("dev.questplus.compat." + target.id()))
				throw new AssertionError(adapter.getClass() + " does not belong to " + target.id());
		// Loads every mixin target now. A missing injection point on this version fails here.
		MixinEnvironment.getCurrentEnvironment().audit();
	}
}
