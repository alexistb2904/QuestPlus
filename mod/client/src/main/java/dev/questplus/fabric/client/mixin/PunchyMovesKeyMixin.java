package dev.questplus.fabric.client.mixin;

import dev.questplus.fabric.client.QuestPlusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punchy Moves consumes emote shortcuts before vanilla can dispatch QuestPlus's
 * keybind.
 */
@Pseudo
@Mixin(targets = "punchymoves.client.emote.EmoteKeybindManager", remap = false)
abstract class PunchyMovesKeyMixin {
	@Inject(method = "handleKey", at = @At("HEAD"), cancellable = true, remap = false)
	private static void questplus$allowTabletShortcut(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		var client = Minecraft.getInstance();
		if (client.player != null && client.gui.screen() == null && QuestPlusClient.matchesOpenKey(event))
			cir.setReturnValue(false);
	}
}
