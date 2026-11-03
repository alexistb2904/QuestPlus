package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedCoreTest {
    @TempDir Path dir;

    private QuestRegistry load() throws Exception { return QuestRegistry.load(dir, (kind, id) -> {}); }
    private void write(String json) throws Exception { Files.writeString(dir.resolve("quest.json"), json); }
    private QuestEvent event(String type, String target, long amount, Map<String,Object> context) {
        return new QuestEvent(type, target, Set.of(), amount, context);
    }
    private String simple(String progression, String fields, String objective) {
        return """
            {"schemaVersion":2,"id":"questplus:test","category":"questplus:general","icon":"minecraft:stone","title":{"en_us":"Test"},"description":{"en_us":"Test"},"objectives":[%s],"rewards":[],"progression":"%s"%s}
            """.formatted(objective, progression, fields);
    }

    @Test void informationQuestAcknowledgementIsGeneratedFromEmptyObjectives() throws Exception {
        write("""
          {"schemaVersion":2,"kind":"information","acknowledgement":"manual","id":"questplus:test",
           "category":"questplus:general","icon":"minecraft:book",
           "title":{"en_us":"Information","fr_fr":"Information"},"description":{"en_us":"Read this guide"},
           "narrative":{"en_us":"A story page","fr_fr":"Une page d'histoire"},
           "objectives":[],"rewards":[],"progression":"once"}
          """);
        QuestRegistry registry=load();
        var quest=registry.get("questplus:test");
        assertEquals("information",quest.kind());
        assertEquals("manual",quest.acknowledgement());
        assertEquals(1,quest.objectives().size());
        assertEquals("manual",quest.objectives().getFirst().event());
        assertEquals("questplus:test",quest.objectives().getFirst().target());
        var player=new PlayerProgress();
        assertEquals(List.of("questplus:test"),new QuestEngine(registry).apply(player,event("manual","questplus:test",1,Map.of())));
        assertTrue(player.quests.get("questplus:test").completed);
    }

    @Test void readOnlyInformationDoesNotCreateGameplayObjective() throws Exception {
        write("""
          {"schemaVersion":2,"kind":"information","acknowledgement":"read_only","id":"questplus:test",
           "category":"questplus:general","icon":"minecraft:book",
           "title":{"en_us":"Information"},"description":{"en_us":"Read only"},
           "objectives":[],"rewards":[],"progression":"once"}
          """);
        var registry=load();
        assertEquals(0,registry.get("questplus:test").objectives().size());
        assertTrue(new QuestEngine(registry).apply(new PlayerProgress(),event("manual","questplus:test",1,Map.of())).isEmpty());
        write(simple("once","",""));
        assertThrows(IllegalArgumentException.class,this::load);
    }

    @Test void informationQuestsRejectOrdinaryGameplayObjectives() throws Exception {
        write(simple("once",",\"kind\":\"information\"","{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}"));
        assertThrows(IllegalArgumentException.class,this::load);
    }

    @Test void repeatableRequiresRestartAndHonorsCooldown() throws Exception {
        write(simple("repeatable", ",\"cooldownSeconds\":10", "{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}"));
        AtomicLong now = new AtomicLong(1_000);
        var engine = new QuestEngine(load(), now::get); var player = new PlayerProgress();
        assertEquals(1, engine.applyDetailed(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
        now.set(10_999); assertThrows(IllegalArgumentException.class, () -> engine.restart(player, "questplus:test"));
        now.set(11_000); engine.restart(player, "questplus:test");
        assertEquals(1, engine.applyDetailed(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
    }

    @Test void deathCannotBypassRepeatableRestartOrCooldown() throws Exception {
        write(simple("repeatable", ",\"cooldownSeconds\":10,\"noDeath\":true", "{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}"));
        AtomicLong now = new AtomicLong(1_000); var engine = new QuestEngine(load(), now::get); var player = new PlayerProgress();
        assertEquals(1, engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
        engine.apply(player, event("player_death", "minecraft:player", 1, Map.of()));
        assertTrue(player.quests.get("questplus:test").waitingRestart);
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> engine.restart(player, "questplus:test"));
        now.set(11_000); engine.restart(player, "questplus:test");
        assertEquals(1, engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
    }

    @Test void infiniteUsesUniqueAwardKeysAndKeepsCumulativeProgress() throws Exception {
        write(simple("infinite", "", "{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":2}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        var first = engine.applyDetailed(player, event("block_break", "minecraft:stone", 2, Map.of())).getFirst();
        var p = player.quests.get("questplus:test"); long cumulative = p.cumulative.get("one");
        var second = engine.applyDetailed(player, event("block_break", "minecraft:stone", 2, Map.of())).getFirst();
        assertNotEquals(first.key(), second.key()); assertEquals(2, p.cycles); assertEquals(0, p.counters.getOrDefault("one", 0L));
        assertEquals(cumulative + 2, p.cumulative.get("one"));
    }

    @Test void infiniteCooldownAppliesBetweenAutomaticAttempts() throws Exception {
        write(simple("infinite", ",\"cooldownSeconds\":10", "{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}"));
        AtomicLong now = new AtomicLong(1_000); var engine = new QuestEngine(load(), now::get); var player = new PlayerProgress();
        assertEquals(1, engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
        assertEquals(0, player.quests.get("questplus:test").counters.getOrDefault("one", 0L));
        now.set(11_000);
        assertEquals(1, engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).size());
    }

    @Test void tiersCrossDistinctThresholdsAndStopAfterLast() throws Exception {
        write("""
            {"schemaVersion":2,"id":"questplus:test","category":"questplus:general","icon":"minecraft:stone","title":{"en_us":"Test"},"description":{"en_us":"Test"},"objectives":[{"id":"one","event":"block_break","target":"minecraft:stone","count":1}],"rewards":[],"progression":"tiered","tiers":[{"id":"a","threshold":1,"rewards":[]},{"id":"b","threshold":2,"rewards":[]},{"id":"c","threshold":5,"rewards":[]}]}
            """);
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        var awards = engine.applyDetailed(player, event("block_break", "minecraft:stone", 5, Map.of()));
        assertEquals(List.of("a", "b", "c"), awards.stream().map(a -> a.key().substring(a.key().lastIndexOf('|') + 1)).toList());
        assertEquals(5, player.quests.get("questplus:test").counters.get("one"));
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
    }

    @Test void allAnyAndSequenceDoNotAdvanceTwoSequenceStepsFromOneEvent() throws Exception {
        write(simple("once", ",\"objectiveMode\":\"sequence\"", "{\"id\":\"first\",\"event\":\"block_break\",\"target\":\"minecraft:oak_log\",\"count\":1},{\"id\":\"second\",\"event\":\"block_break\",\"target\":\"minecraft:oak_log\",\"count\":1}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        assertTrue(engine.apply(player, event("block_break", "minecraft:oak_log", 1, Map.of())).isEmpty());
        assertEquals(1, player.quests.get("questplus:test").counters.get("first"));
        assertFalse(player.quests.get("questplus:test").counters.containsKey("second"));
        assertEquals(List.of("questplus:test"), engine.apply(player, event("block_break", "minecraft:oak_log", 1, Map.of())));
    }

    @Test void sequenceInventoryObjectivesAdvanceOnlyOneStepPerSnapshot() throws Exception {
        write(simple("once", ",\"objectiveMode\":\"sequence\"", "{\"id\":\"first\",\"event\":\"inventory_count\",\"target\":\"minecraft:iron_ingot\",\"count\":1},{\"id\":\"second\",\"event\":\"inventory_count\",\"target\":\"minecraft:iron_ingot\",\"count\":2}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        var snapshot = Map.<String,Object>of("inventory", Map.of("minecraft:iron_ingot", 2));
        assertTrue(engine.apply(player, event("inventory_count", "minecraft:iron_ingot", 0, snapshot)).isEmpty());
        var p = player.quests.get("questplus:test");
        assertEquals(1, p.counters.get("first")); assertEquals(0, p.counters.getOrDefault("second", 0L));
        assertEquals(List.of("questplus:test"), engine.apply(player, event("inventory_count", "minecraft:iron_ingot", 0, snapshot)));
    }

    @Test void allWaitsForEveryObjectiveWhileAnyAcceptsOne() throws Exception {
        String objectives = "{\"id\":\"stone\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1},{\"id\":\"dirt\",\"event\":\"block_break\",\"target\":\"minecraft:dirt\",\"count\":1}";
        write(simple("once", ",\"objectiveMode\":\"all\"", objectives));
        var player = new PlayerProgress(); var engine = new QuestEngine(load());
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
        assertEquals(List.of("questplus:test"), engine.apply(player, event("block_break", "minecraft:dirt", 1, Map.of())));
        write(simple("once", ",\"objectiveMode\":\"any\"", objectives));
        assertEquals(List.of("questplus:test"), new QuestEngine(load()).apply(new PlayerProgress(), event("block_break", "minecraft:stone", 1, Map.of())));
    }

    @Test void cumulativeProgressRevisionChangesEvenWhenCurrentCounterIsSaturated() throws Exception {
        write(simple("once", "", "{\"id\":\"stone\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1},{\"id\":\"dirt\",\"event\":\"block_break\",\"target\":\"minecraft:dirt\",\"count\":1}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of()));
        var p = player.quests.get("questplus:test"); long revision = player.revision;
        engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of()));
        assertEquals(1, p.counters.get("stone")); assertEquals(2, p.cumulative.get("stone"));
        assertTrue(player.revision > revision);
    }

    @Test void timeLimitExpiresAtBoundaryAndDeathResetsAttemptOnly() throws Exception {
        write(simple("repeatable", ",\"timeLimitSeconds\":2,\"noDeath\":true", "{\"id\":\"one\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":3}"));
        AtomicLong now = new AtomicLong(1_000); var engine = new QuestEngine(load(), now::get); var player = new PlayerProgress();
        engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of()));
        now.set(3_000); engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of()));
        var p = player.quests.get("questplus:test"); assertEquals(2, p.cumulative.get("one")); assertEquals(1, p.counters.get("one"));
        engine.apply(player, event("player_death", "minecraft:player", 1, Map.of()));
        assertEquals(0, p.counters.getOrDefault("one", 0L)); assertEquals(2, p.cumulative.get("one"));
    }

    @Test void inventoryCountTracksCurrentStateIncludingZero() throws Exception {
        write(simple("once", "", "{\"id\":\"iron\",\"event\":\"inventory_count\",\"target\":\"minecraft:iron_ingot\",\"count\":2},{\"id\":\"stone\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}"));
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        engine.apply(player, event("inventory_count", "minecraft:iron_ingot", 0, Map.of("inventory", Map.of())));
        assertFalse(player.quests.containsKey("questplus:test"));
        engine.apply(player, event("inventory_count", "minecraft:iron_ingot", 0, Map.of("inventory", Map.of("minecraft:iron_ingot", 2))));
        assertEquals(2, player.quests.get("questplus:test").counters.get("iron"));
        engine.apply(player, event("inventory_count", "minecraft:iron_ingot", 0, Map.of("inventory", Map.of())));
        assertEquals(0, player.quests.get("questplus:test").counters.get("iron"));
        assertTrue(engine.apply(player, event("block_break", "minecraft:stone", 1, Map.of())).isEmpty());
    }

    @Test void registeredExtensionsWorkAndUnknownOnesFail() throws Exception {
        QuestExtensions.registerEvent("test:signal", "marker", false);
        QuestExtensions.registerCondition("test:flag", value -> { if (!value.getAsBoolean()) throw new IllegalArgumentException(); }, (value, context) -> Boolean.TRUE.equals(context.get("flag")));
        QuestExtensions.registerReward("test:token", data -> { if (!data.has("value")) throw new IllegalArgumentException(); });
        write("""
            {"schemaVersion":2,"id":"questplus:test","category":"questplus:general","icon":"minecraft:stone","title":{"en_us":"Test"},"description":{"en_us":"Test"},"objectives":[{"id":"one","event":"test:signal","target":"minecraft:stone","count":1,"conditions":{"test:flag":true}}],"rewards":[{"type":"test:token","data":{"value":1}}],"progression":"once"}
            """);
        var engine = new QuestEngine(load()); var player = new PlayerProgress();
        assertEquals(1, engine.apply(player, event("test:signal", "minecraft:stone", 1, Map.of("flag", true))).size());
        write(simple("once", "", "{\"id\":\"one\",\"event\":\"missing:event\",\"target\":\"minecraft:stone\",\"count\":1}"));
        assertThrows(IllegalArgumentException.class, this::load);
    }

    @Test void schemaOneFingerprintIsStableAndSchemaTwoModeIsMechanical() throws Exception {
        String v1 = """
            {"schemaVersion":1,"id":"questplus:test","category":"questplus:general","icon":"minecraft:stone","title":{"en_us":"Test"},"description":{"en_us":"Test"},"objectives":[{"id":"one","event":"block_break","target":"minecraft:stone","count":2}],"rewards":[],"progression":"once"}
            """;
        write(v1); var old = load().get("questplus:test"); var player = new PlayerProgress();
        new QuestEngine(load()).apply(player, event("block_break", "minecraft:stone", 1, Map.of()));
        write(v1.replace("Test", "Changed")); assertEquals(old.fingerprint(), load().get("questplus:test").fingerprint());
        write(v1.replace("\"schemaVersion\":1", "\"schemaVersion\":2").replace("\"progression\":\"once\"", "\"progression\":\"once\",\"objectiveMode\":\"any\""));
        var changed = new QuestEngine(load()); assertTrue(changed.diagnostic(player, "questplus:test").isPresent());
    }
}
