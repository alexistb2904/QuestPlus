package dev.questplus.fabric.api;

import dev.questplus.fabric.QuestPlusMod;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Server-thread lifecycle notifications for trusted integrations. Listeners are
 * optional; the hot path does no work without subscribers.
 */
public final class QuestPlusEvents {
    public enum Type {
        OBJECTIVE_PROGRESS, OBJECTIVE_COMPLETED, QUEST_COMPLETED, QUEST_RESET, REWARD_CLAIMED, TEAM_CHANGED
    }

    public record Change(Type type, UUID playerId, String questId, String objectiveId,
                         long previous, long current) {}

    private static final EnumMap<Type, CopyOnWriteArrayList<Consumer<Change>>> LISTENERS =
            new EnumMap<>(Type.class);
    private static final ThreadLocal<Boolean> DISPATCHING = ThreadLocal.withInitial(() -> false);

    static {
        for (Type type : Type.values())
            LISTENERS.put(type, new CopyOnWriteArrayList<>());
    }

    private QuestPlusEvents() {}

    /** Close the returned handle when an adapter or scripting runtime unloads. */
    public static AutoCloseable subscribe(Type type, Consumer<Change> listener) {
        Objects.requireNonNull(listener);
        var subscribers = LISTENERS.get(Objects.requireNonNull(type));
        subscribers.addIfAbsent(listener);
        return () -> subscribers.remove(listener);
    }

    public static boolean hasListeners(Type type) {
        return !LISTENERS.get(type).isEmpty();
    }

    public static boolean hasProgressListeners() {
        return hasListeners(Type.OBJECTIVE_PROGRESS) || hasListeners(Type.OBJECTIVE_COMPLETED);
    }

    public static void publish(Type type, UUID actor, String quest, String objective, long previous, long current) {
        var subscribers = LISTENERS.get(Objects.requireNonNull(type));
        if (subscribers.isEmpty() || DISPATCHING.get())
            return;
        var event = new Change(type, actor, quest, objective, previous, current);
        DISPATCHING.set(true);
        try {
            for (var subscriber : subscribers)
                try {
                    subscriber.accept(event);
                } catch (RuntimeException failure) {
                    QuestPlusMod.LOGGER.error("QuestPlus extension event {} failed for {}", type, quest, failure);
                }
        } finally {
            DISPATCHING.remove();
        }
    }
}
