package dev.questplus.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.PushReaction;

/** Minecraft APIs used by both sides whose shape differs between targets. Keep it minimal. */
public interface CommonAdapter {
	CommonAdapter INSTANCE = MinecraftTarget.active().adapter("", CommonAdapter.class);

	/** Pistons neither push nor break the block. */
	PushReaction immovable();

	void setPermanentlyInvulnerable(Entity entity, boolean invulnerable);
}
