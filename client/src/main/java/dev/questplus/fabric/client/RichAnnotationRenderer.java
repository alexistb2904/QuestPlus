package dev.questplus.fabric.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import dev.questplus.fabric.client.ui.QuestSkin;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * JSON-rich annotations are rendered by Minecraft itself; no HTML, network fetches or external
 * hosting. Media is bounded, decoded on the client, and cached as native GPU textures.
 */
final class RichAnnotationRenderer {
    private static final int MAX_CACHE=128;
    private static final LinkedHashMap<String,AnimatedImage> MEDIA=new LinkedHashMap<>(32,.75f,true);
    private record Frame(DynamicTexture texture,int delay) {}
    private record AnimatedImage(List<Frame> frames,int cycle) {
        DynamicTexture current() {
            if(frames.size()==1)return frames.getFirst().texture();
            int elapsed=(int)((System.currentTimeMillis()/10)%Math.max(1,cycle));
            for(var frame:frames){elapsed-=frame.delay();if(elapsed<0)return frame.texture();}
            return frames.getLast().texture();
        }
    }
    private record Part(Component component,DynamicTexture image,int width,int height,float scale,int color,int highlight) {}
    private static final class Line {
        final List<Part> parts=new ArrayList<>();
        int width, height=11;
        void add(Part p){parts.add(p);width+=p.width();height=Math.max(height,p.height());}
    }
    private RichAnnotationRenderer(){}

