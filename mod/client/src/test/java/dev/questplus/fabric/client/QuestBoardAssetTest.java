package dev.questplus.fabric.client;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestBoardAssetTest {
    private static final Path ASSETS=Path.of("client/src/main/resources/assets/questplus");
    @Test void itemPresentationSurvivesExportAndKeepsTheBoardNearItsGrip()throws Exception {
        var source=JsonParser.parseString(Files.readString(Path.of("questplus-assets/blockbench/world/quest_board_module_15.bbmodel"))).getAsJsonObject();
        var item=JsonParser.parseString(Files.readString(ASSETS.resolve("models/item/quest_board.json"))).getAsJsonObject();
        var display=item.getAsJsonObject("display");assertEquals(source.getAsJsonObject("display"),display);
        assertEquals(display.get("thirdperson_righthand"),display.get("thirdperson_lefthand"));
        double minZ=16,maxZ=0;
        for(var element:source.getAsJsonArray("elements")){
            var e=element.getAsJsonObject();minZ=Math.min(minZ,e.getAsJsonArray("from").get(2).getAsDouble());
            maxZ=Math.max(maxZ,e.getAsJsonArray("to").get(2).getAsDouble());
        }
        for(String context:List.of("ground","thirdperson_righthand","thirdperson_lefthand")){
            var transform=display.getAsJsonObject(context);double scale=transform.getAsJsonArray("scale").get(2).getAsDouble();
            assertTrue(scale>0&&scale<=(context.equals("ground")?.3:.5),context+" must remain compact");
            // Blockbench omits a zero rotation on export.
            var rotation=transform.has("rotation")?transform.getAsJsonArray("rotation"):JsonParser.parseString("[0,0,0]").getAsJsonArray();
            var translation=transform.getAsJsonArray("translation");
            var center=new org.joml.Vector3f(0,0,(float)((minZ+maxZ)/2-8)).mul((float)scale)
                    .rotateX((float)Math.toRadians(rotation.get(0).getAsDouble()))
                    .rotateY((float)Math.toRadians(rotation.get(1).getAsDouble()))
                    .rotateZ((float)Math.toRadians(rotation.get(2).getAsDouble()))
                    .add(translation.get(0).getAsFloat(),translation.get(1).getAsFloat(),translation.get(2).getAsFloat());
            assertEquals(0,center.x,.1,context+" horizontal offset");assertEquals(0,center.z,.1,context+" depth offset");
            assertTrue(center.y>=1&&center.y<=4,context+" must stay near the hand/item origin");
        }
    }
    @Test void allBorderCombinationsAndLegacyLayoutsHaveModels()throws Exception {
        var rules=JsonParser.parseString(Files.readString(ASSETS.resolve("blockstates/quest_board.json"))).getAsJsonObject().getAsJsonArray("multipart");
        assertEquals(448,rules.size());var modular=new HashSet<String>();int legacy=0;
        for(var rule:rules){
            var r=rule.getAsJsonObject();var when=r.getAsJsonObject("when");
            if(when.get("modular").getAsString().equals("true"))assertTrue(modular.add(when.get("facing").getAsString()+when.get("edges").getAsString()));
            else legacy++;
            String model=r.getAsJsonObject("apply").get("model").getAsString().replace("questplus:","");
            assertTrue(Files.isRegularFile(ASSETS.resolve("models/"+model+".json")),model);
        }
        assertEquals(64,modular.size());assertEquals(384,legacy);
        for(int mask=0;mask<16;mask++){
            var model=JsonParser.parseString(Files.readString(ASSETS.resolve("models/block/quest_board_module_"+mask+".json"))).getAsJsonObject();
            var names=new HashSet<String>();for(var e:model.getAsJsonArray("elements"))names.add(e.getAsJsonObject().get("name").getAsString());
            String[] edges={"n","s","w","e"};
            for(int bit=0;bit<4;bit++)assertEquals((mask&(1<<bit))!=0,names.contains("carved_frame_"+edges[bit]),"Mask "+mask+" edge "+edges[bit]);
            assertTrue(names.contains("slate_surface"));
            // Overlapping cubes share outer faces and z-fight, letting the slate show on the frame's edge.
            JsonObject slate=null;var frames=new ArrayList<JsonObject>();
            for(var e:model.getAsJsonArray("elements")){
                var o=e.getAsJsonObject();String name=o.get("name").getAsString();
                if(name.equals("slate_surface"))slate=o;else if(name.startsWith("carved_frame_"))frames.add(o);
            }
            for(var frame:frames){
                boolean overlaps=true;
                for(int axis=0;axis<3;axis++)overlaps&=Math.max(slate.getAsJsonArray("from").get(axis).getAsDouble(),frame.getAsJsonArray("from").get(axis).getAsDouble())
                        <Math.min(slate.getAsJsonArray("to").get(axis).getAsDouble(),frame.getAsJsonArray("to").get(axis).getAsDouble());
                assertFalse(overlaps,"Mask "+mask+" slate overlaps "+frame.get("name").getAsString());
            }
        }
    }
}
