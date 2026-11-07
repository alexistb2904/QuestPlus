package dev.questplus.fabric.mixin;

import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TamableAnimal.class)
public abstract class TameMixin {
	@Inject(method = "tame", at = @At("RETURN"))
	private void questplus$tame(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer)
			AdvancedEvents.entity(serverPlayer, "tame_animal", (TamableAnimal) (Object) this);
	}
}
