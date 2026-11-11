package dev.questplus.compat.mc26_1.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.questplus.fabric.client.TabletNativeFirstPersonRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * QuestPlus owns only the first-person hand pass while its tablet is visible. On 26.1 the method
 * itself draws the submitted hands at its end (and Iris swaps that end for its hand pass), so the
 * tablet replaces the vanilla arms instead of cancelling the whole method.
 */
@Mixin(ItemInHandRenderer.class)
abstract class TabletNativeFirstPersonMixin {
	@Unique
	private boolean questplus$tablet;

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void questplus$submitNativeHands(float partialTicks, PoseStack pose, SubmitNodeCollector collector,
			LocalPlayer player, int light, CallbackInfo ci) {
		questplus$tablet = TabletNativeFirstPersonRenderer.submit(pose, collector, light);
	}

	@WrapWithCondition(method = "renderHandsWithItems", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private boolean questplus$hideVanillaArms(ItemInHandRenderer renderer, AbstractClientPlayer player, float frameInterp,
			float xRot, InteractionHand hand, float attack, ItemStack item, float inverseArmHeight, PoseStack pose,
			SubmitNodeCollector collector, int light) {
		return !questplus$tablet;
	}
}
