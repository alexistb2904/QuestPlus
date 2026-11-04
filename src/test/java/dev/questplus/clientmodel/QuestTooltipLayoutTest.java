package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class QuestTooltipLayoutTest {
    @Test void tooltipNormallyOpensRightAndBelowCursor() {
        var point=QuestTooltipLayout.place(100,80,120,30,4,4,496,296);
        assertEquals(112,point.x());
        assertEquals(92,point.y());
    }
    @Test void rightEdgeFlipsToLeftInsideTabletAtlas() {
        var point=QuestTooltipLayout.place(491,125,168,38,4,4,496,296);
        assertEquals(311,point.x());
        assertEquals(137,point.y());
        assertTrue(point.x()+168<=496);
    }
    @Test void bottomEdgeFlipsUpAndCornerNeverClips() {
        var point=QuestTooltipLayout.place(494,294,190,64,4,4,496,296);
        assertEquals(292,point.x());
        assertEquals(222,point.y());
        assertTrue(point.x()>=4&&point.x()+190<=496);
        assertTrue(point.y()>=4&&point.y()+64<=296);
    }
    @Test void longTooltipClampsToVisibleAtlasAndSupports2dViewport() {
        var point=QuestTooltipLayout.place(7,7,480,270,4,4,496,296);
        assertEquals(4,point.x());
        assertEquals(19,point.y());
        assertTrue(point.y()+270<=296);
    }
    @Test void rejectsInvalidViewport() {
        assertThrows(IllegalArgumentException.class,()->QuestTooltipLayout.place(0,0,20,20,10,4,10,20));
    }
}
