package dev.questplus.core;

import com.google.gson.*;
import de.marhali.json5.Json5;
import de.marhali.json5.Json5Element;
import java.util.Map;

/** JSON5 definitions are converted to the same validated Gson AST as legacy JSON.
 *  This parser never evaluates JavaScript or trusts input from a client.
 */
public final class QuestJson5 {
    private QuestJson5() {}
    public static JsonElement parse(String source) {
        if(source.length()>2_000_000)throw new IllegalArgumentException("Definition exceeds 2 MB");
        return convert(new Json5().parse(source),0);
    }
    private static JsonElement convert(Json5Element value,int depth) {
        if(depth>64)throw new IllegalArgumentException("JSON5 nesting exceeds limit");
        if(value==null || value.isJson5Null())return JsonNull.INSTANCE;
        if(value.isJson5Object()){
            var result=new JsonObject();
            for(Map.Entry<String,Json5Element> e:value.getAsJson5Object().asMap().entrySet())
                result.add(e.getKey(),convert(e.getValue(),depth+1));
            return result;
        }
        if(value.isJson5Array()){
            var result=new JsonArray();
            for(Json5Element e:value.getAsJson5Array())result.add(convert(e,depth+1));
            return result;
        }
        var p=value.getAsJson5Primitive();
        if(p.isString())return new JsonPrimitive(p.getAsString());
        if(p.isBoolean())return new JsonPrimitive(p.getAsBoolean());
        if(p.isNumber()){
            double check=p.getAsDouble();
            if(!Double.isFinite(check))throw new IllegalArgumentException("Non-finite JSON5 number");
            return new JsonPrimitive(p.getAsBigDecimal());
        }
        throw new IllegalArgumentException("Unsupported JSON5 value");
    }
}
