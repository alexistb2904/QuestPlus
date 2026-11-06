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
                           List<Annotation> annotations, int order, List<String> images, List<LinkRoute> linkRoutes, String group) {
        public Category(String id,Map<String,String> title,Map<String,String> description,String icon,List<Annotation> annotations,int order,List<String> images,List<LinkRoute> routes) {
            this(id,title,description,icon,annotations,order,images,routes,"");
        }
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
        Set<String> completed=QuestEngine.prerequisiteState(state);
        var visible=new ArrayList<>(quests.stream().filter(q -> {
            var p=state.quests.get(q.id());var settings=q.presentation();
            if(settings.invisibleUntilCompleted() && (p==null||!p.completed))return false;
            if(settings.invisibleUntilTasks()>0 && (p==null||q.objectives().stream().filter(o->p.counters.getOrDefault(o.id(),0L)>=o.count()).count()<settings.invisibleUntilTasks()))return false;
            return !q.hidden() || p!=null || q.requires()==null || q.requires().matches(Map.of(),completed);
        }).toList());
        boolean changed;
        do {
            Set<String> ids=new HashSet<>();visible.forEach(q->ids.add(q.id()));
            changed=visible.removeIf(q->{if(!q.presentation().hideUntilDependenciesVisible()||q.requires()==null)return false;Set<String> references=new HashSet<>();q.requires().questReferences(references);return !ids.containsAll(references);});
        }while(changed);
        return List.copyOf(visible);
    }

    /**
     * Only these transitions change the quest definition bytes sent to a player.
     * Counter updates can use a small progress delta without resending all quests.
     */
    public static Map<String,Integer> redactionFlags(Collection<QuestDefinition> quests,PlayerProgress state) {
        Set<String> completed=QuestEngine.prerequisiteState(state);
        Map<String,Integer> flags=new HashMap<>();
        for(QuestDefinition q:quests) {
            var settings=q.presentation();
            if(!settings.hideTextUntilCompleted()&&!settings.hideDetailsUntilUnlocked())continue;
            var progress=state.quests.get(q.id());
            boolean done=progress!=null&&progress.completed;
            boolean unlocked=q.requires()==null||q.requires().matches(Map.of(),completed);
            int mask=(settings.hideTextUntilCompleted()&&!done?1:0) |
                (settings.hideDetailsUntilUnlocked()&&!unlocked?2:0);
            flags.put(q.id(),mask);
        }
        return Map.copyOf(flags);
    }

    public static JsonArray quests(Collection<QuestDefinition> quests) {return quests(quests,new PlayerProgress());}
    public static JsonArray quests(Collection<QuestDefinition> quests,PlayerProgress state) {
        JsonArray out = new JsonArray();
        Set<String> visibleIds=new HashSet<>(); quests.forEach(q -> visibleIds.add(q.id()));
        Set<String> completedIds=QuestEngine.prerequisiteState(state);
        quests.stream().sorted(Comparator.comparing(QuestDefinition::id)).forEach(q -> {
            JsonObject item=JSON.toJsonTree(q).getAsJsonObject();
            if (item.has("requires")) redact(item.getAsJsonObject("requires"),visibleIds);
            for(var objective:item.getAsJsonArray("objectives"))objective.getAsJsonObject().remove("data");
            stripRewards(item.getAsJsonArray("rewards"));
            for (JsonElement tier:item.getAsJsonArray("tiers")) stripRewards(tier.getAsJsonObject().getAsJsonArray("rewards"));
            item.add("rewardPreview",rewards(q.rewards()));
            JsonArray tierPreviews=new JsonArray();
            for(var tier:q.tiers())tierPreviews.add(rewards(tier.rewards()));
            item.add("tierRewardPreviews",tierPreviews);
            var p=state.quests.get(q.id());boolean completed=p!=null&&p.completed;
            boolean unlocked=q.requires()==null||q.requires().matches(Map.of(),completedIds);
            var settings=q.presentation();
            if(settings.hideTextUntilCompleted()&&!completed || settings.hideDetailsUntilUnlocked()&&!unlocked) {
                for(String field:List.of("description","descriptionRich","narrative"))item.add(field,new JsonObject());
            }
            if(settings.hideDetailsUntilUnlocked()&&!unlocked)for(String field:List.of("objectives","rewards","tiers","rewardPreview","tierRewardPreviews"))item.add(field,new JsonArray());
            out.add(item);
        });
        return out;
    }

    private static void redact(JsonObject condition,Set<String> visibleIds) {
        if (Set.of("quest","started").contains(condition.get("operator").getAsString()) && !visibleIds.contains(condition.get("value").getAsString())) {
            condition.addProperty("operator","hidden"); condition.addProperty("value","undiscovered");
        }
        for (JsonElement child:condition.getAsJsonArray("children")) redact(child.getAsJsonObject(),visibleIds);
    }

    private static void stripRewards(JsonArray rewards) {
        if (rewards!=null) {
            rewards.asList().removeIf(r->r.getAsJsonObject().getAsJsonObject("policy").get("autoClaim").getAsString().equals("invisible"));
            for (JsonElement reward:rewards) {reward.getAsJsonObject().remove("data");reward.getAsJsonObject().remove("policy");}
        }
    }

    /** Allowlisted presentation only: never copy an extension payload or a command. */
    public static JsonArray rewards(List<QuestDefinition.Reward> rewards) {
        JsonArray out=new JsonArray();
        for(var reward:rewards) {
            if(reward.policy().autoClaim().equals("invisible"))continue;
            JsonObject value=new JsonObject();
            value.addProperty("type",reward.type());
            value.add("text",JSON.toJsonTree(reward.text()));
            if(reward.type().equals("item")) {
                value.addProperty("item",reward.item()); value.addProperty("count",reward.count());
            } else if(Set.of("experience","experience_levels").contains(reward.type()))value.addProperty("amount",reward.amount());
            else if(Set.of("questplus:advancement","questplus:message").contains(reward.type())) {
                String field=reward.type().equals("questplus:advancement")?"advancement":"message";
                value.add(field,JsonParser.parseString(reward.data()).getAsJsonObject().get(field));
            }
            else if(Set.of("questplus:stage","questplus:toast").contains(reward.type())) {
                var data=JsonParser.parseString(reward.data()).getAsJsonObject();
                for(String field:reward.type().equals("questplus:stage")?List.of("stage","remove"):List.of("title","description","icon"))value.add(field,data.get(field));
            }
            else if(Set.of("questplus:choice","questplus:random","questplus:loot","questplus:all").contains(reward.type())) {
                var data=JsonParser.parseString(reward.data()).getAsJsonObject();
                var options=new JsonArray();
                for(var entry:data.getAsJsonArray("options")) {
                    var option=entry.getAsJsonObject();
                    var safe=new JsonObject();
                    for(String key:List.of("type","item","count","amount","weight","text"))
                        if(option.has(key))safe.add(key,option.get(key));
                    String type=option.get("type").getAsString();
                    if(Set.of("questplus:advancement","questplus:message").contains(type)) {
                        String field=type.equals("questplus:advancement")?"advancement":"message";
                        safe.add(field,option.getAsJsonObject("data").get(field));
                    }
                    if(Set.of("questplus:stage","questplus:toast").contains(type)) {
                        var payload=option.getAsJsonObject("data");
                        for(String field:type.equals("questplus:stage")?List.of("stage","remove"):List.of("title","description","icon"))safe.add(field,payload.get(field));
                    }
                    options.add(safe);
                }
                value.add("options",options);
                for(String key:List.of("rolls","emptyWeight","description"))if(data.has(key))value.add(key,data.get(key));
            }
            out.add(value);
        }
        return out;
    }
    public static String rewardToken(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    public static JsonObject progress(Collection<QuestDefinition> quests, QuestEngine engine, PlayerProgress state, PlayerProgress personal) {
        JsonObject result=new JsonObject();
        Set<String> completed=QuestEngine.prerequisiteState(state);
        Set<String> claimableAny=new HashSet<>();
        Map<String,Map.Entry<String,PlayerProgress.RewardGrant>> pending=new HashMap<>(),receipts=new HashMap<>();
        personal.entitlements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if(!entry.getValue().rewards().isEmpty()&&entry.getValue().rewards().stream().allMatch(r->r.policy().autoClaim().equals("invisible")))return;
            String status=personal.claims.get(entry.getKey());
            if(status==null && !entry.getValue().rewards().isEmpty() && entry.getValue().rewards().stream().noneMatch(r->r.type().equals("questplus:choice")))claimableAny.add(entry.getValue().questId());
            if(status==null || status.equals("reserved")) {
                var current=pending.get(entry.getValue().questId());
                if(current==null || current.getValue().rewards().isEmpty())pending.put(entry.getValue().questId(),entry);
            }
            else receipts.put(entry.getValue().questId(),entry);
        });
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
            var rewardEntry=pending.getOrDefault(quest.id(),receipts.get(quest.id()));
            boolean claimable=false;
            if(rewardEntry!=null) {
                var grant=rewardEntry.getValue();
                boolean choice=grant.rewards().stream().anyMatch(r->r.type().equals("questplus:choice"));
                String status=personal.claims.get(rewardEntry.getKey());
                if(status==null)status=choice?"choice":grant.rewards().isEmpty()?"empty":"ready";
                claimable=status.equals("ready");
                var rewardState=new JsonObject();
                rewardState.addProperty("token",rewardToken(rewardEntry.getKey()));
                rewardState.addProperty("status",status);
                rewardState.add("rewards",rewards(grant.rewards()));
                value.add("rewardState",rewardState);
            }
            value.addProperty("claimable",claimable);
            value.addProperty("claimableAny",claimableAny.contains(quest.id()));
            value.addProperty("unlocked",quest.requires()==null || quest.requires().matches(Map.of(),completed));
            engine.diagnostic(state,quest.id()).ifPresent(d -> value.addProperty("diagnostic",d));
            result.add(quest.id(),value);
        }
        return result;
    }
}
