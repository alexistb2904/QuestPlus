package dev.questplus.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FabricCompatibilityTest {
	private JsonObject dependencies() throws Exception {
		try (var stream = getClass().getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(stream);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("depends");
		}
	}

	@Test
	void acceptsMinimumInstalledAndCurrentFabricVersions() throws Exception {
		var dependencies = dependencies();
		for (String[] pair : new String[][] {
				{ "0.19.5", "0.161.0+26.3" },
				{ "0.19.5", "0.164.0+26.3" },
				{ "0.20.1", "0.165.2+26.3" } }) {
			assertTrue(VersionPredicate.parse(dependencies.get("fabricloader").getAsString())
					.test(Version.parse(pair[0])), "Loader " + pair[0]);
			assertTrue(VersionPredicate.parse(dependencies.get("fabric-api").getAsString())
					.test(Version.parse(pair[1])), "Fabric API " + pair[1]);
		}
	}

	@Test
	void rejectsVersionsBelowBundledAnimationAndGameRequirements() throws Exception {
		var dependencies = dependencies();
		for (String[] entry : new String[][] {
				{ "fabricloader", "0.19.4" }, { "fabric-api", "0.160.7+26.3" },
				{ "minecraft", "26.2" }, { "minecraft", "26.4" }, { "java", "24" } }) {
			assertFalse(VersionPredicate.parse(dependencies.get(entry[0]).getAsString())
					.test(Version.parse(entry[1])), entry[0] + " " + entry[1]);
		}
	}
}
