package dev.questplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class QuestIconTest {
 @TempDir Path root;
 private String png(int width,int height) throws Exception {
  var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),"png",out);
  return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
 }
 @Test void acceptsSmallPngAndRejectsInvalidOrOversizedImages() throws Exception {
  assertDoesNotThrow(()->QuestIcon.validateImage(png(16,16)));
  assertThrows(IllegalArgumentException.class,()->QuestIcon.validateImage(png(129,16)));
  assertThrows(IllegalArgumentException.class,()->QuestIcon.validateImage("data:image/png;base64,AAAA"));
  assertThrows(IllegalArgumentException.class,()->QuestIcon.validateImage("data:image/svg+xml;base64,AAAA"));
 }
 @Test void imageIconSurvivesRegistryAndProjection() throws Exception {
  String icon=png(16,16);
  Files.writeString(root.resolve("quest.json"),"{\"schemaVersion\":2,\"id\":\"questplus:a\",\"category\":\"questplus:c\",\"icon\":\""+icon+"\",\"title\":{\"en_us\":\"A\"},\"objectives\":[{\"id\":\"step\",\"event\":\"block_break\",\"target\":\"minecraft:stone\",\"count\":1}],\"rewards\":[],\"progression\":\"once\"}");
  var registry=QuestRegistry.load(root,(kind,id)->{if(kind.equals("icon"))fail("Images are not item IDs");});
  var quest=registry.find("questplus:a").orElseThrow();
  assertEquals(icon,quest.icon());
  assertEquals(icon,dev.questplus.clientdata.ClientProjection.quests(java.util.List.of(quest)).get(0).getAsJsonObject().get("icon").getAsString());
 }
}
