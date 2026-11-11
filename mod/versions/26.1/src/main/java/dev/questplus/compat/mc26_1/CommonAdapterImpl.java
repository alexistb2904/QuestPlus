package dev.questplus.compat.mc26_1;

import dev.questplus.compat.CommonAdapter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.PushReaction;

public final class CommonAdapterImpl implements CommonAdapter {
	@Override
	public PushReaction immovable() {
		return PushReaction.BLOCK;
	}

	@Override
	public void setPermanentlyInvulnerable(Entity entity, boolean invulnerable) {
		entity.setInvulnerable(invulnerable);
	}
}
