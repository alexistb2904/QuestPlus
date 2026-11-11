package dev.questplus.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MinecraftTargetTest {
	private static final Map<String, MinecraftTarget.Spec> TARGETS = new LinkedHashMap<>(Map.of(
			"mc26_1", new MinecraftTarget.Spec(List.of("26.1.2"), "0.155.3+26.1.2"),
			"mc26_3", new MinecraftTarget.Spec(List.of("26.3"), "0.161.0+26.3")));

	@Test
	void selectsTheTargetListingTheExactMinecraftVersion() {
		assertEquals("mc26_1", MinecraftTarget.select(TARGETS, "26.1.2", "0.155.3+26.1.2").id());
		assertEquals("mc26_3", MinecraftTarget.select(TARGETS, "26.3", "0.166.0+26.3").id());
	}

	@Test
	void neverGuessesANeighbouringTarget() {
		for (var minecraft : List.of("26.1", "26.2", "26.3.1", "26.4", "26.4-snapshot-8")) {
			var error = assertThrows(IllegalStateException.class,
					() -> MinecraftTarget.select(TARGETS, minecraft, "0.166.0"));
			assertTrue(error.getMessage().contains(minecraft), error.getMessage());
		}
	}

	@Test
	void enforcesEachTargetsTestedFabricApi() {
		var error = assertThrows(IllegalStateException.class,
				() -> MinecraftTarget.select(TARGETS, "26.3", "0.160.7+26.3"));
		assertTrue(error.getMessage().contains("0.161.0+26.3"), error.getMessage());
	}

	@Test
	void mixinPluginOnlyEnablesTheRunningTargetsPackage() {
		assertTrue(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_1.mixin", "mc26_1"));
		assertFalse(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_1.mixin", "mc26_2"));
		assertFalse(TargetMixinPlugin.belongsTo("dev.questplus.compat.mc26_10.mixin", "mc26_1"));
	}

	/** The generated fabric.mod.json and every target's mixin config must agree on ids, packages and plugin. */
	@Test
	void everyDeclaredTargetShipsAGuardedMixinConfigAndAdapters() throws Exception {
		var metadata = json("/fabric.mod.json");
		var targets = metadata.getAsJsonObject("custom").getAsJsonObject("questplus:targets");
		assertFalse(targets.isEmpty());
		for (var id : targets.keySet()) {
			var config = json("/questplus." + id + ".mixins.json");
			assertEquals("dev.questplus.compat." + id + ".mixin", config.get("package").getAsString());
			assertEquals(TargetMixinPlugin.class.getName(), config.get("plugin").getAsString());
			assertTrue(metadata.getAsJsonArray("mixins").toString().contains("questplus." + id + ".mixins.json"));
			var entrypoints = metadata.getAsJsonObject("entrypoints");
			assertEquals("dev.questplus.compat." + id + ".CommonAdapterImpl",
					entrypoints.getAsJsonArray("questplus:" + id).get(0).getAsString());
			assertEquals("dev.questplus.compat." + id + ".ClientAdapterImpl",
					entrypoints.getAsJsonArray("questplus:" + id + "/client").get(0).getAsString());
			for (var adapter : List.of("CommonAdapterImpl", "ClientAdapterImpl"))
				assertNotNull(getClass().getResource("/dev/questplus/compat/" + id + "/" + adapter + ".class"),
						id + " " + adapter);
		}
	}

	private JsonObject json(String path) throws Exception {
		try (var stream = getClass().getResourceAsStream(path)) {
			assertNotNull(stream, path);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
