package dev.questplus.fabric.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(MerchantResultSlot.class)
public abstract class TradeMixin {
    @Shadow @Final private Player player;
    @WrapOperation(method="onTake", at=@At(value="INVOKE", target="Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V"))
    private void questplus$trade(Merchant merchant, MerchantOffer offer, Operation<Void> original) {
        original.call(merchant,offer);
        if (player instanceof ServerPlayer serverPlayer) AdvancedEvents.item(serverPlayer,"trade",offer.getResult(),1);
    }
}
