package dev.questplus.fabric.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FirstPersonAssetTest {
    private static InputStream asset(String path) {
        var resource=FirstPersonAssetTest.class.getResourceAsStream(path);
        assertNotNull(resource,"Missing in mod runtime resources: "+path);
        return resource;
    }
    @Test void blockbenchOpenAndIdleLoadUnderTheCorrectResourceNames() throws Exception {
        try (var stream=asset("/assets/questplus/fp_animations/questplus_first_person.animation.json")) {
            var animations=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("animations");
            assertEquals(1.2,animations.getAsJsonObject("questplus.open").get("animation_length").getAsDouble(),.00001);
            // Prevent a one-frame reset before FP PAL switches to the matching idle clip.
            assertEquals("hold_on_last_frame",
                animations.getAsJsonObject("questplus.open").get("loop").getAsString());
            var open=animations.getAsJsonObject("questplus.open").getAsJsonObject("bones");
            var idle=animations.getAsJsonObject("questplus.idle").getAsJsonObject("bones");
            for (String bone:new String[]{"rightArm","leftArm","tablet_root"}) {
                for (String channel:bone.equals("tablet_root") ? new String[]{"rotation","position","scale"} : new String[]{"rotation","position"}) {
                    var end=open.getAsJsonObject(bone).getAsJsonObject(channel);
                    var begin=idle.getAsJsonObject(bone).getAsJsonObject(channel);
                    String endKey=end.keySet().stream().max(java.util.Comparator.comparingDouble(Double::parseDouble)).orElseThrow();
                    String beginKey=begin.keySet().stream().min(java.util.Comparator.comparingDouble(Double::parseDouble)).orElseThrow();
                    assertEquals(end.get(endKey),begin.get(beginKey),
                        "Blockbench open/idle discontinuity: "+bone+" "+channel);
                }
            }
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("rightArm"));
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("leftArm"));
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("tablet_root"));
            assertTrue(animations.has("questplus.idle"));
            assertTrue(animations.has("questplus.close"));
        }
    }
    @Test void runtimeAnimationMatchesBlockbenchSource() throws Exception {
        var source=JsonParser.parseString(Files.readString(Path.of("questplus-assets/blockbench/questplus_first_person.bbmodel"))).getAsJsonObject();
        try (var stream=asset("/assets/questplus/fp_animations/questplus_first_person.animation.json")) {
            var exports=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            for (var entry:source.getAsJsonArray("animations")) {
                var animation=entry.getAsJsonObject();
                var name=animation.get("name").getAsString();
                var exported=exports.getAsJsonObject(name).getAsJsonObject("bones");
                int snapping=animation.get("snapping").getAsInt();
                for (var animator:animation.getAsJsonObject("animators").entrySet()) {
                    var track=animator.getValue().getAsJsonObject();
                    String bone=track.get("name").getAsString();
                    for (var frame:track.getAsJsonArray("keyframes")) {
                        var key=frame.getAsJsonObject();
                        var channel=key.get("channel").getAsString();
                        double time=Math.round(key.get("time").getAsDouble()*snapping)/(double)snapping;
                        var output=exported.getAsJsonObject(bone).getAsJsonObject(channel).getAsJsonArray(Double.toString(time));
                        assertNotNull(output,name+" "+bone+" "+channel+" at "+time);
                        var value=key.getAsJsonArray("data_points").get(0).getAsJsonObject();
                        for (int i=0;i<3;i++) {
                            double component=value.get("xyz".substring(i,i+1)).getAsDouble();
                            if ((channel.equals("rotation") && i<2) || (channel.equals("position") && i==0)) component=-component;
                            assertEquals(component,output.get(i).getAsDouble(),0.000001,name+" "+bone+" "+channel+" at "+time);
                        }
                    }
                }
            }
        }
    }
    @Test void fpPalAutoModelResolutionHasBothGeometryAndTexture() throws Exception {
        try (var stream=asset("/assets/questplus/fp_models/questplus_first_person.geo.json")) {
            var geo=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry");
            assertEquals(1,geo.size());
            var names=geo.get(0).getAsJsonObject().getAsJsonArray("bones");
            assertTrue(names.size()>5);
            for (var entry:names) {
                var bone=entry.getAsJsonObject();
                if (java.util.Set.of("rightArm","leftArm").contains(bone.get("name").getAsString()))
                    assertFalse(bone.has("cubes"),"The FP model must not contain extra arms");
            }
        }
        try (var stream=asset("/assets/questplus/textures/fp/questplus_first_person.png")) {
            byte[] signature=stream.readNBytes(8);
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},signature);
        }
    }
    @Test void cameraFrameAndPhysicalSizeAreIdenticalThroughoutTheSequence() throws Exception {
        try (var stream=asset("/assets/questplus/fp_animations/questplus_first_person.animation.json")) {
            var animations=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("animations");
            var open=animations.getAsJsonObject("questplus.open");
            for (String name:java.util.List.of("questplus.open","questplus.idle","questplus.close")) {
                var clip=animations.getAsJsonObject(name);
                assertEquals(open.get("player_animation_library"),clip.get("player_animation_library"));
                assertEquals(open.getAsJsonObject("bones").get("fp_player_arms"),clip.getAsJsonObject("bones").get("fp_player_arms"));
                for (var value:clip.getAsJsonObject("bones").getAsJsonObject("tablet_root").getAsJsonObject("scale").entrySet())
                    for (var component:value.getValue().getAsJsonArray()) assertEquals(1,component.getAsDouble());
            }
        }
    }
}
