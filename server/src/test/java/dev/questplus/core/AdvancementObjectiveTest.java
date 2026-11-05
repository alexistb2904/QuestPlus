package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvancementObjectiveTest {
    @TempDir Path root;

    @Test void completesOnlyWhenServerReportsAdvancementIsDone() throws Exception {
        Files.writeString(root.resolve("test.json5"), """
            { schemaVersion:2, id:'test:advance', category:'test:chapter',
              icon:'minecraft:book', title:{en_us:'Stone Age'},
              objectives:[{id:'adv',event:'advancement_complete',
                target:'minecraft:story/mine_stone',count:1}],
              progression:'once', rewards:[] }
            """);
        var engine=new QuestEngine(QuestRegistry.load(root,(kind,target)->{}));
        var player=new PlayerProgress();
        assertTrue(engine.apply(player,event(0)).isEmpty());
        assertEquals(List.of("test:advance"),engine.apply(player,event(1)));
        assertTrue(engine.apply(player,event(1)).isEmpty());
    }
    @Test void advancementRewardRequiresAValidIdentifier() {
        var valid=com.google.gson.JsonParser.parseString(
            "{\"advancement\":\"minecraft:story/iron_tools\"}").getAsJsonObject();
        assertDoesNotThrow(()->QuestExtensions.validateReward("questplus:advancement",valid));
        var malformed=com.google.gson.JsonParser.parseString(
            "{\"advancement\":\"/execute as @e\"}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
            ()->QuestExtensions.validateReward("questplus:advancement",malformed));
    }

    private QuestEvent event(int value) {
        return new QuestEvent("advancement_complete","minecraft:story/mine_stone",Set.of(),1,
            Map.of("stateValues",Map.of("minecraft:story/mine_stone",value)));
    }
}
