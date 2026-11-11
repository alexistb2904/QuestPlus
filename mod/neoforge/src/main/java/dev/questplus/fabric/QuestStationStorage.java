package dev.questplus.fabric;

import dev.questplus.platform.neoforge.ModBus;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

public final class QuestStationStorage {
	private record Handlers(Items items, Tank fluids, Energy energy) {}

	private QuestStationStorage() {}

	static void register(BlockEntityType<QuestUtilityEntity> type) {
		// Only stations (kind 1) have buffers. Kind never changes, so cached capabilities stay valid.
		// Faces are checked on every operation instead of in the provider for the same reason.
		ModBus.capabilities.add(event -> {
			event.registerBlockEntity(Capabilities.Item.BLOCK, type,
					(be, side) -> be.kind() == 1 ? new SidedItems(of(be).items(), be, side) : null);
			event.registerBlockEntity(Capabilities.Fluid.BLOCK, type,
					(be, side) -> be.kind() == 1 ? new SidedFluids(of(be).fluids(), be, side) : null);
			event.registerBlockEntity(Capabilities.Energy.BLOCK, type,
					(be, side) -> be.kind() == 1 ? new SidedEnergy(of(be).energy(), be, side) : null);
		});
	}

	public static ResourceHandler<ItemResource> items(QuestUtilityEntity be) {
		return of(be).items();
	}

	public static ResourceHandler<FluidResource> fluids(QuestUtilityEntity be) {
		return of(be).fluids();
	}

	public static EnergyHandler energy(QuestUtilityEntity be) {
		return of(be).energy();
	}

	/** Empties a held bucket/fluid container, otherwise inserts the held stack. */
	static boolean insertHeld(QuestUtilityEntity be, ServerPlayer actor, InteractionHand hand) {
		if (FluidUtil.interactWithFluidHandler(actor, hand, be.getBlockPos(), fluids(be), null)) return true;
		var held = actor.getItemInHand(hand);
		if (held.isEmpty()) return false;
		try (var tx = Transaction.openRoot()) {
			int count = items(be).insert(0, ItemResource.of(held), held.getCount(), tx);
			if (count <= 0) return false;
			held.shrink(count);
			tx.commit();
			return true;
		}
	}

	// One handler set per entity: a transaction must snapshot each buffer through a single journal.
	private static Handlers of(QuestUtilityEntity be) {
		if (be.storages == null) be.storages = new Handlers(new Items(be), new Tank(be), new Energy(be));
		return (Handlers) be.storages;
	}

	private static final class Items extends SnapshotJournal<ItemStack> implements ResourceHandler<ItemResource> {
		private final QuestUtilityEntity be;

		Items(QuestUtilityEntity be) {
			this.be = be;
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public ItemResource getResource(int index) {
			return ItemResource.of(be.stack);
		}

		@Override
		public long getAmountAsLong(int index) {
			return be.stack.getCount();
		}

		@Override
		public long getCapacityAsLong(int index, ItemResource resource) {
			return be.itemCapacity(resource.isEmpty() ? net.minecraft.world.item.Items.AIR : resource.getItem());
		}

		@Override
		public boolean isValid(int index, ItemResource resource) {
			return be.accepts(resource.toStack());
		}

		@Override
		public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
			if ((be.stack.isEmpty() || resource.matches(be.stack)) && be.accepts(resource.toStack())) {
				int inserted = (int) Math.min(amount, be.itemCapacity(resource.getItem()) - be.stack.getCount());
				if (inserted > 0) {
					updateSnapshots(transaction);
					be.stack = resource.toStack(be.stack.getCount() + inserted);
				}
				return Math.max(0, inserted);
			}
			return 0;
		}

		@Override
		public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
			if (resource.matches(be.stack) && be.input(null)) {
				int extracted = Math.min(amount, be.stack.getCount());
				if (extracted > 0) {
					updateSnapshots(transaction);
					be.stack = be.stack.copyWithCount(be.stack.getCount() - extracted);
				}
				return extracted;
			}
			return 0;
		}

		@Override
		protected ItemStack createSnapshot() {
			return be.stack.copy();
		}

		@Override
		protected void revertToSnapshot(ItemStack snapshot) {
			be.stack = snapshot;
		}

