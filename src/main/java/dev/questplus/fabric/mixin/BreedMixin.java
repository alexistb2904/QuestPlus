package dev.questplus.fabric.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.questplus.fabric.AdvancedEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(Animal.class)
public abstract class BreedMixin {
    @WrapMethod(method="finalizeSpawnChildFromBreeding")
    private void questplus$breed(ServerLevel level, Animal mate, AgeableMob child, Operation<Void> original) {
        Animal self = (Animal)(Object)this;
        var player = self.getLoveCause() != null ? self.getLoveCause() : mate.getLoveCause();
        original.call(level,mate,child);
        if (player != null) AdvancedEvents.entity(player,"breed_animal",self);
    }
}
