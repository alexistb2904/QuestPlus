package dev.questplus.platform.neoforge;

import dev.questplus.fabric.QuestPlusMod;
import dev.questplus.platform.Hooks;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod("questplus")
public final class QuestPlusNeoForge {
	public static final List<Hooks.BlockBreak> AFTER_BREAK = new CopyOnWriteArrayList<>();

	public QuestPlusNeoForge(IEventBus modBus, Dist dist) {
		ModBus.attach(modBus);
		if (dist.isClient()) ClientEntry.attach(modBus);
		// Every built-in registry stays unfrozen for the whole RegisterEvent pass, so the shared
		// register() methods (blocks, items, entities, tabs) run unchanged in one event. NeoForge fires
		// attributes, data components and particles first, so by BLOCK everything they read is bound.
		modBus.addListener((RegisterEvent event) -> {
			if (!event.getRegistryKey().equals(Registries.BLOCK)) return;
			QuestPlusMod.initialize();
			if (dist.isClient()) ClientEntry.initialize();
		});
	}

	public static void afterBreak(ServerPlayer player, BlockPos pos, BlockState state) {
		for (var listener : AFTER_BREAK) listener.broken(player, pos, state);
	}
}
