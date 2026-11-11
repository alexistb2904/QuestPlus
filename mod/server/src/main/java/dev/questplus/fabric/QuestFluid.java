package dev.questplus.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Fluid buffered by a Submission Station. {@link #CODEC} writes exactly the JSON of Fabric's
 * {@code FluidVariant.CODEC} ({@code {"fluid": id, "components": patch}}), so station accounts saved
 * before the NeoForge port load unchanged, and move between loaders.
 */
public record QuestFluid(Holder<Fluid> fluid, DataComponentPatch components) {
	public static final QuestFluid EMPTY = new QuestFluid(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.EMPTY), DataComponentPatch.EMPTY);
	public static final Codec<QuestFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("fluid").forGetter(QuestFluid::fluid),
			DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(QuestFluid::components))
			.apply(instance, QuestFluid::new));

	public boolean isEmpty() {
		return fluid.value() == Fluids.EMPTY;
	}
}
