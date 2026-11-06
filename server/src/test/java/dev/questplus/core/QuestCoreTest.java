package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestCoreTest {
    @TempDir Path dir;
    private String quest(String id, String target, String requires) {
        return """
        {"schemaVersion":1,"id":"questplus:%s","category":"questplus:general","icon":"minecraft:stone","title":{"en_us":"Title"},"description":{"en_us":"Description"},"objectives":[{"id":"one","event":"block_break","target":"%s","count":2}],"rewards":[],"progression":"once"%s}
        """.formatted(id,target,requires);
    }
    private QuestRegistry load() throws Exception { return QuestRegistry.load(dir,(kind,id) -> {}); }
    private void write(String name,String text) throws Exception { Files.writeString(dir.resolve(name + ".json"),text); }
    private QuestEvent event(long amount) { return new QuestEvent("block_break","minecraft:oak_log",Set.of("minecraft:logs"),amount,Map.of()); }
    @Test void candidateFastPathMatchesExactAndTagTargets() throws Exception {
        write("exact",quest("exact","minecraft:oak_log",""));
        write("tag",quest("tag","#minecraft:logs",""));
        QuestRegistry registry=load();
        assertTrue(registry.hasCandidate("block_break","minecraft:oak_log",Set.of()));
        assertTrue(registry.hasCandidate("block_break","minecraft:birch_log",Set.of("minecraft:logs")));
        assertFalse(registry.hasCandidate("block_break","minecraft:birch_log",Set.of()));
        assertFalse(registry.hasCandidate("item_obtained","minecraft:oak_log",Set.of("minecraft:logs")));
        assertEquals(Set.of("minecraft:oak_log","#minecraft:logs"),new HashSet<>(registry.targets("block_break")));
        assertTrue(registry.targets("item_obtained").isEmpty());
    }
    @Test void indexedCandidatesDoNotDuplicateAnObjectiveMatchingItemAndTag() throws Exception {
        write("multi","""
            {"schemaVersion":1,"id":"questplus:multi","category":"questplus:general",
             "icon":"minecraft:diamond","title":{"en_us":"Multi"},
             "objectives":[{"id":"one","event":"item_obtained","target":"minecraft:diamond","count":1,
                 "data":{"targets":["minecraft:diamond","#minecraft:gems"]}}],
             "rewards":[],"progression":"once"}
            """);
        QuestRegistry registry=load();
        var event=new QuestEvent("item_obtained","minecraft:diamond",Set.of("minecraft:gems"),1,Map.of());
        assertEquals(1,registry.candidates(event).size());
        assertEquals("questplus:multi",registry.candidates(event).getFirst().quest().id());
        assertEquals(1,registry.candidates(new QuestEvent("item_obtained","minecraft:diamond",Set.of(),1,Map.of())).size());
        assertTrue(registry.candidates(new QuestEvent("item_obtained","minecraft:stick",Set.of(),1,Map.of())).isEmpty());
    }

    @Test void tagsSaturationAndDependencySnapshot() throws Exception {
        write("a",quest("a","#minecraft:logs","")); write("b",quest("b","minecraft:oak_log",",\"requires\":{\"quest\":\"questplus:a\"}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        assertEquals(List.of("questplus:a"),engine.apply(player,event(Long.MAX_VALUE)));
        assertFalse(player.quests.containsKey("questplus:b"));
        assertEquals(2L,player.quests.get("questplus:a").counters.get("one"));
        assertEquals(List.of("questplus:b"),engine.apply(player,event(2)));
    }
    @Test void mismatchPreservesProgress() throws Exception {
        write("a",quest("a","minecraft:oak_log","")); var player = new PlayerProgress();
        new QuestEngine(load()).apply(player,event(1));
        write("a",quest("a","#minecraft:logs",""));
        var engine = new QuestEngine(load());
        assertTrue(engine.diagnostic(player,"questplus:a").isPresent());
        assertTrue(engine.apply(player,event(1)).isEmpty());
        assertEquals(1L,player.quests.get("questplus:a").counters.get("one"));
    }
    @Test void resetKeepsClaimLedger() throws Exception {
        write("a",quest("a","minecraft:oak_log","")); var engine = new QuestEngine(load()); var player = new PlayerProgress();
        engine.grant(player,"questplus:a"); player.claims.put("questplus:a","delivered"); engine.reset(player,"questplus:a");
        assertFalse(player.quests.containsKey("questplus:a")); assertEquals("delivered",player.claims.get("questplus:a")); player.validate();
    }
    @Test void rejectsCyclesEvenUnderNot() throws Exception {
        write("a",quest("a","minecraft:oak_log",",\"requires\":{\"not\":{\"quest\":\"questplus:b\"}}"));
        write("b",quest("b","minecraft:oak_log",",\"requires\":{\"quest\":\"questplus:a\"}"));
        assertThrows(IllegalArgumentException.class,this::load);
    }
    @Test void rejectsUnknownFieldsAndUnsupportedProgression() throws Exception {
        write("a",quest("a","minecraft:oak_log","").replace("\"once\"","\"daily\"")); assertThrows(IllegalArgumentException.class,this::load);
        write("a",quest("a","minecraft:oak_log","").replace("\"once\"","\"once\",\"typo\":true")); assertThrows(IllegalArgumentException.class,this::load);
    }
    @Test void conditionsAndLocale() {
        var c = new QuestDefinition.Condition("healthAtMost","2",List.of());
        assertTrue(c.matches(Map.of("health",2),Set.of())); assertFalse(c.matches(Map.of("health",3),Set.of()));
        assertEquals("Hello $1",Localization.text(Map.of("en_us","Hello {name}"),"fr_fr",Map.of("name","$1")));
    }
    @Test void duplicateEventsAndTranslationEdit() throws Exception {
        write("a",quest("a","minecraft:oak_log","")); var player = new PlayerProgress(); var e = event(1);
        new QuestEngine(load()).apply(player,e);
        write("a",quest("a","minecraft:oak_log","").replace("Title","Changed"));
        var engine = new QuestEngine(load()); assertTrue(engine.diagnostic(player,"questplus:a").isEmpty());
        assertTrue(engine.apply(player,e).isEmpty()); assertEquals(1L,player.quests.get("questplus:a").counters.get("one"));
        assertEquals(List.of("questplus:a"),engine.apply(player,event(1)));
    }
    @Test void rejectsDuplicateKeysAndMalformedJson() throws Exception {
        write("a",quest("a","minecraft:oak_log","").replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"));
        assertThrows(IllegalArgumentException.class,this::load);
        write("a",quest("a","minecraft:oak_log","") + " true");
        assertThrows(Exception.class,this::load);
    }
    @Test void invalidSaveRejected() { var p = new PlayerProgress(); p.claims.put("x","pending"); assertThrows(IllegalArgumentException.class,p::validate); }
}
