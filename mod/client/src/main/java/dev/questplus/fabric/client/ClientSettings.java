package dev.questplus.fabric.client;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import dev.questplus.fabric.QuestPlusMod;
import java.nio.file.*;
import java.util.*;

public final class ClientSettings {
	public static final int MAX_PINNED = 3;
	private static final Gson JSON = new Gson();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("questplus-client.json");
	public boolean hud = false, notifications = true, blur = true, animations = true, reducedMotion, tablet = true;
	public boolean tabletTransitions = true;
	/** Off by default: damage closes the tablet and plays its close animation. */
	public boolean stayOpenOnDamage = false;
	public int hudPosition = 2, notificationPosition = 1, detail = 1;
	/**
	 * First-person screen emits its own light by default; backlight can be dimmed.
	 */
	public boolean screenEmissive = true;
	public double screenBrightness = 1.0;
	public double scale = 1, blurIntensity = 0.4;
	public Set<String> pinned = new LinkedHashSet<>();

	public static ClientSettings load() {
		try {
			if (Files.exists(FILE)) {
				var json = JSON.fromJson(Files.readString(FILE), com.google.gson.JsonObject.class);
				var settings = JSON.fromJson(json, ClientSettings.class);
				// Older installations had no notification anchor: retain the previous
				// top-centre placement.
				if (settings != null && (json == null || !json.has("notificationPosition")))
					settings.notificationPosition = 1;
				if (settings != null && (json == null || !json.has("hud")))
					settings.hud = false;
				// Gson does not initialize newly-added instance fields for older configs.
				if (settings != null && !json.has("tabletTransitions"))
					settings.tabletTransitions = true;
				if (settings != null && !json.has("screenEmissive"))
					settings.screenEmissive = true;
				if (settings != null && !json.has("screenBrightness"))
					settings.screenBrightness = 1.0;
				if (settings == null || settings.pinned == null || settings.pinned.size() > 1024
						|| settings.hudPosition < 0 || settings.hudPosition > 8 || settings.notificationPosition < 0
						|| settings.notificationPosition > 8 || !Double.isFinite(settings.scale) || settings.scale < 0.7
						|| settings.scale > 1.3 || !Double.isFinite(settings.blurIntensity)
						|| settings.blurIntensity < 0 || settings.blurIntensity > 1
						|| !Double.isFinite(settings.screenBrightness) || settings.screenBrightness < 0
						|| settings.screenBrightness > 1)
					throw new IllegalArgumentException("Invalid client settings");
				// Older versions allowed more than three pinned quests. Keep their first three.
				if (settings.pinned.size() > MAX_PINNED) {
					var trimmed = new LinkedHashSet<String>();
					for (String id : settings.pinned) {
						if (trimmed.size() == MAX_PINNED)
							break;
						trimmed.add(id);
					}
					settings.pinned = trimmed;
					settings.save();
				}
				return settings;
			}
		} catch (Exception e) {
			QuestPlusMod.LOGGER.warn("Cannot read Quest Plus client settings; original file retained", e);
		}
		return new ClientSettings();
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
			Files.writeString(temporary, JSON.toJson(this));
			Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			QuestPlusMod.LOGGER.error("Cannot save Quest Plus client settings", e);
		}
	}
}
