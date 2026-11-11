package dev.questplus.compat.mc26_3;

import dev.questplus.compat.CommonAdapter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.PushReaction;

public final class CommonAdapterImpl implements CommonAdapter {
	@Override
	public PushReaction immovable() {
		return PushReaction.IMMOVEABLE;
	}

	@Override
	public void setPermanentlyInvulnerable(Entity entity, boolean invulnerable) {
		entity.setPermanentlyInvulnerable(invulnerable);
	}
}
