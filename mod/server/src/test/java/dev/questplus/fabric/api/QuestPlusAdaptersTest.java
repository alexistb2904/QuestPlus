package dev.questplus.fabric.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPlusAdaptersTest {
    @Test
    void lifecycleIsOptionalDeterministicAndReloadable() throws Exception {
        var calls = new ArrayList<String>();
        try (var first = QuestPlusAdapters.register("test:first", new QuestPlusAdapters.Adapter() {
            public void started() { calls.add("start"); }
            public void definitionsReloaded() { calls.add("reload"); }
            public void stopped() { calls.add("stop"); }
        })) {
            assertThrows(IllegalArgumentException.class,
                    () -> QuestPlusAdapters.register("test:first", new QuestPlusAdapters.Adapter() {}));
            QuestPlusAdapters.definitionsReloaded();
            assertTrue(calls.isEmpty());
            QuestPlusAdapters.serverStarted();
            assertThrows(IllegalStateException.class,
                    () -> QuestPlusAdapters.register("test:late", new QuestPlusAdapters.Adapter() {}));
            QuestPlusAdapters.definitionsReloaded();
            QuestPlusAdapters.serverStopped();
            QuestPlusAdapters.definitionsReloaded();
            assertEquals(List.of("start", "reload", "stop"), calls);
        } finally {
            QuestPlusAdapters.serverStopped();
        }
    }
}