    static boolean render(GuiGraphicsExtractor g,Font font,JsonObject note,JsonArray pool,String language,int x,int y){
        int requestedWidth=Math.max(1,note.has("width")?note.get("width").getAsInt():170);
        int requestedHeight=Math.max(1,note.has("height")?note.get("height").getAsInt():300);
        int contentWidth=requestedWidth-12;
        if(!note.has("rich")||!note.get("rich").isJsonObject())return false;
        JsonObject locales=note.getAsJsonObject("rich");
        // Older imported annotations may contain only fr_fr rich content while en_us
        // still has the studio placeholder "Note": use the authored rich entry.
        // Empty arrays must not mask a usable translation in another language.
        JsonElement raw=nonEmptyRich(locales,language);
        if(raw==null)raw=nonEmptyRich(locales,"en_us");
        if(raw==null)for(String locale:locales.keySet().stream().sorted().toList()) {
            raw=nonEmptyRich(locales,locale);
            if(raw!=null)break;
        }
        String style=note.has("style")?note.get("style").getAsString():"note";
        if(raw==null||!raw.isJsonArray())return false;
        JsonArray arr=raw.getAsJsonArray();
        
        List<Line> lines=new ArrayList<>();Line line=new Line();lines.add(line);
        for(var entry:arr){
            if(!entry.isJsonObject())continue;
            JsonObject p=entry.getAsJsonObject();
            if(!p.has("type"))continue;
            if(p.get("type").getAsString().equals("image")){
                String uri=imageData(p,pool);
                AnimatedImage image=uri==null?null:image(uri);
                if(image==null)continue;
                int sourceW=p.get("width").getAsInt(),sourceH=p.get("height").getAsInt();
                double fit=Math.min(1d,Math.min(contentWidth/(double)Math.max(1,sourceW),(requestedHeight-8d)/Math.max(1,sourceH)));
                int w=Math.max(8,(int)Math.round(sourceW*fit)),h=Math.max(8,(int)Math.round(sourceH*fit));
                if(!line.parts.isEmpty()){line=new Line();lines.add(line);}
                line.add(new Part(null,image.current(),w,h,1,0,0));
                line=new Line();lines.add(line);
            }else if(p.get("type").getAsString().equals("text")){
                String s=p.get("text").getAsString();
                
                float scale=p.has("sizePx")?Math.max(.01f,p.get("sizePx").getAsFloat()/9f)
                    :p.has("size")?Math.clamp(p.get("size").getAsFloat(),.75f,2f):1f;
                if (style.equals("heading")) scale=Math.min(2f,scale*1.45f);
                else if (style.equals("label")) scale=Math.max(.75f,scale*.9f);
                String face=p.has("font")?p.get("font").getAsString():"default";
                if(!Set.of("default","uniform","alt","illageralt").contains(face))face="default";
                Style st=Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("minecraft",face)))
                        .withBold(style.equals("heading")||bool(p,"bold")).withItalic(bool(p,"italic"))
                        .withUnderlined(bool(p,"underline")).withStrikethrough(bool(p,"strike"));
                int color=p.has("color")?hex(p.get("color").getAsString(),0xffe3cdb1):0xffe3cdb1;
                int highlight=p.has("highlight")?hex(p.get("highlight").getAsString(),0):0;
                // Split at word boundaries, retaining whitespace. Oversized tokens break per glyph.
                for(String token:s.split("(?<=\\s)|(?=\\s)",-1)){
                    if(token.isEmpty())continue;
                    if(token.equals("\n")){line=new Line();lines.add(line);continue;}
                    String pending=token;
                    while(!pending.isEmpty()){
                        int available=contentWidth-line.width;
                        if(available<=2){line=new Line();lines.add(line);available=contentWidth;}
                        int length=pending.length();
                        Component whole=Component.literal(pending).withStyle(st);
                        int width=(int)Math.ceil(font.width(whole)*scale);
                        if(width>available){
                            if(!line.parts.isEmpty()&&pending.length()>1&&!Character.isWhitespace(pending.charAt(0))){
                                line=new Line();lines.add(line);continue;
                            }
                            length=1;
                            while(length<pending.length()){
                                int w=(int)Math.ceil(font.width(Component.literal(pending.substring(0,length+1)).withStyle(st))*scale);
                                if(w>available)break;
                                length++;
                            }
                        }
                        String piece=pending.substring(0,length);
                        Component component=Component.literal(piece).withStyle(st);
                        int sw=(int)Math.ceil(font.width(component)*scale);
                        line.add(new Part(component,null,sw,Math.max(11,(int)Math.ceil(10*scale)),scale,color,highlight));
                        pending=pending.substring(length);
                    }
                }
            }
        }
        if(lines.size()>1&&lines.getLast().parts.isEmpty())lines.removeLast();
        int measuredHeight=lines.stream().mapToInt(l->l.height).sum()+10;
        int total=note.has("height")?Math.max(measuredHeight,requestedHeight):measuredHeight;
        int border=hex(note.has("color")?note.get("color").getAsString():"#c6a986",0xffc6a986);
        int w=note.has("width")?requestedWidth:Math.max(38,lines.stream().mapToInt(l->l.width).max().orElse(28)+12);
        int top=y-total/2, left=x-w/2;
        if (style.equals("heading")) {
            g.fill(left,top-3,left+w,top-2,border);
            g.fill(left,top+total+1,left+w,top+total+2,border);
        } else if (style.equals("note")) QuestSkin.panel(g,left,top,w,total,0xdd33271f,border);
        int cy=top+5+(total-measuredHeight)/2;
        for(var row:lines){
            String align=note.has("align")?note.get("align").getAsString():"center";
            int cx=switch(align){case "left" -> left+6; case "right" -> left+w-6-row.width; default -> x-row.width/2;};
            for(var part:row.parts){
                if(part.image()!=null){
                    g.blit(part.image().getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                        cx,cy,cx+part.width(),cy+part.height(),0,1,0,1);
                }else if(part.component()!=null){
                    if(part.highlight()!=0)g.fill(cx,cy,cx+part.width(),cy+part.height(),part.highlight()|0xff000000);
                    g.pose().pushMatrix();g.pose().translate(cx,cy);g.pose().scale(part.scale());
                    g.text(font,part.component(),0,0,part.color(),false);g.pose().popMatrix();
                }
                cx+=part.width();
            }
            cy+=row.height;
        }
        return true;
    }
    private static String imageData(JsonObject part,JsonArray pool){
        if(part.has("data"))return part.get("data").getAsString();
        if(part.has("ref") && pool!=null){
            int ref=part.get("ref").getAsInt();
            if(ref>=0&&ref<pool.size())return pool.get(ref).getAsString();
        }
        return null;
    }
    private static JsonElement nonEmptyRich(JsonObject locales,String language) {
        if(!locales.has(language))return null;
        JsonElement value=locales.get(language);
        if(!value.isJsonArray())return null;
        for(JsonElement part:value.getAsJsonArray()) {
            if(!part.isJsonObject())continue;
            JsonObject node=part.getAsJsonObject();
            if(!node.has("type"))continue;
            String type=node.get("type").getAsString();
            if(type.equals("image")&&(node.has("ref") || node.has("data")&&!node.get("data").getAsString().isBlank()))return value;
            if(type.equals("text")&&node.has("text")&&!node.get("text").getAsString().isBlank())return value;
        }
        return null;
    }

    private static boolean bool(JsonObject obj,String key){return obj.has(key)&&obj.get(key).getAsBoolean();}
    static boolean drawIcon(GuiGraphicsExtractor graphics,String data,int x,int y,int size) {
        if(data==null||!data.startsWith("data:image/png;base64,"))return false;
        AnimatedImage decoded=image(data);if(decoded==null)return false;
        graphics.blit(decoded.current().getTextureView(),RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),x,y,x+size,y+size,0,1,0,1);
        return true;
    }
    private static int hex(String value,int fallback){
        return value.matches("#[0-9a-fA-F]{6}")?0xff000000|Integer.parseInt(value.substring(1),16):fallback;
    }
    private static synchronized AnimatedImage image(String data){
        AnimatedImage cached=MEDIA.get(data);
        if(cached!=null)return cached;
        // Image cache uses LRU eviction instead of refusing the next asset.
        if(!data.matches("data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/]+={0,2}"))return null;
        try{
            byte[] bytes=Base64.getDecoder().decode(data.substring(data.indexOf(',')+1));
            
            List<Frame> frames=new ArrayList<>();int cycle=0;
            try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext())return null;
                ImageReader reader=readers.next();
                try{
                    reader.setInput(input,false,false);
                    int count=Math.min(32,reader.getNumImages(true));
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1||height<1)return null;
                    int tw=width,th=height;
                    BufferedImage composite=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    for(int i=0;i<count;i++){
                        BufferedImage part=reader.read(i);
                        IIOMetadata metadata=reader.getImageMetadata(i);
                        int offsetX=0,offsetY=0,delay=10;
                        String disposal="none";
                        if(metadata!=null&&metadata.isStandardMetadataFormatSupported()){
                            // ImageIO GIF reader exposes its GIF-specific metadata tree below.
                        }
                        if(metadata!=null){
                            try{
                                Node root=metadata.getAsTree("javax_imageio_gif_image_1.0");
                                Node child=root.getFirstChild();
                                while(child!=null){
                                    if(child.getNodeName().equals("ImageDescriptor")){
                                        var at=child.getAttributes();
                                        offsetX=Integer.parseInt(at.getNamedItem("imageLeftPosition").getNodeValue());
                                        offsetY=Integer.parseInt(at.getNamedItem("imageTopPosition").getNodeValue());
                                    }
                                    if(child.getNodeName().equals("GraphicControlExtension")){
                                        var at=child.getAttributes();
                                        delay=Math.max(2,Integer.parseInt(at.getNamedItem("delayTime").getNodeValue()));
                                        disposal=at.getNamedItem("disposalMethod").getNodeValue();
                                    }
                                    child=child.getNextSibling();
                                }
                            }catch(Exception ignored){}
                        }
                        BufferedImage old=null;
                        if(disposal.equals("restoreToPrevious")){
                            old=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                            Graphics2D backup=old.createGraphics();backup.drawImage(composite,0,0,null);backup.dispose();
                        }
                        Graphics2D graphics=composite.createGraphics();
                        graphics.drawImage(part,offsetX,offsetY,null);graphics.dispose();
                        NativeImage nativeImage=new NativeImage(tw,th,true);
                        for(int yy=0;yy<th;yy++)for(int xx=0;xx<tw;xx++)
                            nativeImage.setPixel(xx,yy,composite.getRGB(xx*width/tw,yy*height/th));
                        DynamicTexture texture=new DynamicTexture(()->"QuestPlus rich annotation",nativeImage);
                        frames.add(new Frame(texture,delay));cycle+=delay;
                        if(disposal.equals("restoreToBackgroundColor")){
                            graphics=composite.createGraphics();
                            graphics.setComposite(java.awt.AlphaComposite.Clear);
                            graphics.fillRect(offsetX,offsetY,part.getWidth(),part.getHeight());
                            graphics.dispose();
                        }else if(old!=null)composite=old;
                    }
                }finally{reader.dispose();}
            }
            if(frames.isEmpty())return null;
            AnimatedImage media=new AnimatedImage(List.copyOf(frames),cycle);
            MEDIA.put(data,media);
            if(MEDIA.size()>MAX_CACHE){
                var iterator=MEDIA.entrySet().iterator();
                if(iterator.hasNext()){
                    var old=iterator.next();
                    iterator.remove();
                    for(var frame:old.getValue().frames())frame.texture().close();
                }
            }
            return media;
        }catch(Exception ex){return null;}
    }
}
