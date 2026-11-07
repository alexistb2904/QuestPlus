package dev.questplus.fabric;

import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/**
 * Replaceable trusted provider, with vanilla-persisted scoreboard tags as the
 * default.
 */
public final class QuestStages {
	public interface Provider {
		Set<String> stages(ServerPlayer player);

		void set(ServerPlayer player, String stage, boolean present);
	}

	private static final String PREFIX = "questplus.stage:";
	private static Provider provider = new Provider() {
		public Set<String> stages(ServerPlayer player) {
			var result = new HashSet<String>();
			for (String tag : player.entityTags())
				if (tag.startsWith(PREFIX))
					result.add(tag.substring(PREFIX.length()));
			return Set.copyOf(result);
		}

		public void set(ServerPlayer player, String stage, boolean present) {
			String tag = PREFIX + stage;
			if (present && !player.entityTags().contains(tag) && !player.addTag(tag))
				throw new IllegalStateException("Player stage tag limit reached");
			if (!present)
				player.removeTag(tag);
		}
	};
	private static boolean custom;

	private QuestStages() {
	}

	public static void register(Provider value) {
		if (custom)
			throw new IllegalStateException("Stage provider already registered");
		provider = Objects.requireNonNull(value);
		custom = true;
	}

	public static Set<String> stages(ServerPlayer player) {
		if (!player.level().getServer().isSameThread())
			throw new IllegalStateException("Stage access requires the server thread");
		return Set.copyOf(provider.stages(player));
	}

	public static void set(ServerPlayer player, String stage, boolean present) {
		if (!stage.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
			throw new IllegalArgumentException("Invalid stage ID");
		stages(player);
		provider.set(player, stage, present);
		QuestNetwork.invalidate(player.getUUID());
	}
}
