package dev.questplus.core;

import java.util.Objects;

/**
 * Counts continuous observation of one actual target, never repeated or missing
 * ticks.
 */
public final class ObservationCounter {
	private String target;
	private long tick = Long.MIN_VALUE, count;

	public long update(String next, long now) {
		count = next == null ? 0
				: Objects.equals(target, next) && now == tick + 1 ? Math.min(1_000_000_000L, count + 1) : 1;
		target = next;
		tick = now;
		return count;
	}
}
