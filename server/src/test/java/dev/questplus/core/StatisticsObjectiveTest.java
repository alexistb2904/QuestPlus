package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StatisticsObjectiveTest {
    @TempDir Path root;
    @Test void customStatisticsAreServerSnapshotStatesNotIncrementalEvents() throws Exception {
        Files.writeString(root.resolve("stats.json5"),"""
        {schemaVersion:2,id:'questplus:stat',category:'questplus:general',
         icon:'minecraft:clock',title:{en_us:'Jump'},description:{en_us:'Jump 100 times'},
         objectives:[{id:'jumps',event:'custom_stat',target:'minecraft:jump',count:100}],
         rewards:[],progression:'once'}
        """);
        var engine=new QuestEngine(QuestRegistry.load(root,(kind,id)->{}));
        var player=new PlayerProgress();
        var first=new QuestEvent("custom_stat","minecraft:jump",Set.of(),1,
            Map.of("stateValues",Map.of("minecraft:jump",42L)));
        assertTrue(engine.apply(player,first).isEmpty());
        assertEquals(42L,player.quests.get("questplus:stat").counters.get("jumps"));
        assertTrue(engine.apply(player,new QuestEvent("custom_stat","minecraft:jump",Set.of(),1,
            Map.of("stateValues",Map.of("minecraft:jump",12L)))).isEmpty());
        assertEquals(12L,player.quests.get("questplus:stat").counters.get("jumps"));
        assertEquals(List.of("questplus:stat"),engine.apply(player,new QuestEvent("custom_stat","minecraft:jump",Set.of(),1,
            Map.of("stateValues",Map.of("minecraft:jump",100L)))));
        assertTrue(player.quests.get("questplus:stat").terminal);
    }
}
