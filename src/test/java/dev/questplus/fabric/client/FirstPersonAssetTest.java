package dev.questplus.fabric.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("rightArm"));
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("leftArm"));
            assertTrue(animations.getAsJsonObject("questplus.open").getAsJsonObject("bones").has("tablet_root"));
            assertTrue(animations.has("questplus.idle"));
            assertTrue(animations.has("questplus.close"));
        }
    }
    @Test void fpPalAutoModelResolutionHasBothGeometryAndTexture() throws Exception {
        try (var stream=asset("/assets/questplus/fp_models/questplus_first_person.geo.json")) {
            var geo=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry");
            assertEquals(1,geo.size());
            var names=geo.get(0).getAsJsonObject().getAsJsonArray("bones");
            assertTrue(names.size()>5);
        }
        try (var stream=asset("/assets/questplus/textures/fp/questplus_first_person.png")) {
            byte[] signature=stream.readNBytes(8);
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},signature);
        }
    }
}
