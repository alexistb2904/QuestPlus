package dev.questplus.core;

import java.util.*;

public record QuestEvent(String type, String target, Set<String> tags, long amount, Map<String, Object> context,
		UUID eventId) {
	public QuestEvent(String type, String target, Set<String> tags, long amount, Map<String, Object> context) {
		// Only has to be unique among a player's last 256 events: skip SecureRandom on the event path.
		this(type, target, tags, amount, context, new UUID(java.util.concurrent.ThreadLocalRandom.current().nextLong(),
				java.util.concurrent.ThreadLocalRandom.current().nextLong()));
	}

	public QuestEvent {
		var eventType = QuestExtensions.event(type);
		if (!QuestExtensions.isId(target) || amount < 0
				|| (amount == 0 && !eventType.state()) || tags == null || context == null || eventId == null)
			throw new IllegalArgumentException("Invalid quest event");
		tags = Set.copyOf(tags);
		context = Map.copyOf(context);
		for (String tag : tags)
			if (!QuestExtensions.isId(tag))
				throw new IllegalArgumentException("Invalid event tag");
	}
}
