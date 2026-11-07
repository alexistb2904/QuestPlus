package dev.questplus.fabric;

import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Messages {
	private static final Map<String, Map<String, String>> LANGUAGES = new HashMap<>();

	private Messages() {
	}

	static Component text(ServerPlayer player, String key, Object... args) {
		String locale = player == null ? "en_us" : player.clientInformation().language();
		String fallback = language(locale).getOrDefault(key, language("en_us").getOrDefault(key, key));
		return Component.translatableWithFallback(key, fallback, args);
	}

	private static Map<String, String> language(String locale) {
		if (!locale.matches("[a-z0-9_]{2,16}"))
			return Map.of();
		var cached = LANGUAGES.get(locale);
		if (cached != null)
			return cached;
		try (var input = Messages.class.getResourceAsStream("/assets/questplus/lang/" + locale + ".json")) {
			if (input == null)
				return Map.of();
			try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				Map<String, String> values = new HashMap<>();
				JsonParser.parseReader(reader).getAsJsonObject().entrySet()
						.forEach(e -> values.put(e.getKey(), e.getValue().getAsString()));
				var result = Map.copyOf(values);
				LANGUAGES.put(locale, result);
				return result;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
