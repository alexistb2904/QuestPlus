package dev.questplus.fabric;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Bounded, read-only previews from assets shipped with installed mods. */
final class StudioRegistryAssets {
    private static final int MAX_JSON=1_000_000, MAX_TEXTURE=24_000, MAX_TOTAL=700_000;
    private static final List<String> LANGUAGES=List.of("en_us","fr_fr","es_es","zh_cn");
    private final List<Path> roots;
    private int remaining=MAX_TOTAL;
    private boolean limited;
    StudioRegistryAssets(List<Path> roots) { this.roots=List.copyOf(roots); }

    JsonObject collect(Map<String,Set<String>> registries) {
        JsonObject names=new JsonObject(), textures=new JsonObject();
        Set<String> ids=new TreeSet<>();registries.values().forEach(ids::addAll);
        Map<String,Map<String,JsonObject>> languages=new HashMap<>();
        for(String id:ids){
            String[] parts=id.split(":",2);
            if(parts.length!=2 || parts[0].equals("minecraft") || !safe(parts[0]) || !safe(parts[1]))continue;
            String ns=parts[0],path=parts[1];
            Map<String,JsonObject> entries=languages.computeIfAbsent(ns,key->{
                Map<String,JsonObject> result=new HashMap<>();
                for(String locale:LANGUAGES)result.put(locale,json("assets/"+key+"/lang/"+locale+".json"));
                return result;
            });
            JsonObject translated=new JsonObject();
            for(String locale:LANGUAGES){
                JsonObject source=entries.get(locale);
                for(String prefix:List.of("item","block","entity","biome","enchantment","effect")){
                    String key=prefix+"."+ns+"."+path;
                    if(source.has(key)&&source.get(key).isJsonPrimitive()&&source.get(key).getAsJsonPrimitive().isString()){
                        String value=source.get(key).getAsString();
                        if(value.length()<=160 && remaining>=value.length()*3){translated.addProperty(locale,value);remaining-=value.length()*3;}
                        else if(value.length()<=160)limited=true;
                        break;
                    }
                }
            }
            if(!translated.isEmpty())names.add(id,translated);
            if(remaining<=0){limited=true;continue;}
            if(!(registries.getOrDefault("items",Set.of()).contains(id)||registries.getOrDefault("blocks",Set.of()).contains(id)))continue;
            String model=findModel(json("assets/"+ns+"/items/"+path+".json"));
            byte[] image=texture(model==null?ns+":item/"+path:model,0,new HashSet<>());
            if(image==null)image=read("assets/"+ns+"/textures/item/"+path+".png",MAX_TEXTURE);
            if(image==null)image=read("assets/"+ns+"/textures/block/"+path+".png",MAX_TEXTURE);
            if(image!=null && isPng(image)){
                String data="data:image/png;base64,"+Base64.getEncoder().encodeToString(image);
                if(data.length()<=remaining){textures.addProperty(id,data);remaining-=data.length();}else limited=true;
            }
        }
        JsonObject result=new JsonObject();result.add("names",names);result.add("textures",textures);
        result.addProperty("limited",limited);return result;
    }
    private static boolean safe(String value){return value.matches("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*")&&!Arrays.asList(value.split("/")).contains("..");}
    private static boolean isPng(byte[] b){return b.length>=8 && b[0]==(byte)0x89 && b[1]==0x50 && b[2]==0x4e && b[3]==0x47 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;}
    private String findModel(JsonElement e){
        if(e==null)return null;
        if(e.isJsonObject()){
            JsonObject o=e.getAsJsonObject();
            if(o.has("model")&&o.get("model").isJsonPrimitive()&&o.get("model").getAsJsonPrimitive().isString())return o.get("model").getAsString();
            for(var child:o.entrySet()){String found=findModel(child.getValue());if(found!=null)return found;}
        }else if(e.isJsonArray())for(var child:e.getAsJsonArray()){String found=findModel(child);if(found!=null)return found;}
        return null;
    }
    private byte[] texture(String model,int depth,Set<String> seen){
        if(model==null||depth>12||!seen.add(model))return null;
        String[] p=model.contains(":")?model.split(":",2):new String[]{"minecraft",model};
        if(p.length!=2||!safe(p[0])||!safe(p[1]))return null;
        JsonObject o=json("assets/"+p[0]+"/models/"+p[1]+".json");
        if(o.has("textures")&&o.get("textures").isJsonObject()){
            JsonObject t=o.getAsJsonObject("textures");
            for(String key:List.of("layer0","particle","all","side","end","texture","top","front","bottom")){
                if(!t.has(key)||!t.get(key).isJsonPrimitive())continue;
                String value=t.get(key).getAsString();Set<String> aliases=new HashSet<>();
                while(value.startsWith("#")&&aliases.add(value)&&t.has(value.substring(1)))value=t.get(value.substring(1)).getAsString();
                String[] q=value.contains(":")?value.split(":",2):new String[]{p[0],value};
                if(q.length==2&&safe(q[0])&&safe(q[1])){byte[] bytes=read("assets/"+q[0]+"/textures/"+q[1]+".png",MAX_TEXTURE);if(bytes!=null&&isPng(bytes))return bytes;}
            }
        }
        return o.has("parent")&&o.get("parent").isJsonPrimitive()?texture(o.get("parent").getAsString(),depth+1,seen):null;
    }
    private JsonObject json(String path){
        byte[] bytes=read(path,MAX_JSON);if(bytes==null)return new JsonObject();
        try{JsonElement parsed=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));return parsed.isJsonObject()?parsed.getAsJsonObject():new JsonObject();}
        catch(RuntimeException ignored){return new JsonObject();}
    }
    private byte[] read(String name,int max){
        if(!name.startsWith("assets/")||name.contains("../")||name.contains("\\"))return null;
        for(Path root:roots){Path file=root.resolve(name).normalize();
            try{if(file.startsWith(root)&&Files.isRegularFile(file)&&Files.size(file)<=max){try(InputStream in=Files.newInputStream(file)){byte[] b=in.readNBytes(max+1);if(b.length<=max)return b;}}}
            catch(IOException|SecurityException ignored){}
        }
        return null;
    }
}
