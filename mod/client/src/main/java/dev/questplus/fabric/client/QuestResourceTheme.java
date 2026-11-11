package dev.questplus.fabric.client;

import dev.questplus.clientmodel.*;
import dev.questplus.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * Local resource-pack assets only. Theme files cannot fetch URLs or read
 * arbitrary files.
 */
public final class QuestResourceTheme {
	private static QuestThemeRules theme = QuestThemeRules.parse("");
	private static Map<String, String> global = Map.of();
	private static final Map<String, Map<String, String>> CACHE = new HashMap<>();
	private static long catalogue = -1;
	private static final Set<String> SHAPES = new HashSet<>();

	private QuestResourceTheme() {
	}

	static void reload() {
		CACHE.clear();
		for (String shape : SHAPES)
			QuestShape.removeCustom(shape);
		SHAPES.clear();
		theme = QuestThemeRules.parse("");
		var resources = Minecraft.getInstance().getResourceManager();
		for (String name : List.of("ftbquests:ftb_quests_theme.txt", "questplus:quest_theme.txt")) {
			var resource = resources.getResource(Identifier.parse(name));
			if (resource.isEmpty())
				continue;
			try (var stream = resource.get().open()) {
				byte[] bytes = stream.readNBytes(65537);
				if (bytes.length > 65536)
					throw new IllegalArgumentException("Theme exceeds 64 KiB");
				theme = QuestThemeRules.parse(new String(bytes, StandardCharsets.UTF_8));
			} catch (Exception error) {
				dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Cannot load quest theme {}: {}", name,
						error.getMessage());
			}
		}
		global = theme.properties("", List.of());
	}

	public static int global(String property, int fallback) {
		return QuestThemeRules.color(global, property, fallback);
	}

	static int color(QuestDefinition quest, String property, int fallback) {
		// Per chart node and edge per frame. A quest's tags only change with a new
		// catalogue, so the id is the key and the cache is dropped with the catalogue.
		long revision = QuestPlusClient.STATE.catalogueRevision;
		if (revision != catalogue) {
			CACHE.clear();
			catalogue = revision;
		}
		var properties = CACHE.get(quest.id());
		if (properties == null)
			CACHE.put(quest.id(), properties = theme.properties(quest.id(), quest.presentation().tags()));
		return QuestThemeRules.color(properties, property, fallback);
	}

	static void ensureShape(String shape) {
		if (!shape.contains(":") || !SHAPES.add(shape))
			return;
		var id = Identifier.parse(shape);
		var texture = Identifier.parse(id.getNamespace() + ":textures/shapes/" + id.getPath() + "/shape.png");
		var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty())
			return;
		try (var stream = resource.get().open(); var input = ImageIO.createImageInputStream(stream)) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext())
				throw new IllegalArgumentException("Invalid shape image");
			var reader = readers.next();
			try {
				reader.setInput(input);
				int width = reader.getWidth(0), height = reader.getHeight(0);
				if (width < 1 || height < 1 || width > 512 || height > 512)
					throw new IllegalArgumentException("Shape must be 1..512 pixels");
				var image = reader.read(0);
				var spans = new ArrayList<QuestShape.Span>();
				for (int y = 0; y < 32; y++) {
					int start = -1;
					for (int x = 0; x <= 32; x++) {
						boolean filled = x < 32 && (image.getRGB(Math.min(width - 1, (int) ((x + .5) * width / 32)),
								Math.min(height - 1, (int) ((y + .5) * height / 32))) >>> 24) >= 128;
						if (filled && start < 0)
							start = x;
						if (!filled && start >= 0) {
							spans.add(new QuestShape.Span(start, x, y));
							start = -1;
						}
					}
				}
				if (spans.isEmpty())
					throw new IllegalArgumentException("Empty quest shape");
				QuestShape.install(shape, spans);
			} finally {
				reader.dispose();
			}
		} catch (Exception error) {
			dev.questplus.fabric.QuestPlusMod.LOGGER.warn("Cannot load quest shape {}: {}", shape, error.getMessage());
		}
	}
}
