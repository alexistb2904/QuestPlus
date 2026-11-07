package dev.questplus.fabric.api;

import dev.questplus.fabric.QuestPlusMod;
import java.util.*;

/**
 * Optional, server-side integration lifecycle. Adapters register during mod
 * initialization; QuestPlus never loads or links an optional scripting engine.
 */
public final class QuestPlusAdapters {
    public interface Adapter {
        default void started() {}
        default void definitionsReloaded() {}
        default void stopped() {}
    }

    private static final Map<String, Adapter> REGISTERED = new LinkedHashMap<>();
    private static boolean running;

    private QuestPlusAdapters() {}

    public static synchronized AutoCloseable register(String id, Adapter adapter) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || adapter == null)
            throw new IllegalArgumentException("Invalid adapter");
        if (running)
            throw new IllegalStateException("Adapters must register before the server starts");
        if (REGISTERED.putIfAbsent(id, adapter) != null)
            throw new IllegalArgumentException("Duplicate adapter: " + id);
        return () -> unregister(id, adapter);
    }

    private static synchronized void unregister(String id, Adapter adapter) {
        if (running)
            throw new IllegalStateException("Cannot unregister an active adapter");
        REGISTERED.remove(id, adapter);
    }

    public static synchronized void serverStarted() {
        if (running)
            throw new IllegalStateException("Adapters already started");
        running = true;
        REGISTERED.forEach((id, adapter) -> invoke(id, "start", adapter::started));
    }

    public static synchronized void definitionsReloaded() {
        if (running)
            REGISTERED.forEach((id, adapter) -> invoke(id, "reload", adapter::definitionsReloaded));
    }

    public static synchronized void serverStopped() {
        if (!running)
            return;
        running = false;
        var entries = new ArrayList<>(REGISTERED.entrySet());
        Collections.reverse(entries);
        entries.forEach(entry -> invoke(entry.getKey(), "stop", entry.getValue()::stopped));
    }

    private static void invoke(String id, String action, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException failure) {
            QuestPlusMod.LOGGER.error("QuestPlus adapter {} failed during {}", id, action, failure);
        }
    }
}
