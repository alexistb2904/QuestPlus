package dev.questplus.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import team.reborn.energy.api.EnergyStorage;

/** Transactions operate exclusively on detached item stacks; the inventory journal commits the resulting plan. */
final class QuestResourceContainers {
    record Plan(List<ItemStack> stacks,int amount) {}
    private QuestResourceContainers() {}
    static Plan extract(List<ItemStack> inventory,String fluid,long requested,int energyRatio) {
        var after=new ArrayList<>(inventory.stream().map(ItemStack::copy).toList());
        List<SingleSlotStorage<ItemVariant>> slots=new ArrayList<>();
        for(int i=0;i<after.size();i++){final int slot=i;slots.add(new SingleStackStorage(){protected ItemStack getStack(){return after.get(slot);}protected void setStack(ItemStack value){after.set(slot,value);}});}
        long scale=fluid==null?energyRatio:FluidConstants.BUCKET/1000;
        long target=fluid==null?requested/scale:Math.multiplyExact(requested,scale),extracted=0;
        try(var transaction=Transaction.openOuter()) {
            for(int i=0;i<slots.size()&&extracted<target;i++) {
                final int index=i;
                var context=new ContainerItemContext(){
                    public SingleSlotStorage<ItemVariant> getMainSlot(){return slots.get(index);}
                    public List<SingleSlotStorage<ItemVariant>> getAdditionalSlots(){return slots;}
                    public long insertOverflow(ItemVariant variant,long max,TransactionContext tx){long inserted=0;for(var slot:slots)inserted+=slot.insert(variant,max-inserted,tx);return inserted;}
                };
                if(fluid==null) {
                    var storage=context.find(EnergyStorage.ITEM);if(storage!=null)extracted+=storage.extract(target-extracted,transaction);
                } else {
                    var storage=context.find(FluidStorage.ITEM);if(storage==null)continue;
                    for(var view:storage.nonEmptyViews()) {
                        var variant=view.getResource();
                        if(BuiltInRegistries.FLUID.getKey(variant.getFluid()).toString().equals(fluid))extracted+=storage.extract(variant,target-extracted,transaction);
                    }
                }
            }
            if(extracted<=0||fluid!=null&&extracted%scale!=0)throw new IllegalArgumentException("No extractable whole resource units in inventory containers");
            transaction.commit();
        }
        long amount=fluid==null?Math.multiplyExact(extracted,scale):extracted/scale;
        if(amount>requested||amount>1_000_000_000L)throw new IllegalStateException("Resource provider exceeded requested amount");
        return new Plan(List.copyOf(after),Math.toIntExact(amount));
    }
}
