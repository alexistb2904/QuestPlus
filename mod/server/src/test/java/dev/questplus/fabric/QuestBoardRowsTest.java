package dev.questplus.fabric;

import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The board's "in progress" column must agree with the tablet's "active" filter. */
class QuestBoardRowsTest {
    @Test void inProgressMatchesTabletActiveRule() {
        var v=new PlayerProgress.QuestProgress();
        assertFalse(v.inProgress());
        v.counters.put("o",0L);
        assertFalse(v.inProgress());
        // Progress without an attempt id (e.g. imported or team-merged state) still counts.
        v.counters.put("o",3L);
        assertTrue(v.inProgress());
        v.counters.clear();v.attemptId="a";
        assertTrue(v.inProgress());
        v.completed=true;
        assertFalse(v.inProgress());
        v.completed=false;v.waitingRestart=true;
        assertFalse(v.inProgress());
    }
}
