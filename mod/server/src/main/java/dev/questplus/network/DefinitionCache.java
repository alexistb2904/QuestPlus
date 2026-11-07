package dev.questplus.network;

import java.util.*;
import java.util.function.Supplier;

/**
 * Only sanitized projections are cached; visibility and redaction are part of
 * the key.
 */
public final class DefinitionCache {
	private record Key(Set<String> visible, Map<String, Integer> redactions) {
	}

	private final LinkedHashMap<Key, String> entries = new LinkedHashMap<>(8, .75f, true);
	private long generation = -1;
	private int chars;

	public void clear() {
		entries.clear();
		chars = 0;
		generation = -1;
	}

	public String get(long generation, Set<String> visible, Map<String, Integer> redactions, Supplier<String> create) {
		if (this.generation != generation) {
			clear();
			this.generation = generation;
		}
		var key = new Key(Set.copyOf(visible), Map.copyOf(redactions));
		var existing = entries.get(key);
		if (existing != null)
			return existing;
		String value = create.get();
		if (value.length() <= 8 * 1024 * 1024) {
			while (!entries.isEmpty() && (entries.size() >= 8 || chars + value.length() > 8 * 1024 * 1024)) {
				var iterator = entries.entrySet().iterator();
				chars -= iterator.next().getValue().length();
				iterator.remove();
			}
			entries.put(key, value);
			chars += value.length();
		}
		return value;
	}
}
