package dev.questplus.compat;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class FabricTargetMetadataTest {
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
