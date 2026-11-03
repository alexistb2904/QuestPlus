package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletFirstPersonAnimationTest {
    @Test void authoredOpeningHoldsTabletInPocketBeforeRevealingIt() {
        assertEquals(1_200_000_000L,TabletFirstPersonAnimation.OPEN_NANOS);
        var start=TabletFirstPersonAnimation.pose("questplus.open",0);
        assertFalse(start.tabletVisible());
        assertFalse(TabletFirstPersonAnimation.pose("questplus.open",.24).tabletVisible());
        var pickup=TabletFirstPersonAnimation.pose("questplus.open",.26);
        assertTrue(pickup.tabletVisible());
        assertTrue(Math.abs(pickup.tabletPosition().x())>1);
        var end=TabletFirstPersonAnimation.pose("questplus.open",1.2);
        assertTrue(end.tabletVisible());
        assertEquals(0,end.tabletPosition().x(),.0001f);
        assertEquals(0,end.tabletPosition().y(),.0001f);
    }

    @Test void closingMatchesIdleAndHidesTabletAtEnd() {
        var open=TabletFirstPersonAnimation.pose("questplus.open",1.2);
        var idle=TabletFirstPersonAnimation.pose("questplus.idle",0);
        var close=TabletFirstPersonAnimation.pose("questplus.close",0);
        assertEquals(open.tabletPosition(),idle.tabletPosition());
        assertEquals(idle.tabletPosition(),close.tabletPosition());
        assertEquals(open.rightRotation(),idle.rightRotation());
        assertEquals(idle.rightRotation(),close.rightRotation());
        assertFalse(TabletFirstPersonAnimation.pose("questplus.close",1.2).tabletVisible());
    }
}
