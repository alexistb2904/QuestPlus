package dev.questplus.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The generated neoforge.mods.toml, questplus-targets.json and every target's mixin config must agree. */
final class NeoForgeTargetMetadataTest {
	@Test
	void everyDeclaredTargetShipsAGuardedMixinConfigAndAdapters() throws Exception {
		var targets = json("/META-INF/questplus-targets.json");
		var toml = text("/META-INF/neoforge.mods.toml");
		assertFalse(targets.isEmpty());
		for (var id : targets.keySet()) {
			var config = json("/questplus." + id + ".mixins.json");
			assertEquals("dev.questplus.compat." + id + ".mixin", config.get("package").getAsString());
			assertEquals(TargetMixinPlugin.class.getName(), config.get("plugin").getAsString());
			assertTrue(toml.contains("config = \"questplus." + id + ".mixins.json\""), id);
			for (var adapter : List.of("CommonAdapterImpl", "ClientAdapterImpl", "NeoForgeClientAdapterImpl"))
				assertNotNull(getClass().getResource("/dev/questplus/compat/" + id + "/" + adapter + ".class"), id + " " + adapter);
		}
		assertTrue(toml.contains("config = \"questplus.neoforge.mixins.json\""));
		assertFalse(toml.contains("${"), "Unexpanded placeholder");
	}

	/** Exact Minecraft releases, stable NeoForge builds only (26.1 and 26.1.1 have beta NeoForge only). */
	@Test
	void declaresExactlyTheValidatedVersionsOnStableNeoForge() throws Exception {
		var toml = text("/META-INF/neoforge.mods.toml");
		var declared = new ArrayList<String>();
		for (var target : json("/META-INF/questplus-targets.json").entrySet()) {
			var spec = target.getValue().getAsJsonObject();
			var neoforge = spec.get("neoforge").getAsString();
			assertTrue(neoforge.matches("[0-9]+([.][0-9]+)+"), "Stable NeoForge build only: " + neoforge);
			spec.getAsJsonArray("minecraft").forEach(version -> declared.add(version.getAsString()));
		}
		for (var version : declared) {
			assertTrue(version.matches("[0-9]+([.][0-9]+)+"), "Exact release versions only: " + version);
			assertTrue(toml.contains("[" + version + "]"), "mods.toml range lists " + version);
		}
		for (var unsupported : List.of("26.1", "26.1.1"))
			assertFalse(declared.contains(unsupported), "No stable NeoForge for Minecraft " + unsupported);
	}

	private JsonObject json(String path) throws Exception {
		try (var stream = open(path)) {
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private String text(String path) throws Exception {
		try (var stream = open(path)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private InputStream open(String path) {
		var stream = getClass().getResourceAsStream(path);
		assertNotNull(stream, path);
		return stream;
	}
}
