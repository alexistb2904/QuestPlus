package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestShapeTest {
    @Test void authoredShapesKeepCenterAndRejectOutsideBounds() {
        for(String shape:java.util.List.of("square","circle","rounded_square","diamond","pentagon","hexagon","octagon","heart","gear")) {
            assertTrue(QuestShape.contains(shape,0,0),shape);
            assertFalse(QuestShape.contains(shape,1.1,0),shape);
            assertFalse(QuestShape.spans(shape).isEmpty());
            assertTrue(QuestShape.spans(shape).stream().allMatch(span->span.start()>=0&&span.start()<span.end()&&span.end()<=32));
            assertSame(QuestShape.spans(shape),QuestShape.spans(shape));
        }
        assertTrue(QuestShape.contains("square",.9,.9));
        assertFalse(QuestShape.contains("circle",.9,.9));
        assertFalse(QuestShape.contains("diamond",.6,.6));
    }
}
