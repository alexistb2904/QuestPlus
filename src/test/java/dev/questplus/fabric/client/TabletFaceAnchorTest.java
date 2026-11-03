package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
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
        assertNull(TabletFaceAnchor.pointer(640,360));
    }
    @Test void unprojectsMouseOntoFaceAndRejectsOutsideAndParallelRays() {
        var identity=new Matrix4f();
        var center=TabletFaceAnchor.intersect(identity,0,0,-1,-1,.5f,2,2);
        assertNotNull(center);
        assertEquals(250,center.x(),.001);
        assertEquals(150,center.y(),.001);
        var corner=TabletFaceAnchor.intersect(identity,1,1,-1,-1,.5f,2,2);
        assertEquals(0,corner.x(),.001);
        assertEquals(0,corner.y(),.001);
        assertNull(TabletFaceAnchor.intersect(identity,1.1f,0,-1,-1,.5f,2,2));
        assertNull(TabletFaceAnchor.intersect(identity,0,0,-1,-1,2,2,2));
        assertNull(TabletFaceAnchor.intersect(new Matrix4f().rotateY((float)Math.PI/2),0,0,-1,-1,.5f,2,2));
    }
    @Test void inversePerspectivePreservesUvUnderRotationAndScale() {
        var matrix=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,100)
            .translate(.15f,-.1f,-3).rotateY(.2f).rotateZ(-.15f).scale(.8f);
        var point=new org.joml.Vector4f(.4f,-.6f,0,1).mul(matrix);
        var actual=TabletFaceAnchor.intersect(new Matrix4f(matrix).invert(),point.x/point.w,point.y/point.w,-1,-1,0,2,2);
        assertNotNull(actual);
        assertEquals(150,actual.x(),.01);
        assertEquals(240,actual.y(),.01);
    }
}
