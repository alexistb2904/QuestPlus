package dev.questplus.fabric.testmixin;

import dev.questplus.fabric.client.QuestClientGameTest;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client-test-only skin selection exercises both real vanilla arm meshes. */
@Mixin(AbstractClientPlayer.class)
abstract class TestSkinMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void questplus$testSkin(CallbackInfoReturnable<PlayerSkin> result) {
		if (QuestClientGameTest.skin != null)
			result.setReturnValue(QuestClientGameTest.skin);
	}
}
