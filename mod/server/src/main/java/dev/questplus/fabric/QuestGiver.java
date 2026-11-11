package dev.questplus.fabric;

import dev.questplus.platform.Platform;
import dev.questplus.compat.CommonAdapter;

import dev.questplus.core.Localization;
import dev.questplus.core.QuestNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The mod's quest-giver NPC. It stands where it spawned, never despawns, and
 * opens its quest dialog when a player interacts with it.
 */
public final class QuestGiver extends PathfinderMob {
	public static EntityType<QuestGiver> TYPE;

	private record Pending(ResourceKey<Level> dimension, BlockPos column, int minY, int maxY, String npc) {
	}

	/** Spawns rolled at generation, placed once their column is loaded. */
	private static final List<Pending> PENDING = new ArrayList<>();
	/** Synced so the client can pick this NPC's appearance. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<String> NPC = net.minecraft.network.syncher.SynchedEntityData
			.defineId(QuestGiver.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);

	public QuestGiver(EntityType<? extends QuestGiver> type, Level level) {
		super(type, level);
		// Creative players and /kill can still remove it.
		CommonAdapter.INSTANCE.setPermanentlyInvulnerable(this, true);
		setPersistenceRequired();
	}

	static void register() {
		var key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.parse("questplus:quest_giver"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<QuestGiver>of(QuestGiver::new, MobCategory.MISC).sized(.6f, 1.95f).eyeHeight(1.62f)
						.clientTrackingRange(10).build(key));
		Platform.registerAttributes(TYPE, Mob.createMobAttributes());
		Platform.onChunkGenerated(QuestGiver::generated);
		Platform.onServerTick(QuestGiver::placePending);
		Platform.onServerStopped(server -> PENDING.clear());
		QuestGiverNetwork.register();
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(NPC, "");
	}

	public String npc() {
		return entityData.get(NPC);
	}

	void npc(QuestNpcs.Npc definition) {
		entityData.set(NPC, definition.id());
		// shortcut: the floating name uses the en_us name. Localize client-side if packs need per-player names.
		setCustomName(Component.literal(Localization.text(definition.name(), "en_us", Map.of())));
		setCustomNameVisible(true);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer actor)
			QuestGiverNetwork.open(actor, this);
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putString("questplus_npc", npc());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(NPC, input.getStringOr("questplus_npc", ""));
	}

	/** Each structure start and chunk is generated once, so every roll happens once. */
	private static void generated(ServerLevel level, LevelChunk chunk) {
		Map<String, QuestNpcs.Npc> npcs;
		try {
			npcs = QuestPlusMod.runtime().npcs();
		} catch (IllegalStateException unavailable) {
			return;
		}
		if (npcs.isEmpty())
			return;
		var random = level.getRandom();
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (var start : chunk.getAllStarts().values()) {
			if (!start.isValid() || !start.getChunkPos().equals(chunk.getPos()) || start.getPieces().isEmpty())
				continue;
			String id = structures.getKey(start.getStructure()).toString();
			Set<String> tags = structures.wrapAsHolder(start.getStructure()).tags().map(t -> t.location().toString())
					.collect(Collectors.toSet());
			// The first piece is the structure's centre: a village's meeting point.
			var box = start.getPieces().getFirst().getBoundingBox();
			for (var npc : npcs.values())
				QuestNpcs.structureRule(npc, id, tags).filter(rule -> random.nextDouble() < rule.chance())
						.ifPresent(rule -> PENDING.add(
								new Pending(level.dimension(), box.getCenter(), box.minY(), box.maxY(), npc.id())));
		}
		String dimension = level.dimension().identifier().toString();
		for (var npc : npcs.values())
			if (npc.spawns().stream().anyMatch(rule -> rule.type().equals("random") && rule.target().equals(dimension)
					&& random.nextDouble() < rule.chance()))
				PENDING.add(new Pending(level.dimension(),
						chunk.getPos().getBlockAt(random.nextInt(16), 0, random.nextInt(16)), 0, -1, npc.id()));
	}

	private static void placePending(MinecraftServer server) {
		if (PENDING.isEmpty() || server.getTickCount() % 10 != 0)
			return;
		Map<String, QuestNpcs.Npc> npcs;
		try {
			npcs = QuestPlusMod.runtime().npcs();
		} catch (IllegalStateException unavailable) {
			return;
		}
		// shortcut: pending spawns live in memory. A restart before the column loads drops that spawn.
		var iterator = PENDING.iterator();
		while (iterator.hasNext()) {
			var pending = iterator.next();
			var level = server.getLevel(pending.dimension());
			var npc = npcs.get(pending.npc());
			if (level == null || npc == null) {
				iterator.remove();
				continue;
			}
			if (!level.hasChunkAt(pending.column()))
				continue;
			iterator.remove();
			var at = spot(level, pending);
			if (at != null)
				spawn(level, npc, at);
		}
	}

	static QuestGiver spawn(ServerLevel level, QuestNpcs.Npc npc, BlockPos at) {
		var giver = TYPE.create(level, EntitySpawnReason.STRUCTURE);
		if (giver == null)
			return null;
		giver.npc(npc);
		giver.snapTo(at.getX() + .5, at.getY(), at.getZ() + .5, level.getRandom().nextFloat() * 360, 0);
		level.addFreshEntity(giver);
		return giver;
	}

	/** Lowest standable block in the structure's column, else the surface. Null over water or lava. */
	private static BlockPos spot(ServerLevel level, Pending pending) {
		var column = pending.column();
		for (int y = pending.minY(); y <= pending.maxY(); y++) {
			var at = new BlockPos(column.getX(), y, column.getZ());
			if (standable(level, at))
				return at;
		}
		// Under a ceiling the heightmap is the bedrock roof: climb from above the lava sea instead.
		if (level.dimensionType().hasCeiling()) {
			int top = level.getMinY() + level.dimensionType().logicalHeight() - 2;
			for (int y = level.getSeaLevel() + 1; y < top; y++) {
				var at = new BlockPos(column.getX(), y, column.getZ());
				if (standable(level, at))
					return at;
			}
			return null;
		}
		var surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		return standable(level, surface) ? surface : null;
	}

	private static boolean standable(ServerLevel level, BlockPos at) {
		var below = at.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getFluidState(at).isEmpty()
				&& level.getBlockState(at).getCollisionShape(level, at).isEmpty()
				&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
	}
}