		@Override
		protected void onRootCommit(ItemStack original) {
			be.resourcesChanged();
		}
	}

	private static final class Tank extends SnapshotJournal<Tank.State> implements ResourceHandler<FluidResource> {
		private record State(QuestFluid fluid, long amount) {}

		private final QuestUtilityEntity be;

		Tank(QuestUtilityEntity be) {
			this.be = be;
		}

		private boolean holds(FluidResource resource) {
			return !be.fluid.isEmpty() && resource.getFluid() == be.fluid.fluid().value()
					&& resource.getComponentsPatch().equals(be.fluid.components());
		}

		@Override
		public int size() {
			return 1;
		}

		@Override
		public FluidResource getResource(int index) {
			return be.fluid.isEmpty() ? FluidResource.EMPTY : FluidResource.of(be.fluid.fluid(), be.fluid.components());
		}

		@Override
		public long getAmountAsLong(int index) {
			return be.fluidAmount / 81;
		}

		@Override
		public long getCapacityAsLong(int index, FluidResource resource) {
			return be.fluidCapacity() / 81;
		}

		@Override
		public boolean isValid(int index, FluidResource resource) {
			return be.acceptsFluid(resource.getFluid());
		}

		@Override
		public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
			if ((be.fluid.isEmpty() || holds(resource)) && be.acceptsFluid(resource.getFluid())) {
				int inserted = (int) Math.min(amount, (be.fluidCapacity() - be.fluidAmount) / 81);
				if (inserted > 0) {
					updateSnapshots(transaction);
					be.fluid = new QuestFluid(resource.typeHolder(), resource.getComponentsPatch());
					be.fluidAmount += inserted * 81L;
				}
				return Math.max(0, inserted);
			}
			return 0;
		}

		@Override
		public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
			if (holds(resource) && be.input(null)) {
				int extracted = (int) Math.min(amount, be.fluidAmount / 81);
				if (extracted > 0) {
					updateSnapshots(transaction);
					be.fluidAmount -= extracted * 81L;
					if (be.fluidAmount == 0) be.fluid = QuestFluid.EMPTY;
				}
				return extracted;
			}
			return 0;
		}

		@Override
		protected State createSnapshot() {
			return new State(be.fluid, be.fluidAmount);
		}

		@Override
		protected void revertToSnapshot(State snapshot) {
			be.fluid = snapshot.fluid();
			be.fluidAmount = snapshot.amount();
		}

		@Override
		protected void onRootCommit(State original) {
			be.resourcesChanged();
		}
	}

	private static final class Energy extends SnapshotJournal<Long> implements EnergyHandler {
		private final QuestUtilityEntity be;

		Energy(QuestUtilityEntity be) {
			this.be = be;
		}

		@Override
		public long getAmountAsLong() {
			return be.energy;
		}

		@Override
		public long getCapacityAsLong() {
			return Math.min(1_000_000, be.remaining("energy_submit") / be.energyRatio());
		}

		@Override
		public int insert(int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonNegative(amount);
			int inserted = (int) Math.min(amount, Math.min(10_000, Math.max(0, getCapacityAsLong() - be.energy)));
			if (inserted > 0) {
				updateSnapshots(transaction);
				be.energy += inserted;
			}
			return inserted;
		}

		@Override
		public int extract(int amount, TransactionContext transaction) {
			TransferPreconditions.checkNonNegative(amount);
			if (!be.input(null)) return 0;
			int taken = (int) Math.min(Math.min(amount, 10_000), be.energy);
			if (taken > 0) {
				updateSnapshots(transaction);
				be.energy -= taken;
			}
			return taken;
		}

		@Override
		protected Long createSnapshot() {
			return be.energy;
		}

		@Override
		protected void revertToSnapshot(Long snapshot) {
			be.energy = snapshot;
		}

		@Override
		protected void onRootCommit(Long original) {
			be.resourcesChanged();
		}
	}

	private record SidedItems(Items handler, QuestUtilityEntity be, @Nullable Direction side) implements ResourceHandler<ItemResource> {
		public int size() { return 1; }
		public ItemResource getResource(int index) { return handler.getResource(index); }
		public long getAmountAsLong(int index) { return be.input(side) ? handler.getAmountAsLong(index) : 0; }
		public long getCapacityAsLong(int index, ItemResource resource) { return be.input(side) ? handler.getCapacityAsLong(index, resource) : 0; }
		public boolean isValid(int index, ItemResource resource) { return be.input(side) && handler.isValid(index, resource); }
		public int insert(int index, ItemResource resource, int amount, TransactionContext tx) { return be.input(side) ? handler.insert(index, resource, amount, tx) : 0; }
		public int extract(int index, ItemResource resource, int amount, TransactionContext tx) { return be.input(side) ? handler.extract(index, resource, amount, tx) : 0; }
	}

	private record SidedFluids(Tank handler, QuestUtilityEntity be, @Nullable Direction side) implements ResourceHandler<FluidResource> {
		public int size() { return 1; }
		public FluidResource getResource(int index) { return handler.getResource(index); }
		public long getAmountAsLong(int index) { return be.input(side) ? handler.getAmountAsLong(index) : 0; }
		public long getCapacityAsLong(int index, FluidResource resource) { return be.input(side) ? handler.getCapacityAsLong(index, resource) : 0; }
		public boolean isValid(int index, FluidResource resource) { return be.input(side) && handler.isValid(index, resource); }
		public int insert(int index, FluidResource resource, int amount, TransactionContext tx) { return be.input(side) ? handler.insert(index, resource, amount, tx) : 0; }
		public int extract(int index, FluidResource resource, int amount, TransactionContext tx) { return be.input(side) ? handler.extract(index, resource, amount, tx) : 0; }
	}

	private record SidedEnergy(Energy handler, QuestUtilityEntity be, @Nullable Direction side) implements EnergyHandler {
		public long getAmountAsLong() { return be.input(side) ? handler.getAmountAsLong() : 0; }
		public long getCapacityAsLong() { return be.input(side) ? handler.getCapacityAsLong() : 0; }
		public int insert(int amount, TransactionContext tx) { return be.input(side) ? handler.insert(amount, tx) : 0; }
		public int extract(int amount, TransactionContext tx) { return be.input(side) ? handler.extract(amount, tx) : 0; }
	}
}
