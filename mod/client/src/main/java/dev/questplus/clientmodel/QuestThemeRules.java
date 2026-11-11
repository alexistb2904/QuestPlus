package dev.questplus.clientmodel;

import java.util.*;

/**
 * Data-only FTB theme sections: global defaults, quest IDs, tags and bounded
 * property references.
 */
public final class QuestThemeRules {
	private record Rule(String selector, Map<String, String> values) {
	}

	private final List<Rule> rules;

	private QuestThemeRules(List<Rule> rules) {
		this.rules = List.copyOf(rules);
	}

	public static QuestThemeRules parse(String source) {
		if (source.length() > 65536)
			throw new IllegalArgumentException("Theme exceeds 64 KiB");
		var rules = new ArrayList<Rule>();
		Map<String, String> values = new LinkedHashMap<>();
		rules.add(new Rule("*", values));
		for (String raw : source.split("\\R")) {
			String line = raw.trim();
			if (line.isEmpty() || line.startsWith("//"))
				continue;
			if (line.startsWith("[") && line.endsWith("]")) {
				if (rules.size() >= 128)
					throw new IllegalArgumentException("Too many theme sections");
				String selector = line.substring(1, line.length() - 1).trim();
				if (selector.isBlank() || selector.length() > 256)
					throw new IllegalArgumentException("Invalid theme selector");
				values = new LinkedHashMap<>();
				rules.add(new Rule(selector, values));
				continue;
			}
			int colon = line.indexOf(':');
			if (colon <= 0 || values.size() >= 128)
				throw new IllegalArgumentException("Invalid theme property");
			String key = line.substring(0, colon).trim();
			if (!key.matches("[a-z_]{1,80}"))
				throw new IllegalArgumentException("Invalid theme key");
			values.put(key, line.substring(colon + 1).trim());
		}
		return new QuestThemeRules(rules.stream().map(r -> new Rule(r.selector(), Map.copyOf(r.values()))).toList());
	}

	public Map<String, String> properties(String quest, List<String> tags) {
		var result = new LinkedHashMap<String, String>();
		for (var rule : rules)
			if (rule.selector().equals("*"))
				result.putAll(rule.values());
		for (var rule : rules) {
			String selector = rule.selector();
			if (selector.equals("*"))
				continue;
			if (selector.startsWith("#") ? (tags.contains(selector.substring(1)) || tags.contains(selector))
					: selector.equals(quest)
							|| quest.toLowerCase(Locale.ROOT).endsWith("/" + selector.toLowerCase(Locale.ROOT)))
				result.putAll(rule.values());
		}
		return Map.copyOf(result);
	}

	// Looked up for every text draw: compiled once, and nothing allocated on the common paths.
	private static final java.util.regex.Pattern REFERENCE = java.util.regex.Pattern.compile("\\{\\{\\s*[a-z_]+\\s*}}"),
			RGB = java.util.regex.Pattern.compile("#[a-fA-F0-9]{6}"),
			ARGB = java.util.regex.Pattern.compile("#[a-fA-F0-9]{8}");

	public static int color(Map<String, String> properties, String key, int fallback) {
		String value = properties.get(key);
		if (value == null)
			return fallback;
		Set<String> seen = null;
		while (value != null && REFERENCE.matcher(value).matches()) {
			if (seen == null)
				seen = new HashSet<>();
			if (!seen.add(value) || seen.size() > 16)
				return fallback;
			value = properties.get(value.substring(2, value.length() - 2).trim());
		}
		if (value == null)
			return fallback;
		if (RGB.matcher(value).matches())
			return 0xff000000 | (int) Long.parseLong(value.substring(1), 16);
		if (ARGB.matcher(value).matches())
			return (int) Long.parseLong(value.substring(1), 16);
		return switch (value.toLowerCase(Locale.ROOT)) {
		case "transparent" -> 0;
		case "black" -> 0xff000000;
		case "dark_gray" -> 0xff555555;
		case "gray" -> 0xffaaaaaa;
		case "white" -> 0xffffffff;
		case "red" -> 0xffaa0000;
		case "green" -> 0xff00aa00;
		case "blue" -> 0xff0000aa;
		case "light_red" -> 0xffff5555;
		case "light_green" -> 0xff55ff55;
		case "light_blue" -> 0xff5555ff;
		default -> fallback;
		};
	}
}
