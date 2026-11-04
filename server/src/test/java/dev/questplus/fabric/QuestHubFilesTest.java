package dev.questplus.fabric;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestHubFilesTest {
    private JsonArray valid(){
        JsonArray docs=new JsonArray();
        for(String[] row:new String[][]{
            {"config.json","{\"schemaVersion\":1,\"autosaveSeconds\":5}"},
            {"categories/a.json","{\"id\":\"questplus:a\"}"},
            {"quests/a.json","{\"id\":\"questplus:a\"}"}
        }){
            JsonObject file=new JsonObject();file.addProperty("name",row[0]);file.addProperty("content",row[1]);docs.add(file);
        }
        return docs;
    }
    @Test void acceptsAndRejectsUntrustedDocuments() throws Exception{
        JsonArray good=valid();assertDoesNotThrow(()->QuestHubFiles.validate(good));
        JsonArray duplicate=good.deepCopy();duplicate.add(good.get(1));assertThrows(Exception.class,()->QuestHubFiles.validate(duplicate));
        JsonArray traversal=good.deepCopy();traversal.get(1).getAsJsonObject().addProperty("name","quests/../evil.json");
        assertThrows(Exception.class,()->QuestHubFiles.validate(traversal));
        JsonArray malformed=good.deepCopy();malformed.get(0).getAsJsonObject().addProperty("content","{");
        assertThrows(Exception.class,()->QuestHubFiles.validate(malformed));
        JsonArray incomplete=good.deepCopy();incomplete.remove(0);
        assertThrows(Exception.class,()->QuestHubFiles.validate(incomplete));
    }
    @Test void snapshotsOnlyDefinitionsAndNeverPlayerProgress() throws Exception{
        Path temp=Files.createTempDirectory("questhub-test");
        try{
            Path def=temp.resolve("questplus");
            for(var el:valid()){
                var doc=el.getAsJsonObject();Path file=def.resolve(doc.get("name").getAsString());
                Files.createDirectories(file.getParent());Files.writeString(file,doc.get("content").getAsString());
            }
            Path player=temp.resolve("data/questplus/players/test.json");Files.createDirectories(player.getParent());Files.writeString(player,"{}");
            JsonArray snapshot=QuestHubFiles.snapshot(def);
            assertEquals(3,snapshot.size());
            for(var el:snapshot)assertFalse(el.getAsJsonObject().get("name").getAsString().contains("players"));
        }finally{
            try(var walk=Files.walk(temp)){for(Path file:walk.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}
        }
    }
}
