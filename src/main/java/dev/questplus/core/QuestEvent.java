package dev.questplus.core;
import java.util.*;
public record QuestEvent(String type, String target, Set<String> tags, long amount, Map<String,Object> context, UUID eventId) {
    public QuestEvent(String type, String target, Set<String> tags, long amount, Map<String,Object> context) { this(type,target,tags,amount,context,UUID.randomUUID()); }
    public QuestEvent {
        var eventType = QuestExtensions.event(type);
        if (target == null || !target.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || amount < 0 || (amount == 0 && !eventType.state()) || tags == null || context == null || eventId == null) throw new IllegalArgumentException("Invalid quest event");
        tags = Set.copyOf(tags); context = Map.copyOf(context);
        if (tags.stream().anyMatch(t -> !t.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))) throw new IllegalArgumentException("Invalid event tag");
    }
}
