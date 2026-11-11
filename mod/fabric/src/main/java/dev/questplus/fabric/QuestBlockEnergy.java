package dev.questplus.fabric;

import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.base.SimpleEnergyStorage;

/** Loaded only when the optional, non-bundled Team Reborn API is installed. */
final class QuestBlockEnergy {
    static void register() {
        EnergyStorage.SIDED.registerForBlockEntity((be,side)-> {
            if(!be.input(side))return null;
            if(be.optionalEnergyStorage==null)be.optionalEnergyStorage=new SimpleEnergyStorage(1000000,10000,10000) {
            { amount=be.energy; }
            @Override protected Long createSnapshot() { return be.energy; }
            @Override protected void readSnapshot(Long value) { be.energy=value; }
            @Override public long getAmount() { return be.energy; }
            @Override public long getCapacity() { return Math.min(1000000,be.remaining("energy_submit")/be.energyRatio()); }
            @Override public long insert(long maximum,TransactionContext tx) {
                if(maximum<0)throw new IllegalArgumentException("Negative energy transfer");
                long inserted=Math.min(maximum,Math.min(10000,Math.max(0,getCapacity()-be.energy)));
                if(inserted>0){updateSnapshots(tx);be.energy+=inserted;}return inserted;
            }
            @Override public long extract(long maximum,TransactionContext tx) {
                if(maximum<0)throw new IllegalArgumentException("Negative energy transfer");
                if(!be.input(null))return 0;
                long taken=Math.min(Math.min(maximum,10000),be.energy);
                if(taken>0){updateSnapshots(tx);be.energy-=taken;}return taken;
            }
            @Override protected void onFinalCommit() { be.resourcesChanged(); }
            };
            var storage=(EnergyStorage)be.optionalEnergyStorage;
            return new EnergyStorage() {
                public long insert(long max,TransactionContext tx){return be.input(side)?storage.insert(max,tx):0;}
                public long extract(long max,TransactionContext tx){return be.input(side)?storage.extract(max,tx):0;}
                public long getAmount(){return be.input(side)?storage.getAmount():0;}
                public long getCapacity(){return be.input(side)?storage.getCapacity():0;}
            };
        },QuestWorldBlocks.ENTITY);
    }
}
