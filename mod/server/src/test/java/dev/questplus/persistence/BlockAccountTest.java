package dev.questplus.persistence;

import com.google.gson.*;
import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockAccountTest {
    @TempDir Path root;
    private BlockAccount account(UUID id,int amount) {
        var data=new JsonObject();data.addProperty("items",amount);
        return new BlockAccount(1,id,"minecraft:overworld",123,data);
    }
    @Test void balancesAndQuestProgressCommitTogether() throws Exception {
        UUID station=UUID.randomUUID(),player=UUID.randomUUID();
        try(var store=new JsonProgressStore(root)) {
            store.transaction(Map.of(),Map.of(),Map.of(station,account(station,8))).join();
            var progress=new PlayerProgress();progress.blockContributions=8;
            store.transaction(Map.of(player,progress),Map.of(),Map.of(station,account(station,0))).join();
        }
        try(var reopened=new JsonProgressStore(root)) {
            assertEquals(8,reopened.load(player).blockContributions);
            assertEquals(0,reopened.loadBlock(station).data().get("items").getAsInt());
        }
    }
    @Test void rejectsAccountMismatchAndKeepsOriginalBalance() throws Exception {
        UUID station=UUID.randomUUID();
        try(var store=new JsonProgressStore(root)) {
            store.transaction(Map.of(),Map.of(),Map.of(station,account(station,8))).join();
            assertThrows(RuntimeException.class,()->store.transaction(Map.of(),Map.of(),Map.of(station,account(UUID.randomUUID(),0))).join());
            assertEquals(8,store.loadBlock(station).data().get("items").getAsInt());
        }
    }
    @Test void oldSchemaTwoSavesRemainReadable() throws Exception {
        UUID player=UUID.randomUUID();
        Files.createDirectories(root.resolve("players"));
        Files.writeString(root.resolve("players/"+player+".json"),"""
                {"schemaVersion":2,"quests":{},"claims":{},"entitlements":{},"teamId":null,"legacyUnclaimed":[]}
                """);
        try(var store=new JsonProgressStore(root)) {
            assertEquals(0,store.load(player).blockContributions);assertEquals("",store.load(player).lastKnownName);
        }
    }
    @Test void interruptedDebitRecoversBothImagesExactlyOnce() throws Exception {
        UUID station=UUID.randomUUID(),player=UUID.randomUUID();
        try(var store=new JsonProgressStore(root)) {
            store.transaction(Map.of(),Map.of(),Map.of(station,account(station,8))).join();
        }
        var progress=new PlayerProgress();progress.blockContributions=8;
        try(var store=new JsonProgressStore(root,point->{if(point.startsWith("file:players/"))throw new IOException("simulated interruption");})) {
            assertThrows(RuntimeException.class,()->store.transaction(Map.of(player,progress),Map.of(),Map.of(station,account(station,0))).join());
        }
        for(int restart=0;restart<2;restart++)try(var store=new JsonProgressStore(root)) {
            assertEquals(8,store.load(player).blockContributions);
            assertEquals(0,store.loadBlock(station).data().get("items").getAsInt());
        }
    }
}
