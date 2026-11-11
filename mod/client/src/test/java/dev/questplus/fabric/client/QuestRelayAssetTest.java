package dev.questplus.fabric.client;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestRelayAssetTest {
    private static final Path ASSETS = Path.of("client/src/main/resources/assets/questplus");
    private static JsonObject json(String file) throws Exception {
        return JsonParser.parseString(Files.readString(ASSETS.resolve(file))).getAsJsonObject();
    }
    @Test void relayHasLowCabinetAndDistinctSignalLens() throws Exception {
        var off = json("models/block/quest_relay.json");
        var on = json("models/block/quest_relay_on.json");
        assertEquals("questplus:block/quest_relay", off.getAsJsonObject("textures").get("utility").getAsString());
        var names = new HashSet<String>();
        double height = 0;
        for (var element : off.getAsJsonArray("elements")) {
            var cube = element.getAsJsonObject(); names.add(cube.get("name").getAsString());
            height = Math.max(height, cube.getAsJsonArray("to").get(1).getAsDouble());
        }
        assertTrue(height >= 7 && height <= 9, "Low wooden relay cabinet");
        assertTrue(names.containsAll(Set.of("timber_lid", "front_signal_lens", "back_socket", "side_redstone_west")));
        assertEquals(off.getAsJsonArray("elements").size(), on.getAsJsonArray("elements").size());
        assertNotEquals(off.getAsJsonArray("elements"), on.getAsJsonArray("elements"));
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/quest_relay.png")));
    }
    @Test void utilityItemsHaveSmallDropsAndExplicitHandPoses() throws Exception {
        for (String name : List.of("quest_relay", "submission_station", "quest_board")) {
            var display = json("models/item/" + name + ".json").getAsJsonObject("display");
            String source = name.equals("quest_board") ? "quest_board_module_15" : name;
            var editable = JsonParser.parseString(Files.readString(Path.of("questplus-assets/blockbench/world/" + source + ".bbmodel"))).getAsJsonObject();
            assertEquals(editable.getAsJsonObject("display"), display, "Editable poses must survive export: " + name);
            assertEquals(display.getAsJsonObject("thirdperson_righthand").get("rotation"), display.getAsJsonObject("thirdperson_lefthand").get("rotation"),
                    "Minecraft mirrors the left-hand transform itself");
            for (var scale : display.getAsJsonObject("ground").getAsJsonArray("scale")) assertTrue(scale.getAsDouble() <= .25);
            for (String slot : List.of("ground", "firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand")) {
                assertTrue(display.has(slot), name + ": " + slot);
                var transform = display.getAsJsonObject(slot);
                for (var scale : transform.getAsJsonArray("scale"))
                    assertTrue(scale.getAsDouble() > 0 && scale.getAsDouble() <= .5, name + ": " + slot);
                assertEquals(3, transform.getAsJsonArray("translation").size());
            }
        }
    }
}
