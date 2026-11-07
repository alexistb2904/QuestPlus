package dev.questplus.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Player.class)
public abstract class EntityInteractMixin {
	@WrapMethod(method = "interactOn")
	private InteractionResult questplus$interact(Entity target, InteractionHand hand, Vec3 location,
			Operation<InteractionResult> original) {
		var held = ((Player) (Object) this).getItemInHand(hand).copyWithCount(1);
		InteractionResult result = original.call(target, hand, location);
		if (result.consumesAction() && (Object) this instanceof ServerPlayer player) {
			AdvancedEvents.entity(player, "npc_interact", target);
			if (result instanceof InteractionResult.Success success && success.wasItemInteraction())
				AdvancedEvents.item(player, "item_use", held, 1);
		}
		return result;
	}
}
