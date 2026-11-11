package dev.questplus.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MinecraftTargetTest {
	private static final Map<String, MinecraftTarget.Spec> TARGETS = new LinkedHashMap<>(Map.of(
			"mc26_1", new MinecraftTarget.Spec(List.of("26.1.2"), "1.155.3"),
			"mc26_3", new MinecraftTarget.Spec(List.of("26.3"), "1.161.0")));

	@Test
	void selectsTheTargetListingTheExactMinecraftVersion() {
		assertEquals("mc26_1", MinecraftTarget.select(TARGETS, "26.1.2", "1.155.3").id());
		assertEquals("mc26_3", MinecraftTarget.select(TARGETS, "26.3", "1.166.0").id());
	}

	@Test
	void neverGuessesANeighbouringTarget() {
		for (var minecraft : List.of("26.1", "26.2", "26.3.1", "26.4", "26.4-snapshot-8")) {
			var error = assertThrows(IllegalStateException.class,
					() -> MinecraftTarget.select(TARGETS, minecraft, "1.166.0"));
			assertTrue(error.getMessage().contains(minecraft), error.getMessage());
		}
	}

	@Test
	// Versions both loaders' comparators order the same way (Fabric API and NeoForge versions).
	void enforcesEachTargetsTestedLoaderApi() {
		var error = assertThrows(IllegalStateException.class,
				() -> MinecraftTarget.select(TARGETS, "26.3", "1.160.7"));
		assertTrue(error.getMessage().contains("1.161.0"), error.getMessage());
	}

	@Test
	void mixinPluginOnlyEnablesTheRunningTargetsPackage() {
		assertTrue(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_1.mixin", "mc26_1"));
		assertFalse(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_1.mixin", "mc26_2"));
		assertFalse(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_10.mixin", "mc26_1"));
	}

	@Test
	void neoForgeHidesTheHudThroughLayersInsteadOfTheHudMixin() {
		assertTrue(TargetMixinPlugin.appliesOn("fabric", "dev.questplus.compat.mc26_3.mixin.TabletHudMixin"));
		assertFalse(TargetMixinPlugin.appliesOn("neoforge", "dev.questplus.compat.mc26_3.mixin.TabletHudMixin"));
		assertTrue(TargetMixinPlugin.appliesOn("neoforge", "dev.questplus.compat.mc26_3.mixin.TabletNativeFirstPersonMixin"));
	}
}
