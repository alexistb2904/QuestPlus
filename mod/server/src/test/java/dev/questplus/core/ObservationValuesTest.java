package dev.questplus.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObservationValuesTest {
    @Test void sparseSnapshotRetainsEveryAuthoredZeroAndCannotChangeAfterPublication() {
        var authored = new HashSet<>(Set.of("minecraft:stone", "minecraft:dirt"));
        var positive = new HashMap<>(Map.of("minecraft:stone", 12L));
        var values = new ObservationValues(authored, positive);
        authored.clear();positive.clear();
        assertEquals(12L, values.get("minecraft:stone"));
        assertEquals(0L, values.get("minecraft:dirt"));
        assertTrue(values.containsKey("minecraft:dirt"));
        assertNull(values.get("minecraft:air"));
        assertEquals(Map.of("minecraft:stone", 12L, "minecraft:dirt", 0L), Map.copyOf(values));
        assertThrows(UnsupportedOperationException.class, () -> values.put("minecraft:dirt", 2L));
        assertThrows(UnsupportedOperationException.class, () -> values.entrySet().iterator().next().setValue(2L));
    }
}
