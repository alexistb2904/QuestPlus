package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletFaceAnchorTest {
    @Test void readsActualFirstPersonScreenFaceInsteadOfStandaloneAttachment() {
        var screen=TabletFaceAnchor.sourceScreen();
        assertEquals(13f,screen.width(),.0001f);
        assertEquals(7.8f,screen.height(),.0001f);
        assertEquals(13f/7.8f,screen.width()/screen.height(),.001f);
        assertEquals(18.8f,screen.pivotY(),.001f);
    }
    @Test void staleProjectionFailsClosed() {
        TabletFaceAnchor.reset();
        assertNull(TabletFaceAnchor.current(1280,720));
    }
}
