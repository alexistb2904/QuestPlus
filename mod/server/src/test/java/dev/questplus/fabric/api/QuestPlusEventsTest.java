package dev.questplus.fabric.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestPlusEventsTest {
    @Test
    void subscriptionsAreOptionalCloseableAndPreventRecursiveDispatch() throws Exception {
        var recorded = new ArrayList<String>();
        assertFalse(QuestPlusEvents.hasListeners(QuestPlusEvents.Type.OBJECTIVE_COMPLETED));
        try (var one = QuestPlusEvents.subscribe(QuestPlusEvents.Type.OBJECTIVE_COMPLETED, change -> {
            recorded.add(change.objectiveId());
            QuestPlusEvents.publish(QuestPlusEvents.Type.OBJECTIVE_COMPLETED, change.playerId(),
                    change.questId(), "nested", 0, 1);
        })) {
            assertTrue(QuestPlusEvents.hasListeners(QuestPlusEvents.Type.OBJECTIVE_COMPLETED));
            QuestPlusEvents.publish(QuestPlusEvents.Type.OBJECTIVE_COMPLETED, UUID.randomUUID(),
                    "test:quest", "first", 0, 1);
            assertEquals(List.of("first"), recorded);
        }
        QuestPlusEvents.publish(QuestPlusEvents.Type.OBJECTIVE_COMPLETED, UUID.randomUUID(),
                "test:quest", "second", 0, 1);
        assertEquals(List.of("first"), recorded);
        assertFalse(QuestPlusEvents.hasListeners(QuestPlusEvents.Type.OBJECTIVE_COMPLETED));
    }
}
