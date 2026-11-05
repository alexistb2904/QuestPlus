package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlexiblePrerequisiteTest {
    @TempDir Path root;

    private QuestEngine engine(boolean flexible) throws Exception {
        Files.writeString(root.resolve("first.json"),"""
            {"schemaVersion":2,"id":"test:first","category":"test:cat","icon":"minecraft:book",
             "title":{"en_us":"First"},"progression":"once",
             "objectives":[{"id":"stone","event":"block_break","target":"minecraft:stone","count":1}],
             "rewards":[]}
            """);
        Files.writeString(root.resolve("next.json"),"""
            {"schemaVersion":2,"id":"test:next","category":"test:cat","icon":"minecraft:book",
             "title":{"en_us":"Next"},"progression":"once","progressBeforeUnlock":%s,
             "requires":{"quest":"test:first"},
             "objectives":[{"id":"dirt","event":"block_break","target":"minecraft:dirt","count":1}],
             "rewards":[]}
            """.formatted(flexible));
        return new QuestEngine(QuestRegistry.load(root,(kind,id)->{}));
    }
    private QuestEvent event(String block){return new QuestEvent("block_break",block,Set.of(),1,Map.of());}

    @Test void flexibleQuestCompletesWhenItsPrerequisiteUnlocks() throws Exception {
        var engine=engine(true);
        var progress=new PlayerProgress();
        assertTrue(engine.apply(progress,event("minecraft:dirt")).isEmpty());
        assertEquals(1L,progress.quests.get("test:next").counters.get("dirt"));
        var completed=engine.apply(progress,event("minecraft:stone"));
        assertEquals(Set.of("test:first","test:next"),new HashSet<>(completed));
        assertTrue(progress.quests.get("test:next").terminal);
    }
    @Test void strictQuestDoesNotCountLockedActions() throws Exception {
        var engine=engine(false);
        var progress=new PlayerProgress();
        engine.apply(progress,event("minecraft:dirt"));
        assertFalse(progress.quests.containsKey("test:next"));
        assertEquals(List.of("test:first"),engine.apply(progress,event("minecraft:stone")));
        assertEquals(List.of("test:next"),engine.apply(progress,event("minecraft:dirt")));
    }
}
