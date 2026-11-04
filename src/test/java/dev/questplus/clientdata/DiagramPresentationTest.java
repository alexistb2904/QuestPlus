package dev.questplus.clientdata;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.questplus.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class DiagramPresentationTest {
    @TempDir Path temp;

    private QuestRegistry registry() throws Exception {
        String template="""
            {"schemaVersion":1,"id":"questplus:%s","category":"questplus:test","icon":"minecraft:stone",
             "title":{"en_us":"Example"},"progression":"once","objectives":[{"id":"step",
             "event":"block_break","target":"minecraft:stone","count":1}],"rewards":[]%s}
            """;
        Files.writeString(temp.resolve("a.json"),template.formatted("a",""));
        Files.writeString(temp.resolve("b.json"),template.formatted("b",",\"hidden\":true,\"requires\":{\"quest\":\"questplus:a\"}"));
        Files.writeString(temp.resolve("c.json"),template.formatted("c",""));
        return QuestRegistry.load(temp,(kind,id) -> {});
    }
    private static com.google.gson.JsonElement json(String value) { return JsonParser.parseString(value); }

    @Test void routesAreValidatedAndHiddenQuestIdsAreNotProjected() throws Exception {
        var registry=registry();
        var valid=json("""
            [{"from":"questplus:a","to":"questplus:b","points":[{"x":42,"y":-15},{"x":80,"y":4}]}]
            """);
        var routes=ClientProjection.routes(valid,"questplus:test",registry);
        assertEquals(List.of(new ClientProjection.RoutePoint(42,-15),new ClientProjection.RoutePoint(80,4)),routes.getFirst().points());
        var category=new ClientProjection.Category("questplus:test",Map.of("en_us","Test"),Map.of("en_us","Test"),
            "minecraft:stone",List.of(),0,List.of(),routes);
        var state=new PlayerProgress();
        var initial=ClientProjection.categories(List.of(category),ClientProjection.visible(registry.all(),state));
        assertFalse(initial.toString().contains("questplus:b"));
        assertEquals(0,initial.get(0).getAsJsonObject().getAsJsonArray("linkRoutes").size());
        new QuestEngine(registry).grant(state,"questplus:a");
        var after=ClientProjection.categories(List.of(category),ClientProjection.visible(registry.all(),state));
        assertEquals(valid,after.get(0).getAsJsonObject().getAsJsonArray("linkRoutes"));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.routes(json("""
            [{"from":"questplus:a","to":"questplus:c","points":[]}]
            """),"questplus:test",registry));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.routes(json("""
            [{"from":"questplus:a","to":"questplus:b","points":[],"extra":true}]
            """),"questplus:test",registry));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.routes(json("""
            [{"from":"questplus:a","to":"questplus:b","points":[{"x":100001,"y":0}]}]
            """),"questplus:test",registry));
        var duplicate=valid.getAsJsonArray().deepCopy(); duplicate.add(duplicate.get(0).deepCopy());
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.routes(duplicate,"questplus:test",registry));
        var tooMany=valid.getAsJsonArray().deepCopy();
        for (int i=0;i<64;i++) tooMany.get(0).getAsJsonObject().getAsJsonArray("points").add(json("{\"x\":0,\"y\":0}"));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.routes(tooMany,"questplus:test",registry));
    }

    @Test void backgroundRequiresBoundedIntegerSizeAndNoRichContent() {
        var note=json("""
            {"id":"area","x":12,"y":-25,"width":400,"height":180,"text":{},"style":"background","color":"#886644"}
            """).getAsJsonObject();
        assertDoesNotThrow(() -> ClientProjection.validateBackground(note,"area"));
        var fractional=note.deepCopy(); fractional.addProperty("width",2.5);
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.validateBackground(fractional,"area"));
        var tooLarge=note.deepCopy(); tooLarge.addProperty("height",100001);
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.validateBackground(tooLarge,"area"));
        var stringX=note.deepCopy(); stringX.addProperty("x","12");
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.validateBackground(stringX,"area"));
        var nan=note.deepCopy(); nan.add("x",new JsonPrimitive(Double.NaN));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.validateBackground(nan,"area"));
        var rich=note.deepCopy(); rich.add("rich",json("{}"));
        assertThrows(IllegalArgumentException.class,() -> ClientProjection.validateBackground(rich,"area"));
    }
}
