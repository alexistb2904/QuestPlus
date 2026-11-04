package dev.questplus.clientdata;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class AnnotationProjectionTest {
    @Test void indexedImagesAndDimensionsAreExposedToMinecraftClients(){
        var rich=new JsonArray();
        var image=new com.google.gson.JsonObject();
        image.addProperty("type","image");
        image.addProperty("ref",0);
        image.addProperty("width",280);
        image.addProperty("height",150);
        rich.add(image);
        var note=new ClientProjection.Annotation("banner",10,30,Map.of("fr_fr","Bienvenue"),"heading",
            "#c6a986",Map.of("fr_fr",rich),400,260);
        var cat=new ClientProjection.Category("questplus:test",Map.of("en_us","Test"),
            Map.of("en_us","Example"),"minecraft:book",List.of(note),0,List.of("data:image/png;base64,YWJj"));
        var json=new Gson().toJsonTree(cat).getAsJsonObject();
        assertEquals(1,json.getAsJsonArray("images").size());
        assertEquals(400,json.getAsJsonArray("annotations").get(0).getAsJsonObject().get("width").getAsInt());
        assertEquals(260,json.getAsJsonArray("annotations").get(0).getAsJsonObject().get("height").getAsInt());
        assertEquals(0,json.getAsJsonArray("annotations").get(0).getAsJsonObject()
            .getAsJsonObject("rich").getAsJsonArray("fr_fr").get(0).getAsJsonObject().get("ref").getAsInt());
    }

    @Test void legacyConstructorsRetainDefaultDimensions(){
        var note=new ClientProjection.Annotation("old",0,0,Map.of("en_us","Welcome"),"label","#c6a986");
        assertEquals(170,note.width());
        assertEquals(100,note.height());
        var category=new ClientProjection.Category("questplus:old",Map.of("en_us","Old"),
            Map.of("en_us",""),"minecraft:book");
        assertTrue(category.images().isEmpty());
    }
}
