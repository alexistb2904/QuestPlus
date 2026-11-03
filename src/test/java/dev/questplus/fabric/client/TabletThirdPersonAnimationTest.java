package dev.questplus.fabric.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletThirdPersonAnimationTest {
    @Test void samplesTheBlockbenchPoseAndReversesWithoutJumping() {
        var initial=TabletThirdPersonAnimation.sample(false,0);
        var finish=TabletThirdPersonAnimation.sample(false,TabletThirdPersonAnimation.OPEN_SECONDS);
        var idle=TabletThirdPersonAnimation.sample(true,0);
        assertEquals(0,initial.scale().x());
        assertEquals(-53,finish.right().x());
        var part=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        TabletPoseClient.arm(part,finish.right(),finish.rightPosition(),1);
        assertEquals(Math.toRadians(-53),part.xRot,1e-6);
        var translated=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        TabletPoseClient.arm(translated,finish.right(),new TabletThirdPersonAnimation.Vector(-2,3,4),1);
        assertEquals(-2,translated.x); assertEquals(-3,translated.y); assertEquals(4,translated.z);
        translated.xRot=1;
        TabletPoseClient.arm(translated,finish.right(),finish.rightPosition(),0);
        assertEquals(1,translated.xRot);
        assertEquals(finish.right(),idle.right());
        assertEquals(finish.left(),idle.left());
        assertEquals(finish.rotation(),idle.rotation());
        assertEquals(finish.position(),idle.position());
        var clock=new TabletPoseClient.PoseTime();
        clock.set(true,1_000_000_000L,true);
        double before=clock.progress(1_600_000_000L,true);
        clock.set(false,1_600_000_000L,true);
        assertEquals(before,clock.progress(1_600_000_000L,true));
        assertTrue(clock.finishedClosed(2_200_000_000L,true));
    }

    @Test void idleBreathingStaysContinuousAtClose() {
        var clock=new TabletPoseClient.PoseTime();
        clock.set(true,1_000_000_000L,true);
        long closeAt=4_200_000_000L;
        var before=clock.sample(closeAt,true);
        assertTrue(before.pose().position().y()>0);
        clock.set(false,closeAt,true);
        var after=clock.sample(closeAt,true);
        assertEquals(before.pose().position().y(),after.pose().position().y());
        assertEquals(before.pose().rightPosition().y(),after.pose().rightPosition().y());
        assertEquals(before.pose().right().x(),after.pose().right().x());
        assertEquals(before.weight(),after.weight());
        assertTrue(clock.sample(closeAt+1_100_000_000L,true).weight()<1);
        assertNull(clock.sample(closeAt+1_200_000_000L,true));
    }

    @Test void closeIsExactlyTheReverseOfOpenInTheExport() throws Exception {
        try (var in=getClass().getResourceAsStream("/assets/questplus/animations/third_person.json")) {
            assertNotNull(in);
            var animations=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            var open=animations.getAsJsonObject("questplus.third_person_open").getAsJsonObject("bones");
            var close=animations.getAsJsonObject("questplus.third_person_close").getAsJsonObject("bones");
            for (var bone:open.entrySet()) for (var channel:bone.getValue().getAsJsonObject().entrySet()) {
                var reverse=close.getAsJsonObject(bone.getKey()).getAsJsonObject(channel.getKey());
                for (var frame:channel.getValue().getAsJsonObject().entrySet()) {
                    double time=TabletThirdPersonAnimation.OPEN_SECONDS-Double.parseDouble(frame.getKey());
                    var other=reverse.entrySet().stream().filter(e -> Math.abs(Double.parseDouble(e.getKey())-time)<1e-6).findFirst().orElseThrow();
                    assertEquals(frame.getValue(),other.getValue(),bone.getKey()+"/"+channel.getKey()+" at "+frame.getKey());
                }
            }
        }
    }
}
