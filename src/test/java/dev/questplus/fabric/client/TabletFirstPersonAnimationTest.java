package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletFirstPersonAnimationTest {
    @Test void usesRealBlockbenchTimingAndPocketReveal() {
        assertEquals(1_200_000_000L,TabletFirstPersonAnimation.OPEN_NANOS);
        assertFalse(TabletFirstPersonAnimation.pose("questplus.open",0).tabletVisible());
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",.26).tabletVisible());
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",1.2).tabletVisible());
    }
    @Test void holdingAndClosingMatchAtTransition() {
        var first=TabletFirstPersonAnimation.pose("questplus.idle",0);
        var last=TabletFirstPersonAnimation.pose("questplus.open",1.2);
        var closing=TabletFirstPersonAnimation.pose("questplus.close",0);
        assertEquals(last.tabletPosition(),first.tabletPosition());
        assertEquals(last.tabletRotation(),first.tabletRotation());
        assertEquals(first.tabletPosition(),closing.tabletPosition());
        assertFalse(TabletFirstPersonAnimation.pose("questplus.close",1.2).tabletVisible());
    }
}
