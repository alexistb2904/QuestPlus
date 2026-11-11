package dev.questplus.compat;

import java.util.List;
import java.util.Set;
import dev.questplus.platform.Loader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Applies a {@code questplus.<id>.mixins.json} config only when {@code <id>} is the running target. */
public final class TargetMixinPlugin implements IMixinConfigPlugin {
	private boolean active;

	static boolean belongsTo(String mixinPackage, String targetId) {
		return mixinPackage.startsWith("dev.questplus.compat." + targetId + ".");
	}

	/** NeoForge draws the HUD as GUI layers (the hooked methods do not exist); its glue hides those layers instead. */
	static boolean appliesOn(String loader, String mixinClassName) {
		return !(loader.equals("neoforge") && mixinClassName.endsWith(".TabletHudMixin"));
	}

	@Override
	public void onLoad(String mixinPackage) {
		active = belongsTo(mixinPackage, MinecraftTarget.active().id());
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return active && appliesOn(Loader.name(), mixinClassName);
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
