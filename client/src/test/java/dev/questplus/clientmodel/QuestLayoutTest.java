package dev.questplus.clientmodel;

import dev.questplus.core.QuestDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestLayoutTest {
    private static QuestDefinition q(String id, QuestDefinition.Layout layout, String... refs) {
        var requirements = Arrays.stream(refs).map(ref -> new QuestDefinition.Condition("quest", ref, List.of())).toList();
        var condition = refs.length == 0 ? null : new QuestDefinition.Condition("and", null, requirements);
        return new QuestDefinition(id,"test:category","minecraft:stone",Map.of("en_us",id),Map.of("en_us",id),List.of(),condition,
            List.of(),"once",layout,Map.of(),"fingerprint","all",List.of(),"stop",0,0,false,"standard","manual");
    }
    @Test void ranksBranchesAndConvergenceDeterministically() {
        var input = List.of(q("a",null), q("b",null,"a"), q("c",null,"a"), q("d",null,"b","c"), q("independent",null));
        var layout = QuestLayout.compute(input);
        var reversed = new ArrayList<>(input); Collections.reverse(reversed);
        assertEquals(layout.points(), QuestLayout.compute(reversed).points());
        assertEquals(Set.of(new QuestLayout.Edge("a","b"),new QuestLayout.Edge("a","c"),new QuestLayout.Edge("b","d"),new QuestLayout.Edge("c","d")),Set.copyOf(layout.edges()));
        assertTrue(layout.points().get("a").y() < layout.points().get("b").y());
        assertTrue(layout.points().get("c").y() < layout.points().get("d").y());
        assertEquals(0, layout.points().get("independent").y());
        assertNotEquals(layout.points().get("b"),layout.points().get("c"));
    }
    @Test void manualCoordinatesWinAndExternalReferencesDoNotCreateEdges() {
        var layout = QuestLayout.compute(List.of(q("a",new QuestDefinition.Layout(0,0)),q("b",null,"outside")));
        assertEquals(new QuestLayout.Point(0,0),layout.points().get("a"));
        assertTrue(layout.edges().isEmpty());
        var a = layout.points().get("a"); var b = layout.points().get("b");
        assertTrue(Math.abs(a.x()-b.x()) >= QuestLayout.NODE_SIZE || Math.abs(a.y()-b.y()) >= QuestLayout.NODE_SIZE);
    }
    @Test void cycleAndDuplicateRejected() {
        assertThrows(IllegalArgumentException.class,() -> QuestLayout.compute(List.of(q("a",null,"b"),q("b",null,"a"))));
        assertThrows(IllegalArgumentException.class,() -> QuestLayout.compute(List.of(q("a",null),q("a",null))));
    }
    @Test void largeGraphVisibleAndHitUseWorldCoordinates() {
        var quests = new ArrayList<QuestDefinition>();
        for (int i = 0; i < 5000; i++) quests.add(q("q"+i,null));
        var layout = QuestLayout.compute(quests);
        assertEquals(5000,layout.points().size());
        var point = layout.points().get("q2500");
        assertEquals(Optional.of("q2500"),layout.hit(point.x(),point.y()));
        assertEquals(List.of("q2500"),layout.visible(point.x()-1,point.y()-1,point.x()+1,point.y()+1));
        assertEquals(5000,layout.visible(-1e9,-1e9,1e9,1e9).size());
    }
    @Test void zoomKeepsCursorWorldPositionAndFitCentres() {
        var camera = new QuestCamera(.25, 3);
        camera.pan(50,80);
        double wx = camera.worldX(120), wy = camera.worldY(250);
        camera.zoomAt(4,120,250);
        assertEquals(3,camera.zoom());
        assertEquals(wx,camera.worldX(120),1e-9); assertEquals(wy,camera.worldY(250),1e-9);
        camera.zoomAt(.0001,120,250); assertEquals(.25,camera.zoom());
        var layout = QuestLayout.compute(List.of(q("a",new QuestDefinition.Layout(-100,0)),q("b",new QuestDefinition.Layout(100,0))));
        camera.fit(layout,10,20,400,300,20);
        assertEquals(210,camera.screenX(0),1e-9); assertEquals(170,camera.screenY(0),1e-9);
        assertTrue(camera.screenX(-116) >= 30); assertTrue(camera.screenX(116) <= 390);
    }
    @Test void extremeManualPositionsKeepCameraFinite() {
        var camera = new QuestCamera(.25, 3);
        var layout = QuestLayout.compute(List.of(q("a",new QuestDefinition.Layout(-1e308,-1e308)),q("b",new QuestDefinition.Layout(1e308,1e308))));
        camera.zoomAt(.5,200,150);
        double originalZoom=camera.zoom();
        camera.fit(layout,0,0,400,300,10);
        assertTrue(Double.isFinite(camera.screenX(0))); assertTrue(Double.isFinite(camera.screenY(0)));
        assertEquals(originalZoom,camera.zoom());
    }
}
