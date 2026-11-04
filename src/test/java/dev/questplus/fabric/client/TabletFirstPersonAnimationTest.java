package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletFirstPersonAnimationTest {
    @Test void usesRealBlockbenchTimingAndConstantPhysicalSize() {
        assertEquals(1_000_000_000L,TabletFirstPersonAnimation.OPEN_NANOS);
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",0).tabletVisible());
        float initial=TabletFirstPersonAnimation.pose("questplus.open",0).rightPosition().y();
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",.2).rightPosition().y()<initial-10);
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",.2167).tabletVisible());
        assertTrue(TabletFirstPersonAnimation.pose("questplus.open",1.0).tabletVisible());
    }
    @Test void holdingAndClosingMatchAtTransition() {
        var first=TabletFirstPersonAnimation.pose("questplus.idle",0);
        var last=TabletFirstPersonAnimation.pose("questplus.open",1.0);
        var closing=TabletFirstPersonAnimation.pose("questplus.close",0);
        assertEquals(last.tabletPosition(),first.tabletPosition());
        assertEquals(last.tabletRotation(),first.tabletRotation());
        assertEquals(first.tabletPosition(),closing.tabletPosition());
        assertTrue(TabletFirstPersonAnimation.pose("questplus.close",1.0).tabletVisible());
    }
}
