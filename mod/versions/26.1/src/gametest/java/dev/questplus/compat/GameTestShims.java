package dev.questplus.compat;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;

/** Test-only APIs that differ on Minecraft 26.1. Compiled into this target's GameTests only, never shipped. */
public final class GameTestShims {
	public static final EntityType<Arrow> ARROW = EntityType.ARROW;
	public static final EntityType<Cow> COW = EntityType.COW;
	public static final EntityType<Skeleton> SKELETON = EntityType.SKELETON;
	public static final EntityType<Villager> VILLAGER = EntityType.VILLAGER;
	public static final EntityType<Wolf> WOLF = EntityType.WOLF;
	public static final EntityType<Zombie> ZOMBIE = EntityType.ZOMBIE;

	private GameTestShims() {}

	public static ServerPlayer mockServerPlayer(GameTestHelper helper, GameType mode) {
		// 26.1 has no makeMockServerPlayer; same body as the 26.2 helper.
		var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault()) {
			@Override
			public GameType gameMode() {
				return mode;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		mode.updatePlayerAbilities(player.getAbilities());
		return player;
	}

	public static void clearInvulnerableTime(LivingEntity entity) {
		entity.invulnerableTime = 0;
	}
}
