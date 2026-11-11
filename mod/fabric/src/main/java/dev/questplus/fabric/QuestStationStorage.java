package dev.questplus.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorageUtil;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.ResourceAmount;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Fabric Transfer API view of a Submission Station's buffers ({@code mod/neoforge} has the NeoForge
 * one). The station entity owns the values. These storages only add transactions.
 */
public final class QuestStationStorage {
	private record Storages(SingleStackStorage items, Tank fluids) {}

	private QuestStationStorage() {}

	static void register(BlockEntityType<QuestUtilityEntity> type) {
		ItemStorage.SIDED.registerForBlockEntity((be, side) -> !be.input(side) ? null : new FilteringStorage<>(items(be)) {
			protected boolean canInsert(ItemVariant value) {return be.input(side);}
			protected boolean canExtract(ItemVariant value) {return be.input(side);}
		}, type);
		FluidStorage.SIDED.registerForBlockEntity((be, side) -> !be.input(side) ? null : new FilteringStorage<>(fluids(be)) {
			protected boolean canInsert(FluidVariant value) {return be.input(side);}
			protected boolean canExtract(FluidVariant value) {return be.input(side);}
		}, type);
		if (FabricLoader.getInstance().isModLoaded("team_reborn_energy")) QuestBlockEnergy.register();
	}

	public static SingleSlotStorage<ItemVariant> items(QuestUtilityEntity be) {
		return of(be).items();
	}

	public static SingleSlotStorage<FluidVariant> fluids(QuestUtilityEntity be) {
		return of(be).fluids();
	}

	/** Empties a held bucket/fluid container, otherwise inserts the held stack. */
	static boolean insertHeld(QuestUtilityEntity be, ServerPlayer actor, InteractionHand hand) {
		if (FluidStorageUtil.interactWithFluidStorage(fluids(be), actor, hand)) return true;
		var held = actor.getItemInHand(hand);
		try (var tx = Transaction.openOuter()) {
			long count = items(be).insert(ItemVariant.of(held), held.getCount(), tx);
			if (count <= 0) return false;
			held.shrink((int) count);
			tx.commit();
			return true;
		}
	}

	// One storage pair per entity: a transaction must snapshot each buffer through a single participant.
	private static Storages of(QuestUtilityEntity be) {
		if (be.storages == null) be.storages = new Storages(new SingleStackStorage() {
			protected ItemStack getStack() { return be.stack; }
			protected void setStack(ItemStack value) { be.stack = value; }
			protected boolean canInsert(ItemVariant v) { return be.accepts(v.toStack()); }
			protected boolean canExtract(ItemVariant v) { return be.input(null); }
			protected int getCapacity(ItemVariant v) { return be.itemCapacity(v.getItem()); }
			protected void onFinalCommit() { be.resourcesChanged(); }
		}, new Tank(be));
		return (Storages) be.storages;
	}

	/** {@code SingleVariantStorage} semantics, stored in the entity's {@code fluid}/{@code fluidAmount}. */
	private static final class Tank extends SnapshotParticipant<ResourceAmount<FluidVariant>> implements SingleSlotStorage<FluidVariant> {
		private final QuestUtilityEntity be;

		Tank(QuestUtilityEntity be) {
			this.be = be;
		}

		private void set(FluidVariant variant, long amount) {
			be.fluid = variant.isBlank() ? QuestFluid.EMPTY : new QuestFluid(variant.typeHolder(), variant.getComponentsPatch());
			be.fluidAmount = amount;
		}

		@Override
		public long insert(FluidVariant variant, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(variant, maxAmount);
			var current = getResource();
			if ((variant.equals(current) || current.isBlank()) && be.acceptsFluid(variant.getFluid())) {
				long inserted = Math.min(maxAmount, be.fluidCapacity() - be.fluidAmount);
				if (inserted > 0) {
					updateSnapshots(transaction);
					set(variant, current.isBlank() ? inserted : be.fluidAmount + inserted);
				}
				return inserted;
			}
			return 0;
		}

		@Override
		public long extract(FluidVariant variant, long maxAmount, TransactionContext transaction) {
			StoragePreconditions.notBlankNotNegative(variant, maxAmount);
			if (variant.equals(getResource()) && be.input(null)) {
				long extracted = Math.min(maxAmount, be.fluidAmount);
				if (extracted > 0) {
					updateSnapshots(transaction);
					long left = be.fluidAmount - extracted;
					set(left == 0 ? FluidVariant.blank() : variant, left);
				}
				return extracted;
			}
			return 0;
		}

		@Override
		public boolean isResourceBlank() {
			return be.fluid.isEmpty();
		}

		@Override
		public FluidVariant getResource() {
			return be.fluid.isEmpty() ? FluidVariant.blank() : FluidVariant.of(be.fluid.fluid().value(), be.fluid.components());
		}

		@Override
		public long getAmount() {
			return be.fluidAmount;
		}

		@Override
		public long getCapacity() {
			return be.fluidCapacity();
		}

		@Override
		protected ResourceAmount<FluidVariant> createSnapshot() {
			return new ResourceAmount<>(getResource(), be.fluidAmount);
		}

		@Override
		protected void readSnapshot(ResourceAmount<FluidVariant> snapshot) {
			set(snapshot.resource(), snapshot.amount());
		}

		@Override
		protected void onFinalCommit() {
			be.resourcesChanged();
		}
	}
}
