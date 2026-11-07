package dev.questplus.core;

import java.util.*;

public final class Localization {
	private Localization() {
	}

	/**
	 * Prefer the player's language, then English, then any non-empty translation.
	 */
	public static String text(Map<String, String> values, String locale, Map<String, String> placeholders) {
		if (values == null || values.isEmpty())
			return "";
		String result = nonBlank(values.get(locale));
		if (result == null)
			result = nonBlank(values.get("en_us"));
		if (result == null)
			result = values.entrySet().stream().filter(e -> nonBlank(e.getValue()) != null)
					.sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).findFirst().orElse("");
		if (placeholders != null)
			for (var entry : placeholders.entrySet())
				result = result.replace("{" + entry.getKey() + "}", entry.getValue());
		return result;
	}

	private static String nonBlank(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
