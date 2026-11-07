package dev.questplus.fabric;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class StudioRegistryAssetsTest {
	@TempDir
	Path root;

	private void put(String path, String text) throws Exception {
		Path p = root.resolve(path);
		Files.createDirectories(p.getParent());
		Files.writeString(p, text);
	}

	@Test
	void exportsOnlyRegisteredAssetsAndResolvesModelAndTranslations() throws Exception {
		put("assets/example/lang/fr_fr.json",
				"{\"item.example.gem\":\"Gemme bleue\",\"item.example.secret\":\"Invisible\"}");
		put("assets/example/items/gem.json",
				"{\"model\":{\"type\":\"minecraft:model\",\"model\":\"example:item/gem_model\"}}");
		put("assets/example/models/item/gem_model.json", "{\"textures\":{\"layer0\":\"example:item/gem_texture\"}}");
		Path png = root.resolve("assets/example/textures/item/gem_texture.png");
		Files.createDirectories(png.getParent());
		ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());
		JsonObject result = new StudioRegistryAssets(List.of(root)).collect(Map.of("items", Set.of("example:gem")));
		assertEquals("Gemme bleue",
				result.getAsJsonObject("names").getAsJsonObject("example:gem").get("fr_fr").getAsString());
		assertTrue(result.getAsJsonObject("textures").get("example:gem").getAsString()
				.startsWith("data:image/png;base64,"));
		assertFalse(result.getAsJsonObject("names").has("example:secret"));
	}

	@Test
	void rejectsTraversalAndIgnoresMissingResources() {
		var result = new StudioRegistryAssets(List.of(root))
				.collect(Map.of("items", Set.of("example:../secret", "example:missing")));
		assertTrue(result.getAsJsonObject("textures").isEmpty());
		assertTrue(result.getAsJsonObject("names").isEmpty());
	}
}
