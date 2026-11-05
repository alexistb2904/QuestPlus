package dev.questplus.clientdata;

import com.google.gson.*;
import dev.questplus.core.*;
import java.util.*;

/** Explicit read-only projection; persistence records and reward extension data stay on the server. */
public final class ClientProjection {
    private static final Gson JSON = new Gson();
    private ClientProjection() {}

    public record Annotation(String id, double x, double y, Map<String,String> text, String style, String color,
                             Map<String,JsonArray> rich, Integer width, Integer height, String align) {
        public Annotation(String id,double x,double y,Map<String,String> text,String style,String color,Map<String,JsonArray> rich,Integer width,Integer height) {
            this(id,x,y,text,style,color,rich,width,height,null);
        }
        public Annotation(String id,double x,double y,Map<String,String> text,String style,String color,Map<String,JsonArray> rich) {
            this(id,x,y,text,style,color,rich,null,null);
        }
        public Annotation(String id,double x,double y,Map<String,String> text,String style,String color) {
            this(id,x,y,text,style,color,Map.of(),null,null);
        }
    }
    public static String annotationAlignment(JsonObject note) {
        if (!note.has("align")) return null;
        JsonElement value=note.get("align");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || !Set.of("left","center","right").contains(value.getAsString()))
            throw new IllegalArgumentException("Invalid annotation alignment");
        return value.getAsString();
    }
    public record RoutePoint(double x,double y) {}
    public record LinkRoute(String from,String to,List<RoutePoint> points) {}
    public record Category(String id, Map<String,String> title, Map<String,String> description, String icon,
                           List<Annotation> annotations, int order, List<String> images, List<LinkRoute> linkRoutes) {
        public Category(String id,Map<String,String> title,Map<String,String> description,String icon,List<Annotation> annotations,int order,List<String> images) {
            this(id,title,description,icon,annotations,order,images,List.of());
        }
        public Category(String id,Map<String,String> title,Map<String,String> description,String icon,List<Annotation> annotations,int order) {
            this(id,title,description,icon,annotations,order,List.of());
        }
        public Category(String id,Map<String,String> title,Map<String,String> description,String icon,List<Annotation> annotations) {
            this(id,title,description,icon,annotations,Integer.MAX_VALUE,List.of());
        }
        public Category(String id,Map<String,String> title,Map<String,String> description,String icon) {
            this(id,title,description,icon,List.of(),Integer.MAX_VALUE,List.of());
        }
    }

    /** Validate author-supplied presentation routes against the complete candidate registry. */
    public static List<LinkRoute> routes(JsonElement value,String category,QuestRegistry registry) {
        if (value==null) return List.of();
        if (!value.isJsonArray() || value.getAsJsonArray().size()>128)
            throw new IllegalArgumentException("linkRoutes must be an array of at most 128 routes in "+category);
        List<LinkRoute> result=new ArrayList<>(); Set<String> pairs=new HashSet<>();
        for (var entry:value.getAsJsonArray()) {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("Invalid link route in "+category);
            var route=entry.getAsJsonObject();
            if (!route.keySet().equals(Set.of("from","to","points")) || !string(route.get("from")) || !string(route.get("to"))
                    || !route.get("points").isJsonArray() || route.getAsJsonArray("points").size()>64)
                throw new IllegalArgumentException("Invalid link route fields in "+category);
            String from=route.get("from").getAsString(),to=route.get("to").getAsString();
            var source=registry.find(from).orElseThrow(() -> new IllegalArgumentException("Unknown route source "+from));
            var target=registry.find(to).orElseThrow(() -> new IllegalArgumentException("Unknown route target "+to));
            Set<String> refs=new HashSet<>(); if (target.requires()!=null) target.requires().questReferences(refs);
            if (!source.category().equals(category) || !target.category().equals(category) || !refs.contains(from) || !pairs.add(from+'\0'+to))
                throw new IllegalArgumentException("Route must name a unique same-category prerequisite: "+from+" -> "+to);
            List<RoutePoint> points=new ArrayList<>();
            for (var point:route.getAsJsonArray("points")) {
                if (!point.isJsonObject() || !point.getAsJsonObject().keySet().equals(Set.of("x","y"))
                        || !number(point.getAsJsonObject().get("x")) || !number(point.getAsJsonObject().get("y")))
                    throw new IllegalArgumentException("Invalid route point: "+from+" -> "+to);
                double x=point.getAsJsonObject().get("x").getAsDouble(),y=point.getAsJsonObject().get("y").getAsDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x)>100000 || Math.abs(y)>100000)
                    throw new IllegalArgumentException("Route point outside -100000..100000: "+from+" -> "+to);
                points.add(new RoutePoint(x,y));
            }
            result.add(new LinkRoute(from,to,List.copyOf(points)));
        }
        return List.copyOf(result);
    }
    private static boolean string(JsonElement value) { return value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); }
    private static boolean number(JsonElement value) { return value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(); }

    public static void validateBackground(JsonObject note,String id) {
        try {
            for (String field:List.of("x","y")) {
                if (!number(note.get(field))) throw new IllegalArgumentException();
                double coordinate=note.get(field).getAsDouble();
                if (!Double.isFinite(coordinate) || Math.abs(coordinate)>100000) throw new IllegalArgumentException();
            }
            for (String field:List.of("width","height")) {
                var value=note.get(field);
                if (!number(value)) throw new IllegalArgumentException();
                int dimension=value.getAsBigDecimal().intValueExact();
                if (dimension<1 || dimension>100000) throw new IllegalArgumentException();
            }
            if (note.has("rich")) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Background requires finite position, integer width/height 1..100000 and no rich text: "+id,invalid);
        }
    }

    /** Hidden quest identifiers must not leak through otherwise visible category metadata. */
    public static JsonArray categories(Collection<Category> categories,Collection<QuestDefinition> visible) {
        Set<String> ids=new HashSet<>(); visible.forEach(q -> ids.add(q.id()));
        JsonArray out=new JsonArray();
        for (Category category:categories) {
            JsonObject data=JSON.toJsonTree(category).getAsJsonObject();
            JsonArray routes=new JsonArray();
            for (LinkRoute route:category.linkRoutes()) if (ids.contains(route.from()) && ids.contains(route.to()))
                routes.add(JSON.toJsonTree(route));
            data.add("linkRoutes",routes); out.add(data);
        }
        return out;
    }

    public static List<QuestDefinition> visible(Collection<QuestDefinition> quests,PlayerProgress state) {
        Set<String> completed=new HashSet<>();
        state.quests.forEach((id,p) -> { if (p.completed) completed.add(id); });
        return quests.stream().filter(q -> !q.hidden() || state.quests.containsKey(q.id())
            || q.requires()==null || q.requires().matches(Map.of(),completed)).toList();
    }

    public static JsonArray quests(Collection<QuestDefinition> quests) {
        JsonArray out = new JsonArray();
        Set<String> visibleIds=new HashSet<>(); quests.forEach(q -> visibleIds.add(q.id()));
        quests.stream().sorted(Comparator.comparing(QuestDefinition::id)).forEach(q -> {
            JsonObject item=JSON.toJsonTree(q).getAsJsonObject();
            if (item.has("requires")) redact(item.getAsJsonObject("requires"),visibleIds);
            stripRewards(item.getAsJsonArray("rewards"));
            for (JsonElement tier:item.getAsJsonArray("tiers")) stripRewards(tier.getAsJsonObject().getAsJsonArray("rewards"));
            out.add(item);
        });
        return out;
    }

    private static void redact(JsonObject condition,Set<String> visibleIds) {
        if (condition.get("operator").getAsString().equals("quest") && !visibleIds.contains(condition.get("value").getAsString())) {
            condition.addProperty("operator","hidden"); condition.addProperty("value","undiscovered");
        }
        for (JsonElement child:condition.getAsJsonArray("children")) redact(child.getAsJsonObject(),visibleIds);
    }

    private static void stripRewards(JsonArray rewards) {
        if (rewards!=null) for (JsonElement reward:rewards) reward.getAsJsonObject().remove("data");
    }

    public static JsonObject progress(Collection<QuestDefinition> quests, QuestEngine engine, PlayerProgress state, PlayerProgress personal) {
        JsonObject result=new JsonObject();
        Set<String> completed=new HashSet<>();
        state.quests.forEach((id,p) -> { if (p.completed) completed.add(id); });
        Set<String> claimable=new HashSet<>();
        personal.entitlements.forEach((key,grant) -> { if (!personal.claims.containsKey(key) && !grant.rewards().isEmpty()) claimable.add(grant.questId()); });
        for (QuestDefinition quest:quests) {
            PlayerProgress.QuestProgress p=state.quests.get(quest.id());
            JsonObject value=new JsonObject();
            value.add("counters",JSON.toJsonTree(p==null ? Map.of() : p.counters));
            value.add("cumulative",JSON.toJsonTree(p==null ? Map.of() : p.cumulative));
            value.addProperty("completed",p!=null && p.completed);
            value.addProperty("cycles",p==null ? 0 : p.cycles);
            value.addProperty("tier",p==null ? 0 : p.tier);
            value.addProperty("waitingRestart",p!=null && p.waitingRestart);
            value.addProperty("terminal",p!=null && p.terminal);
            value.addProperty("claimable",claimable.contains(quest.id()));
            value.addProperty("unlocked",quest.requires()==null || quest.requires().matches(Map.of(),completed));
            engine.diagnostic(state,quest.id()).ifPresent(d -> value.addProperty("diagnostic",d));
            result.add(quest.id(),value);
        }
        return result;
    }
}
