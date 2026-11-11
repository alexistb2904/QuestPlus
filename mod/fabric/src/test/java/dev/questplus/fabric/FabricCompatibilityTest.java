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
				{ "fabricloader", "0.19.4" }, { "fabric-api", "0.155.2+26.1.2" }, { "java", "24" } }) {
			assertFalse(VersionPredicate.parse(dependencies.get(entry[0]).getAsString())
					.test(Version.parse(entry[1])), entry[0] + " " + entry[1]);
		}
	}

	@Test
	void declaresExactlyTheValidatedMinecraftVersions() throws Exception {
		var declared = new java.util.ArrayList<String>();
		dependencies().getAsJsonArray("minecraft").forEach(v -> declared.add(v.getAsString()));
		var expected = java.util.Map.of("mc26_1", java.util.List.of("26.1", "26.1.1", "26.1.2"),
				"mc26_2", java.util.List.of("26.2"), "mc26_3", java.util.List.of("26.3"));
		var fromTargets = new java.util.ArrayList<String>();
		try (var stream = getClass().getResourceAsStream("/fabric.mod.json")) {
			var targets = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("custom").getAsJsonObject("questplus:targets");
			assertFalse(targets.isEmpty());
			for (var target : targets.entrySet()) {
				var versions = new java.util.ArrayList<String>();
				target.getValue().getAsJsonObject().getAsJsonArray("minecraft").forEach(v -> versions.add(v.getAsString()));
				if (expected.containsKey(target.getKey()))
					assertEquals(expected.get(target.getKey()), versions, target.getKey());
				fromTargets.addAll(versions);
			}
		}
		assertEquals(fromTargets, declared);
		for (String version : declared)
			assertTrue(version.matches("[0-9]+([.][0-9]+)+"), "Exact release versions only, no ranges: " + version);
		for (String untested : new String[] { "26.1-rc-1", "26.1.3", "26.2-pre-1", "26.3-rc-1", "26.4", "26.4-snapshot-1" })
			assertFalse(declared.stream().anyMatch(v -> {
				try {
					return VersionPredicate.parse(v).test(Version.parse(untested));
				} catch (Exception e) {
					throw new AssertionError(e);
				}
			}), "Undeclared Minecraft " + untested);
	}
}
