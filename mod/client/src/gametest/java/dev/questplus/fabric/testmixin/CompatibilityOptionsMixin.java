package dev.questplus.fabric.testmixin;

import net.fabricmc.fabric.impl.client.gametest.context.ClientGameTestContextImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mod keybindings are registered after Fabric captures its default option
 * snapshot.
 */
@Mixin(ClientGameTestContextImpl.class)
abstract class CompatibilityOptionsMixin {
	@Unique
	private boolean questplus$captured;

	@Inject(method = "restoreDefaultGameOptions", at = @At("HEAD"), remap = false)
	private void questplus$captureModOptions(CallbackInfo ci) {
		if (!Boolean.getBoolean("questplus.tabletCompatibility") || questplus$captured)
			return;
		questplus$captured = true;
		((ClientGameTestContextImpl) (Object) this)
				.runOnClient(client -> ClientGameTestContextImpl.initGameOptions(client.options));
	}
}
