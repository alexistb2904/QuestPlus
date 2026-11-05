package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonPalBridgeTest {
    @Test void palHeldFrameFinishesEvenWhenItsRenderClockRoundsBelowTheEnd() {
        assertFalse(FirstPersonPalBridge.terminalPose(12,20,false));
        assertFalse(FirstPersonPalBridge.terminalPose(Math.nextDown(20f),20,false));
        assertTrue(FirstPersonPalBridge.terminalPose(Math.nextDown(20f),20,true));
        assertTrue(FirstPersonPalBridge.terminalPose(20,20,false));
    }
}
