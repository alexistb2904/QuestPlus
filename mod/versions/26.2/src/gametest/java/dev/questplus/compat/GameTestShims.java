package dev.questplus.compat;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.level.GameType;

/** Test-only APIs that differ on Minecraft 26.2. Compiled into this target's GameTests only, never shipped. */
public final class GameTestShims {
	public static final EntityType<Arrow> ARROW = EntityTypes.ARROW;
	public static final EntityType<Cow> COW = EntityTypes.COW;
	public static final EntityType<Skeleton> SKELETON = EntityTypes.SKELETON;
	public static final EntityType<Villager> VILLAGER = EntityTypes.VILLAGER;
	public static final EntityType<Wolf> WOLF = EntityTypes.WOLF;
	public static final EntityType<Zombie> ZOMBIE = EntityTypes.ZOMBIE;
	public static final EntityType<net.minecraft.world.entity.monster.warden.Warden> WARDEN = EntityTypes.WARDEN;
	public static final EntityType<net.minecraft.world.entity.boss.wither.WitherBoss> WITHER = EntityTypes.WITHER;

	private GameTestShims() {}

	public static ServerPlayer mockServerPlayer(GameTestHelper helper, GameType mode) {
		return (ServerPlayer) helper.makeMockServerPlayer(mode);
	}

	public static void clearInvulnerableTime(LivingEntity entity) {
		entity.invulnerableTime = 0;
	}
}
