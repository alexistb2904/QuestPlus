package dev.questplus.clientdata;

import dev.questplus.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientProjectionTest {
    @TempDir Path temp;

    @Test void choiceOptionsArePublicButClaimsWaitForAllPersonalChoices() throws Exception {
        var registry=QuestRegistry.load(Path.of("server/src/main/resources/defaults/quests"),(kind,id)->{});
        var q=registry.get("questplus:survival/logs");
        var choice=new QuestDefinition.Reward("questplus:choice",null,0,0,
            "{\"options\":[{\"type\":\"experience\",\"amount\":7},{\"type\":\"experience_levels\",\"amount\":2}]}",Map.of());
        var personal=new PlayerProgress();
        personal.entitlements.put("private-attempt",new PlayerProgress.RewardGrant(q.id(),List.of(choice,choice)));
        var projected=ClientProjection.progress(List.of(q),new QuestEngine(registry),personal,personal).getAsJsonObject(q.id());
        assertFalse(projected.get("claimable").getAsBoolean());
        var rewardState=projected.getAsJsonObject("rewardState");
        assertEquals("choice",rewardState.get("status").getAsString());
        assertEquals(2,rewardState.getAsJsonArray("rewards").get(0).getAsJsonObject().getAsJsonArray("options").size());
        assertFalse(projected.toString().contains("private-attempt"));
        assertFalse(projected.toString().contains("\"data\""));
        personal.entitlements.put("private-attempt",RewardTables.choose(personal.entitlements.get("private-attempt"),0,0));
        assertFalse(ClientProjection.progress(List.of(q),new QuestEngine(registry),personal,personal).getAsJsonObject(q.id()).get("claimable").getAsBoolean());
        personal.entitlements.put("private-attempt",RewardTables.choose(personal.entitlements.get("private-attempt"),1,1));
        assertTrue(ClientProjection.progress(List.of(q),new QuestEngine(registry),personal,personal).getAsJsonObject(q.id()).get("claimable").getAsBoolean());
    }

    @Test void hiddenQuestsAndTheirReferencesAppearOnlyWhenRevealed() throws Exception {
        String template="""
            {"schemaVersion":1,"id":"questplus:%s","category":"questplus:general","icon":"minecraft:stone",
             "title":{"en_us":"%s"},"description":{"en_us":"Description"},"progression":"once",
             "objectives":[{"id":"one","event":"block_break","target":"minecraft:stone","count":1}],"rewards":[]%s}
            """;
        Files.writeString(temp.resolve("a.json"),template.formatted("a","A",""));
        Files.writeString(temp.resolve("b.json"),template.formatted("secret","secret title",",\"hidden\":true,\"requires\":{\"quest\":\"questplus:a\"}"));
        Files.writeString(temp.resolve("c.json"),template.formatted("c","C",",\"requires\":{\"quest\":\"questplus:secret\"}"));
        QuestRegistry registry=QuestRegistry.load(temp,(kind,id) -> {}); QuestEngine engine=new QuestEngine(registry);
        PlayerProgress state=new PlayerProgress();
        var before=ClientProjection.visible(registry.all(),state);
        String json=ClientProjection.quests(before).toString();
        assertFalse(json.contains("questplus:secret")); assertFalse(json.contains("secret title"));
        assertTrue(json.contains("undiscovered"));
        assertEquals(2,before.size());
        engine.grant(state,"questplus:a");
        var after=ClientProjection.visible(registry.all(),state);
        assertEquals(3,after.size());
        assertTrue(ClientProjection.quests(after).toString().contains("questplus:secret"));
        engine.grant(state,"questplus:secret"); engine.reset(state,"questplus:a");
        assertEquals(3,ClientProjection.visible(registry.all(),state).size());
        engine.reset(state,"questplus:secret");
        assertEquals(2,ClientProjection.visible(registry.all(),state).size());
        Files.writeString(temp.resolve("b.json"),template.formatted("secret","secret title",",\"hidden\":\"true\""));
        assertThrows(IllegalArgumentException.class,() -> QuestRegistry.load(temp,(kind,id) -> {}));
    }
    @Test void emptyRewardEntitlementsAreNotClaimable() throws Exception {
        QuestRegistry registry=QuestRegistry.load(Path.of("server/src/main/resources/defaults/quests"),(kind,id) -> {});
        QuestDefinition information=registry.get("questplus:survival/welcome");
        var engine=new QuestEngine(registry);
        PlayerProgress state=new PlayerProgress(),personal=new PlayerProgress();
        engine.grant(state,information.id());
        personal.entitlements.put("legacy-empty",new PlayerProgress.RewardGrant(information.id(),List.of()));
        var json=ClientProjection.progress(List.of(information),engine,state,personal).getAsJsonObject(information.id());
        assertTrue(json.get("completed").getAsBoolean());
        assertFalse(json.get("claimable").getAsBoolean());
        assertEquals("completed",QuestPresentation.status(information,json));
    }

    @Test void collectiveProgressNeverExposesPersonalLedgerOrExtensionData() throws Exception {
        QuestRegistry registry=QuestRegistry.load(Path.of("server/src/main/resources/defaults/quests"),(kind,id) -> {});
        QuestDefinition q=registry.all().stream().filter(v -> v.progression().equals("once") && !v.objectives().isEmpty() && !v.rewards().isEmpty()).findFirst().orElseThrow();
        QuestEngine engine=new QuestEngine(registry);
        PlayerProgress collective=new PlayerProgress(), personal=new PlayerProgress();
        engine.grant(collective,q.id());
        personal.entitlements.put("private-key",new PlayerProgress.RewardGrant(q.id(),q.rewards()));
        var projected=ClientProjection.progress(List.of(q),engine,collective,personal);
        assertTrue(projected.getAsJsonObject(q.id()).get("completed").getAsBoolean());
        assertTrue(projected.getAsJsonObject(q.id()).get("claimable").getAsBoolean());
        assertFalse(projected.toString().contains("private-key"));
        personal.claims.put("private-key","reserved");
        assertFalse(ClientProjection.progress(List.of(q),engine,collective,personal).getAsJsonObject(q.id()).get("claimable").getAsBoolean());

        var secret=new QuestDefinition.Reward("custom",null,0,0,"secret-server-command",Map.of());
        var copy=new QuestDefinition(q.id(),q.category(),q.icon(),q.title(),q.description(),q.objectives(),q.requires(),List.of(secret),q.progression(),q.layout(),q.narrative(),q.fingerprint(),q.objectiveMode(),q.tiers(),q.afterLast(),q.cooldownSeconds(),q.timeLimitSeconds(),q.noDeath(),q.kind(),q.acknowledgement());
        String json=ClientProjection.quests(List.of(copy)).toString();
        assertFalse(json.contains("secret-server-command"));
        assertFalse(json.contains("\"data\""));
    }
}
