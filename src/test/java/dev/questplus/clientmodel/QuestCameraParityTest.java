package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

final class QuestCameraParityTest {
    @Test void anchorCoordinatesRemainInvertibleWithPointerAnchoredZoom() {
        QuestCamera camera=new QuestCamera(.25,2.5);
        camera.fitBounds(List.of(new QuestLayout.Point(-60,40),new QuestLayout.Point(140,100)),
            0,0,680,390,85,50);
        double beforeX=camera.screenX(140),beforeY=camera.screenY(100);
        assertEquals(140,camera.worldX(beforeX),.00001);
        assertEquals(100,camera.worldY(beforeY),.00001);
        double original=camera.screenX(140)-camera.screenX(-60);
        camera.zoomAt(1.5,beforeX,beforeY);
        assertEquals(beforeX,camera.screenX(140),.00001);
        assertEquals(beforeY,camera.screenY(100),.00001);
        assertEquals(original*1.5,camera.screenX(140)-camera.screenX(-60),.00001);
        camera.pan(-30,45);
        assertEquals(beforeX-30,camera.screenX(140),.00001);
        assertEquals(beforeY+45,camera.screenY(100),.00001);
        assertEquals(1.5,camera.zoom());
    }
    @Test void fittingAnnotationsUsesTheirWorldCentersAndFixedScreenPadding() {
        QuestCamera camera=new QuestCamera(.25,2.5);
        var quest=new QuestLayout.Point(0,0);
        var annotation=new QuestLayout.Point(180,65);
        camera.fitBounds(List.of(quest,annotation),40,30,500,320,100,80);
        assertEquals(40+500/2.0,(camera.screenX(0)+camera.screenX(180))/2,.00001);
        assertEquals(30+320/2.0,(camera.screenY(0)+camera.screenY(65))/2,.00001);
        assertEquals(1,camera.zoom());
    }
}
