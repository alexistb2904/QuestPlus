package dev.questplus.fabric.client.benchmarkmixin;

import dev.questplus.fabric.client.QuestClientLoadTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class BenchmarkFrameMixin {
    @Inject(method="renderFrame",at=@At("HEAD"))
    private void benchmarkStart(boolean tick,CallbackInfo ci) { QuestClientLoadTest.frameStart(); }
    @Inject(method="renderFrame",at=@At("RETURN"))
    private void benchmarkEnd(boolean tick,CallbackInfo ci) { QuestClientLoadTest.frameEnd(); }
}
