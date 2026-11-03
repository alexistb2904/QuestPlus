package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletAnimationControllerTest {
    @Test void interruptedTransitionsAreContinuousAndFinish() {
        var pose = new TabletAnimationController();
        pose.set(true,1_000_000_000L,true);
        assertEquals(0,pose.value(1_000_000_000L,true));
        float halfway=pose.value(1_125_000_000L,true);
        assertEquals(.5f,halfway,1e-6);
        pose.set(false,1_125_000_000L,true);
        assertEquals(halfway,pose.value(1_125_000_000L,true),1e-6);
        pose.set(true,1_200_000_000L,true);
        assertEquals(1,pose.value(1_450_000_000L,true));
        pose.set(false,1_450_000_000L,true);
        assertTrue(pose.finishedClosed(1_700_000_000L,true));
    }
    @Test void reducedMotionSwitchesImmediately() {
        var pose=new TabletAnimationController();
        pose.set(true,0,false); assertEquals(1,pose.value(0,false));
        pose.set(false,1,false); assertTrue(pose.finishedClosed(1,false));
    }
}
