package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletFirstPersonAnimationTest {
    @Test void strikeMovesTheTabletWithTheHandAndReturnsToCarry() {
        var carry=TabletFirstPersonAnimation.pose("questplus.carry",0);
        assertEquals(carry,TabletFirstPersonAnimation.pose("questplus.carry_attack",0));
        assertEquals(carry,TabletFirstPersonAnimation.pose("questplus.carry_attack",.35));
        var strike=TabletFirstPersonAnimation.pose("questplus.carry_attack",.125);
        assertTrue(Math.abs(strike.tabletPosition().z()-carry.tabletPosition().z())>6);
        assertEquals(strike.rightPosition().z()-carry.rightPosition().z(),
            strike.tabletPosition().z()-carry.tabletPosition().z(),1e-5);
        assertTrue(strike.tabletVisible());
    }
    @Test void carryRetainsTheLastClosingPoseWithoutOpeningTheMenu() {
        var closing=TabletFirstPersonAnimation.pose("questplus.close",.55);
        assertEquals(closing,TabletFirstPersonAnimation.pose("questplus.carry",0));
        assertEquals(closing,TabletFirstPersonAnimation.pose("questplus.carry",12.5));
        assertTrue(closing.tabletVisible());
        assertEquals(closing,TabletFirstPersonAnimation.pose("questplus.carry_open",0));
        assertEquals(closing,TabletFirstPersonAnimation.pose("questplus.carry_close",.55));
        assertEquals(TabletFirstPersonAnimation.pose("questplus.idle",0),TabletFirstPersonAnimation.pose("questplus.carry_open",.55));
    }
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
