package dev.questplus.fabric;

import dev.questplus.compat.CommonAdapter;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/**
 * Cosmetic stage for a crate the server has already rolled, consumed and
 * entitled. Rewards never depend on this block: it only carries what to show,
 * removes itself after {@link #DURATION}, and cannot be mined, pushed or looted.
 */
public final class QuestLootChest extends Block implements EntityBlock {
	/** Animation beats in ticks from placement; the client renderer uses the same clock. */
	public static final int OPEN = 40, BURST = 84, COLLECT = 125, DURATION = 150;
	/** Auto-delivery waits until the loot flies to its owner. */
	static final int DELIVER = COLLECT + 5;
	public static QuestLootChest BLOCK;
	public static BlockEntityType<Stage> ENTITY;
	/** Set by the client entrypoint; dedicated servers never load client code. */
	public static BlockEntityTicker<Stage> clientTicker;
	private static final VoxelShape SHAPE = box(1, 0, 2, 15, 14, 14);

	private QuestLootChest(Properties properties) {
		super(properties);
	}

	static void register() {
		var id = Identifier.parse("questplus:loot_chest");
		var key = ResourceKey.create(Registries.BLOCK, id);
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key,
				new QuestLootChest(BlockBehaviour.Properties.of().setId(key).strength(-1, 3_600_000).noLootTable()
						.noOcclusion().lightLevel(s -> 10).sound(SoundType.WOOD).pushReaction(CommonAdapter.INSTANCE.immovable())));
		ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id,
				FabricBlockEntityTypeBuilder.create(Stage::new, BLOCK).build());
	}

	/** Where a crate used on {@code clicked} would stand, or null when it cannot be staged there. */
	static BlockPos target(Level level, BlockPos clicked, Direction face) {
		var pos = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
		var state = level.getBlockState(pos);
		return level.isInWorldBounds(pos) && state.canBeReplaced() && state.getFluidState().isEmpty() ? pos : null;
	}

	static boolean place(ServerLevel level, BlockPos pos, ServerPlayer owner, QuestRuntime.OpenedCrate crate) {
		if (!pos.equals(target(level, pos, Direction.UP)) || !level.setBlock(pos, BLOCK.defaultBlockState(), 3)
				|| !(level.getBlockEntity(pos) instanceof Stage stage))
			return false;
		stage.start = level.getGameTime();
		stage.rarity = crate.rarity();
		stage.items = crate.display();
		stage.owner = owner.getUUID();
		stage.facing = owner.getDirection().getOpposite().get2DDataValue();
		stage.setChanged();
		level.sendBlockUpdated(pos, stage.getBlockState(), stage.getBlockState(), 3);
		return true;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Stage(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (type != ENTITY)
			return null;
		return (BlockEntityTicker<T>) (level.isClientSide() ? clientTicker : (BlockEntityTicker<Stage>) Stage::serverTick);
	}

	public static final class Stage extends BlockEntity {
		/** Game time of placement; -1 (e.g. /setblock) removes the stage on its first tick. */
		public long start = -1;
		public int rarity;
		public List<ItemStack> items = List.of();
		public UUID owner;
		/** Horizontal direction the lock faces (2D data value). */
		public int facing;
		/** Client only: last animation beat already played. */
		public int clientAge = -1;

		public Stage(BlockPos pos, BlockState state) {
			super(ENTITY, pos, state);
		}

		private static void serverTick(Level level, BlockPos pos, BlockState state, Stage stage) {
			long age = level.getGameTime() - stage.start;
			if (stage.start < 0 || age < 0 || age >= DURATION)
				level.removeBlock(pos, false);
		}

		@Override
		protected void saveAdditional(ValueOutput out) {
			super.saveAdditional(out);
			out.putLong("start", start);
			out.putInt("rarity", rarity);
			out.putInt("facing", facing);
			out.store("items", ItemStack.CODEC.listOf(), items);
			if (owner != null)
				out.putString("owner", owner.toString());
		}

		@Override
		protected void loadAdditional(ValueInput in) {
			super.loadAdditional(in);
			start = in.getLongOr("start", -1);
			rarity = Math.clamp(in.getIntOr("rarity", 0), 0, 4);
			facing = Math.clamp(in.getIntOr("facing", 0), 0, 3);
			items = in.read("items", ItemStack.CODEC.listOf()).map(l -> List.copyOf(l.subList(0, Math.min(8, l.size()))))
					.orElse(List.of());
			try {
				owner = in.getString("owner").map(UUID::fromString).orElse(null);
			} catch (IllegalArgumentException malformed) {
				owner = null;
			}
		}

		@Override
		public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) {
			return saveCustomOnly(registries);
		}

		@Override
		public ClientboundBlockEntityDataPacket getUpdatePacket() {
			return ClientboundBlockEntityDataPacket.create(this);
		}
	}
}
