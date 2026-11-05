package dev.questplus.persistence;

import com.google.gson.JsonArray;
import dev.questplus.core.PlayerProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class InventoryRecoveryTest {
    @TempDir Path root;
    private JsonArray inventory() { var array=new JsonArray(); for(int i=0;i<37;i++)array.add(new com.google.gson.JsonObject()); return array; }
    private PlayerProgress progress(String value) { var p=new PlayerProgress(); p.claims.put("questplus:test",value); return p; }
    @Test void firstImageInterruptedBeforeRenameRecoversItsTemporaryFile() throws Exception {
        UUID player=UUID.randomUUID(),old=UUID.randomUUID(),operation=UUID.randomUUID();
        try(var store=JsonProgressStore.withSavedMarker(root,id->old.toString())) {
            store.prepareInventory(operation,player,old.toString(),inventory(),inventory(),Map.of(player,progress("delivered")),Map.of()).join();
        }
        Path temporary=root.resolve("players/"+player+".json.tmp");Files.createDirectories(temporary.getParent());
        Files.writeString(temporary,"partially written image");
        try(var recovered=JsonProgressStore.withSavedMarker(root,id->operation.toString())) {
            assertEquals("delivered",recovered.load(player).claims.get("questplus:test"));
            assertFalse(Files.exists(temporary));
        }
    }
    @Test void markerDecidesCommitOrAbort() throws Exception {
        for(boolean consumed:List.of(false,true)) {
            Path path=root.resolve(Boolean.toString(consumed)); UUID player=UUID.randomUUID(),old=UUID.randomUUID(),operation=UUID.randomUUID();
            try(var store=JsonProgressStore.withSavedMarker(path,id->old.toString())) {
                store.save(player,progress("reserved")).join();
                store.prepareInventory(operation,player,old.toString(),inventory(),inventory(),Map.of(player,progress("delivered")),Map.of()).join();
                assertThrows(CompletionException.class,()->store.commitInventory(operation).join());
            }
            try(var store=JsonProgressStore.withSavedMarker(path,id->(consumed?operation:old).toString())) {
                assertEquals(consumed?"delivered":"reserved",store.load(player).claims.get("questplus:test"));
            }
        }
    }
    @Test void unknownMarkerAndChangedImageFreezeRecovery() throws Exception {
        UUID player=UUID.randomUUID(),old=UUID.randomUUID(),operation=UUID.randomUUID();
        try(var store=JsonProgressStore.withSavedMarker(root,id->old.toString())) {
            store.save(player,progress("reserved")).join();
            store.prepareInventory(operation,player,old.toString(),inventory(),inventory(),Map.of(player,progress("delivered")),Map.of()).join();
        }
        assertThrows(java.io.IOException.class,()->JsonProgressStore.withSavedMarker(root,id->UUID.randomUUID().toString()));
        Files.writeString(root.resolve("players/"+player+".json"),"{}");
        assertThrows(java.io.IOException.class,()->JsonProgressStore.withSavedMarker(root,id->operation.toString()));
        assertTrue(Files.exists(root.resolve("transactions/inventory.json")));
    }
    @Test void resolutionWritesAuditWithClaim() throws Exception {
        UUID player=UUID.randomUUID(),auditId=UUID.randomUUID();
        try(var store=new JsonProgressStore(root)) {
            store.save(player,progress("reserved")).join();
            var audit=new JsonProgressStore.ClaimAudit(auditId,player,"questplus:test","operator","discard","reviewed",1);
            store.resolveClaim(player,progress("delivered"),audit).join();
            assertEquals("delivered",store.load(player).claims.get("questplus:test"));
            assertTrue(Files.readString(root.resolve("audit/"+auditId+".json")).contains("discard"));
            assertThrows(CompletionException.class,()->store.resolveClaim(player,progress("delivered"),audit).join());
        }
    }
    @Test void consumedRecoveryFinishesInterruptedPlayerAndTeamWrites() throws Exception {
        UUID player=UUID.randomUUID(),teamId=UUID.randomUUID(),old=UUID.randomUUID(),operation=UUID.randomUUID();
        var team=new dev.questplus.team.TeamData(); team.id=teamId; team.owner=player; team.name="test"; team.members.add(player);
        var after=progress("delivered"); after.teamId=teamId;
        try(var store=new JsonProgressStore(root,point->{if(point.startsWith("inventory:file:"))throw new java.io.IOException("crash");},id->operation.toString())) {
            store.save(player,progress("reserved")).join();
            store.prepareInventory(operation,player,old.toString(),inventory(),inventory(),Map.of(player,after),Map.of(teamId,team)).join();
            assertThrows(CompletionException.class,()->store.commitInventory(operation).join());
        }
        try(var store=JsonProgressStore.withSavedMarker(root,id->operation.toString())) {
            assertEquals(teamId,store.load(player).teamId);
            assertEquals(player,store.loadTeams().get(teamId).owner);
            assertFalse(Files.exists(root.resolve("transactions/inventory.json")));
        }
    }
}
