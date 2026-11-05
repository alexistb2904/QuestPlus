package dev.questplus.core;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.*;

public final class QuestRegistry {
    @FunctionalInterface public interface ValidationHook { void validate(String kind, String id); }
    public record Candidate(QuestDefinition quest, QuestDefinition.Objective objective) {}
    private final Map<String,QuestDefinition> quests;
    private final Map<String,List<Candidate>> index;
    private final Map<String,List<QuestDefinition.Objective>> byEvent;
    private QuestRegistry(Map<String,QuestDefinition> quests) {
        this.quests = Map.copyOf(quests);
        Map<String,List<Candidate>> index = new HashMap<>();
        quests.values().forEach(q -> q.objectives().forEach(o -> index.computeIfAbsent(o.event() + "\0" + o.target(), k -> new ArrayList<>()).add(new Candidate(q,o))));
        index.replaceAll((k,v) -> List.copyOf(v)); this.index = Map.copyOf(index);
        Map<String,List<QuestDefinition.Objective>> grouped=new HashMap<>();
        quests.values().forEach(q -> q.objectives().forEach(o -> grouped.computeIfAbsent(o.event(),ignored -> new ArrayList<>()).add(o)));
        grouped.replaceAll((key,value) -> List.copyOf(value)); byEvent=Map.copyOf(grouped);
    }
    public Optional<QuestDefinition> find(String id) { return Optional.ofNullable(quests.get(id)); }
    public QuestDefinition get(String id) { var q = quests.get(id); if (q == null) throw new IllegalArgumentException("Unknown quest: " + id); return q; }
    public Collection<QuestDefinition> all() { return quests.values(); }
    public boolean hasEvent(String event) { return byEvent.containsKey(event); }
    public List<QuestDefinition.Objective> objectives(String event) {
        return byEvent.getOrDefault(event,List.of());
    }
    public List<Candidate> candidates(QuestEvent event) {
        List<Candidate> result = new ArrayList<>(index.getOrDefault(event.type() + "\0" + event.target(),List.of()));
        for (String tag : event.tags()) result.addAll(index.getOrDefault(event.type() + "\0#" + tag,List.of()));
        return List.copyOf(result);
    }
    public static QuestRegistry load(Path dir, ValidationHook hook) throws IOException {
        Objects.requireNonNull(hook); Map<String,QuestDefinition> quests = new LinkedHashMap<>();
        var tables=NamedRewardTables.load(dir);
        if (Files.exists(dir)) try (var paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")||p.toString().endsWith(".json5")).sorted().toList()) {
                try {
                    JsonElement root;
                    if(path.toString().endsWith(".json5"))
                        root = QuestJson5.parse(Files.readString(path, StandardCharsets.UTF_8));
                    else try (JsonReader reader = new JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
                        reader.setStrictness(Strictness.STRICT);
                        root = readJson(reader, 0);
                        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
                    }
                    var q = parse(tables.quest(root.getAsJsonObject()), hook);
                    if (quests.putIfAbsent(q.id(), q) != null) throw new IllegalArgumentException("Duplicate quest " + q.id());
                } catch (RuntimeException e) { throw new IllegalArgumentException(path + ": " + e.getMessage(), e); }
            }
        }
        Map<String,Integer> states = new HashMap<>(); for (String id : quests.keySet()) visit(id, quests, states);
        return new QuestRegistry(quests);
    }
    public static JsonElement parseStrict(Reader input) throws IOException {
        var reader = new JsonReader(input); reader.setStrictness(Strictness.STRICT);
        JsonElement result = readJson(reader, 0);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
        return result;
    }
    private static JsonElement readJson(JsonReader reader, int depth) throws IOException {
        if (depth > 64) throw new IllegalArgumentException("JSON nesting exceeds limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IllegalArgumentException("Duplicate JSON key: " + name);
                    object.add(name, readJson(reader, depth + 1));
                }
                reader.endObject(); yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) array.add(readJson(reader, depth + 1));
                reader.endArray(); yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new java.math.BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected JSON token");
        };
    }
    private static void visit(String id, Map<String,QuestDefinition> quests, Map<String,Integer> states) {
        if (!quests.containsKey(id)) throw new IllegalArgumentException("Unknown quest reference: " + id);
        if (states.getOrDefault(id,0) == 1) throw new IllegalArgumentException("Quest dependency cycle: " + id);
        if (states.getOrDefault(id,0) == 2) return;
        states.put(id,1); var refs = new HashSet<String>(); var req = quests.get(id).requires(); if (req != null) req.questReferences(refs);
        refs.forEach(ref -> {
            var prerequisite = quests.get(ref);
            if (prerequisite != null && prerequisite.kind().equals("information") && prerequisite.acknowledgement().equals("read_only"))
                throw new IllegalArgumentException("Read-only information quest cannot be a prerequisite: " + ref);
            visit(ref,quests,states);
        }); states.put(id,2);
    }
    private static QuestDefinition parse(JsonObject o, ValidationHook hook) {
        fields(o,"schemaVersion","id","category","icon","title","description","objectives","requires","rewards","progression","layout","narrative",
            "objectiveMode","objectiveThreshold","progressBeforeUnlock","tiers","afterLast","cooldownSeconds","timeLimitSeconds","noDeath","kind","acknowledgement","hidden","size","descriptionRich","presentation");
        long version = integer(o,"schemaVersion",1,2);
        String id = identifier(string(o,"id")), category = identifier(string(o,"category")), icon = identifier(string(o,"icon")); hook.validate("icon",icon);
        var title = translations(object(o,"title")); var description = o.has("description") ? translations(object(o,"description")) : Map.<String,String>of();
        String progression=string(o,"progression");
        if (!Set.of("once","repeatable","infinite","tiered").contains(progression) || (version==1 && !progression.equals("once"))) throw new IllegalArgumentException("Invalid progression/schema version");
        String mode=o.has("objectiveMode") ? string(o,"objectiveMode") : "all";
        if (!Set.of("all","any","sequence","at_least").contains(mode)) throw new IllegalArgumentException("Invalid objectiveMode");
        int objectiveThreshold=o.has("objectiveThreshold") ? (int)integer(o,"objectiveThreshold",1,256) : 1;
        if(o.has("objectiveThreshold") && !mode.equals("at_least"))throw new IllegalArgumentException("objectiveThreshold requires at_least mode");
        long cooldown=o.has("cooldownSeconds") ? integer(o,"cooldownSeconds",0,31_536_000) : 0;
        long timeLimit=o.has("timeLimitSeconds") ? integer(o,"timeLimitSeconds",1,31_536_000) : 0;
        boolean noDeath=o.has("noDeath") && bool(o,"noDeath");
        boolean progressBeforeUnlock=o.has("progressBeforeUnlock") && bool(o,"progressBeforeUnlock");
        String kind=o.has("kind") ? string(o,"kind") : "standard";
        if (!Set.of("standard","information").contains(kind)) throw new IllegalArgumentException("Invalid quest kind");
        String acknowledgement=o.has("acknowledgement") ? string(o,"acknowledgement") : "manual";
        if (!Set.of("manual","read_only").contains(acknowledgement) || (o.has("acknowledgement") && !kind.equals("information")))
            throw new IllegalArgumentException("Invalid information acknowledgement");
        if (kind.equals("information") && (version!=2 || !progression.equals("once") || o.has("tiers") || o.has("objectiveMode") || o.has("objectiveThreshold") || progressBeforeUnlock
                || cooldown!=0 || timeLimit!=0 || noDeath))
            throw new IllegalArgumentException("Information quests require schemaVersion 2 and once progression without timers or tiers");
        if (version==1 && (o.has("objectiveMode") || o.has("objectiveThreshold") || progressBeforeUnlock || o.has("tiers") || o.has("afterLast") || o.has("cooldownSeconds") || o.has("timeLimitSeconds") || o.has("noDeath")))
            throw new IllegalArgumentException("Advanced progression requires schemaVersion 2");
        List<QuestDefinition.Objective> objectives = new ArrayList<>(); Set<String> ids = new HashSet<>();
        if (array(o,"objectives").size() > 256) throw new IllegalArgumentException("Too many objectives");
        for (JsonElement e : array(o,"objectives")) {
            JsonObject v = e.getAsJsonObject(); fields(v,"id","event","target","count","text","conditions","optional","data");
            String oid = string(v,"id"), event = string(v,"event"), target = string(v,"target");
            if (!oid.matches("[a-z0-9_.-]+") || !ids.add(oid)) throw new IllegalArgumentException("Invalid objective " + oid);
            QuestExtensions.event(event);
            identifier(target.startsWith("#") ? target.substring(1) : target); hook.validate(event,target);
            JsonObject data=v.has("data")?object(v,"data"):new JsonObject();QuestExtensions.validateObjective(event,data);
            objectives.add(new QuestDefinition.Objective(oid,event,target,integer(v,"count",1,1_000_000_000L),v.has("text") ? translations(object(v,"text")) : Map.of(),v.has("conditions") ? condition(object(v,"conditions"),false,hook,0,new int[1]) : null,v.has("optional") && bool(v,"optional"),data.toString()));
        }
        if(kind.equals("standard")) {
            long required=objectives.stream().filter(objective->!objective.optional()).count();
            if(required==0 || mode.equals("at_least") && objectiveThreshold>required)
                throw new IllegalArgumentException("Quest needs enough non-optional objectives for completion");
        }
        if (kind.equals("information")) {
            if (!objectives.isEmpty()) throw new IllegalArgumentException("Information quests must not define gameplay objectives");
            if (acknowledgement.equals("manual")) objectives.add(new QuestDefinition.Objective("acknowledge","manual",id,1,Map.of(),null));
        } else if (objectives.isEmpty()) throw new IllegalArgumentException("Objectives cannot be empty");
        var requires = o.has("requires") ? condition(object(o,"requires"),true,hook,0,new int[1]) : null;
        List<QuestDefinition.Reward> rewards = rewards(array(o,"rewards"),hook);
        if (kind.equals("information") && acknowledgement.equals("read_only") && !rewards.isEmpty())
            throw new IllegalArgumentException("Read-only information quests cannot have rewards");
        List<QuestDefinition.Tier> tiers=new ArrayList<>(); Set<String> tierIds=new HashSet<>(); long previous=0;
        if (o.has("tiers")) {
            if (!progression.equals("tiered") || array(o,"tiers").size()>256) throw new IllegalArgumentException("Invalid tiers");
            for (var value:array(o,"tiers")) {
                var tier=value.getAsJsonObject(); fields(tier,"id","threshold","rewards");
                String tid=string(tier,"id"); long threshold=integer(tier,"threshold",1,1_000_000_000);
                if (!tid.matches("[a-z0-9_.-]+") || !tierIds.add(tid) || threshold<=previous) throw new IllegalArgumentException("Tier thresholds must increase and IDs must be unique");
                for (var objective:objectives) if (objective.count()>1_000_000_000L/threshold) throw new IllegalArgumentException("Tier target exceeds 1e9");
                tiers.add(new QuestDefinition.Tier(tid,threshold,rewards(array(tier,"rewards"),hook))); previous=threshold;
            }
        }
        if (progression.equals("tiered") && tiers.isEmpty()) throw new IllegalArgumentException("Tiered quests require tiers");
        String afterLast=o.has("afterLast") ? string(o,"afterLast") : "stop";
        if (!Set.of("stop","repeat").contains(afterLast) || (o.has("afterLast") && !progression.equals("tiered"))) throw new IllegalArgumentException("Invalid afterLast");
        QuestDefinition.Layout layout = null;
        if (o.has("layout")) { var l = object(o,"layout"); fields(l,"x","y"); double x = number(l,"x"), y = number(l,"y"); layout = new QuestDefinition.Layout(x,y); }
        String fingerprint;
        String mechanical=mechanics(objectives);
        if (kind.equals("information")) mechanical += "|information|"+acknowledgement;
        if (version==2) mechanical += "|v2|"+progression+"|"+mode+"|"+afterLast+"|"+cooldown+"|"+timeLimit+"|"+noDeath
            +"|"+objectives.stream().map(QuestDefinition.Objective::id).collect(Collectors.joining(","))+"|"+tiers.stream().map(t -> t.id()+":"+t.threshold()).collect(Collectors.joining(","));
        // Preserve legacy progress fingerprints exactly. New optional/threshold semantics are
        // only fingerprinted when a pack actually uses them.
        if(mode.equals("at_least")||objectives.stream().anyMatch(QuestDefinition.Objective::optional))
            mechanical+="|selection-v1|"+objectiveThreshold+"|"+objectives.stream()
                .map(x->x.id()+":"+x.optional()).collect(Collectors.joining(","));
        if(progressBeforeUnlock)mechanical+="|flexible-v1";
        try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mechanical.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        return new QuestDefinition(id,category,icon,title,description,List.copyOf(objectives),requires,List.copyOf(rewards),progression,layout,o.has("narrative") ? translations(object(o,"narrative")) : Map.of(),fingerprint,
            mode,List.copyOf(tiers),afterLast,cooldown,timeLimit,noDeath,kind,acknowledgement,o.has("hidden") && bool(o,"hidden"),o.has("size") ? (int)integer(o,"size",1,Integer.MAX_VALUE) : 32,
            o.has("descriptionRich")?descriptionRich(object(o,"descriptionRich")):Map.of(),objectiveThreshold,progressBeforeUnlock,presentation(o));
    }
    private static QuestDefinition.Presentation presentation(JsonObject quest) {
        if(!quest.has("presentation"))return QuestDefinition.Presentation.DEFAULT;
        var p=object(quest,"presentation");fields(p,"subtitle","tags","shape","iconScale","optional","disableCompletionToast","hideDetailsUntilUnlocked","hideTextUntilCompleted","invisibleUntilCompleted","invisibleUntilTasks","hideDependencyLines","hideDependentLines");
        Map<String,String> subtitle=p.has("subtitle")?translations(object(p,"subtitle")):Map.of();
        List<String> tags=new ArrayList<>();
        if(p.has("tags"))for(var value:array(p,"tags")) {
            if(tags.size()>=64 || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || !value.getAsString().matches("[a-zA-Z0-9_.:/-]{1,80}"))throw new IllegalArgumentException("Invalid quest tag");
            if(!tags.contains(value.getAsString()))tags.add(value.getAsString());
        }
        String shape=p.has("shape")?string(p,"shape"):"square";
        if(!Set.of("square","circle","rounded_square","diamond","pentagon","hexagon","octagon","heart","gear").contains(shape))throw new IllegalArgumentException("Unknown quest shape");
        double scale=p.has("iconScale")?number(p,"iconScale"):1;
        if(scale<.25||scale>2)throw new IllegalArgumentException("Icon scale outside .25..2");
        return new QuestDefinition.Presentation(subtitle,List.copyOf(tags),shape,scale,
            p.has("optional")&&bool(p,"optional"),p.has("disableCompletionToast")&&bool(p,"disableCompletionToast"),
            p.has("hideDetailsUntilUnlocked")&&bool(p,"hideDetailsUntilUnlocked"),p.has("hideTextUntilCompleted")&&bool(p,"hideTextUntilCompleted"),
            p.has("invisibleUntilCompleted")&&bool(p,"invisibleUntilCompleted"),p.has("invisibleUntilTasks")?(int)integer(p,"invisibleUntilTasks",0,256):0,
            p.has("hideDependencyLines")&&bool(p,"hideDependencyLines"),p.has("hideDependentLines")&&bool(p,"hideDependentLines"));
    }
    private static Map<String,JsonArray> descriptionRich(JsonObject locales) {
        Map<String,JsonArray> parsed=new HashMap<>();
        for (var entry:locales.entrySet()) {
            if(!entry.getKey().matches("[a-z]{2,3}_[a-z]{2,4}") || !entry.getValue().isJsonArray())
                throw new IllegalArgumentException("Invalid rich description locale: "+entry.getKey());
            JsonArray parts=entry.getValue().getAsJsonArray();
            
            for(var el:parts){
                if(!el.isJsonObject())throw new IllegalArgumentException("Invalid rich description part");
                JsonObject part=el.getAsJsonObject();
                if(!part.has("type"))throw new IllegalArgumentException("Missing rich description type");
                String type=part.get("type").getAsString();
                if(type.equals("text")){
                    if(!Set.of("type","text","font","size","sizePx","color","highlight","bold","italic","underline","strike").containsAll(part.keySet())
                        || !part.has("text"))
                        throw new IllegalArgumentException("Invalid rich description text");
                    if(part.has("sizePx") && (part.get("sizePx").getAsInt()<=0))
                        throw new IllegalArgumentException("Rich description pixel size out of range");
                    if(part.has("size") && (part.get("size").getAsDouble()<.75||part.get("size").getAsDouble()>2))
                        throw new IllegalArgumentException("Invalid legacy description size");
                } else if(type.equals("image")){
                    if(!part.keySet().containsAll(Set.of("data","width","height")) ||
                        !Set.of("type","data","width","height").containsAll(part.keySet()))
                        throw new IllegalArgumentException("Invalid rich description image");
                    String data=part.get("data").getAsString();
                    if(!data.matches("data:image/(png|jpeg|gif);base64,[A-Za-z0-9+/]+={0,2}") ||
                        part.get("width").getAsInt()<=0||
                        part.get("height").getAsInt()<=0)
                        throw new IllegalArgumentException("Invalid rich description image size");
                }else throw new IllegalArgumentException("Unknown rich description part: "+type);
            }
            parsed.put(entry.getKey(),parts);
        }
        return Map.copyOf(parsed);
    }
    private static List<QuestDefinition.Reward> rewards(JsonArray array,ValidationHook hook) {
        if (array.size()>64) throw new IllegalArgumentException("Too many rewards");
        List<QuestDefinition.Reward> result=new ArrayList<>();
        for (var value:array) {
            var v=value.getAsJsonObject(); String type=string(v,"type");
            Map<String,String> text=v.has("text") ? translations(object(v,"text")) : Map.of();
            QuestDefinition.Reward reward;
            if (type.equals("experience") || type.equals("experience_levels")) {
                fields(v,"type","amount","text"); reward=new QuestDefinition.Reward(type,null,0,(int)integer(v,"amount",1,1_000_000),"{}",text);
            } else if (type.equals("item")) {
                fields(v,"type","item","count","text"); String item=identifier(string(v,"item")); hook.validate("item",item);
                reward=new QuestDefinition.Reward(type,item,(int)integer(v,"count",1,2304),0,"{}",text);
            } else {
                fields(v,"type","data","text"); var data=object(v,"data"); QuestExtensions.validateReward(type,data);
                if(type.equals("questplus:advancement"))
                    hook.validate("advancement_complete",data.get("advancement").getAsString());
                if(Set.of("questplus:random","questplus:loot","questplus:all","questplus:choice").contains(type))
                    for(var option:data.getAsJsonArray("options")){
                        var entry=option.getAsJsonObject();
                        if(entry.get("type").getAsString().equals("item"))
                            hook.validate("item",entry.get("item").getAsString());
                        else if(entry.get("type").getAsString().equals("questplus:advancement"))
                            hook.validate("advancement_complete",entry.getAsJsonObject("data").get("advancement").getAsString());
                    }
                reward=new QuestDefinition.Reward(type,null,0,0,data.toString(),text);
            }
            QuestExtensions.validateSavedReward(reward); result.add(reward);
        }
        int maximum=0;
        for(var reward:result){
            if(Set.of("questplus:random","questplus:loot","questplus:all").contains(reward.type())){
                var data=JsonParser.parseString(reward.data()).getAsJsonObject();
                maximum+=reward.type().equals("questplus:all") ? data.getAsJsonArray("options").size() :
                    data.has("rolls")?data.get("rolls").getAsInt():1;
            }else maximum++;
            if(maximum>64)throw new IllegalArgumentException("Expanded reward table may exceed 64 grants");
        }
        return List.copyOf(result);
    }
    private static String mechanics(List<QuestDefinition.Objective> objectives) {
        return objectives.stream().sorted(Comparator.comparing(QuestDefinition.Objective::id))
            .map(o -> o.id() + "|" + o.event() + "|" + o.target() + "|" + o.count() + "|" + conditionSignature(o.conditions())+(o.data()!=null&&!o.data().equals("{}")?"|data:"+o.data():""))
            .collect(Collectors.joining("\n"));
    }
    private static String conditionSignature(QuestDefinition.Condition c) {
        if (c == null) return "";
        return c.operator() + "(" + Objects.toString(c.value(), "") + ":" + c.children().stream().map(QuestRegistry::conditionSignature).collect(Collectors.joining(",")) + ")";
    }
    private static QuestDefinition.Condition condition(JsonObject o, boolean prerequisite, ValidationHook hook, int depth, int[] nodes) {
        if (depth > 16 || ++nodes[0] > 256 || o.size() != 1) throw new IllegalArgumentException("Invalid condition tree");
        String key = o.keySet().iterator().next(); JsonElement value = o.get(key); List<QuestDefinition.Condition> children = new ArrayList<>(); String literal = null;
        if (key.equals("and") || key.equals("or")) { if (!value.isJsonArray() || value.getAsJsonArray().isEmpty()) throw new IllegalArgumentException("Empty condition"); for (var child : value.getAsJsonArray()) children.add(condition(child.getAsJsonObject(),prerequisite,hook,depth+1,nodes)); }
        else if (key.equals("not")) children.add(condition(value.getAsJsonObject(),prerequisite,hook,depth+1,nodes));
        else if (key.equals("at_least")) {
            var quorum=value.getAsJsonObject();fields(quorum,"count","of");var options=array(quorum,"of");
            literal=Long.toString(integer(quorum,"count",1,options.size()));
            for(var child:options)children.add(condition(child.getAsJsonObject(),prerequisite,hook,depth+1,nodes));
        }
        else if (prerequisite && Set.of("quest","started").contains(key)) literal = identifier(string(o,key));
        else if (!prerequisite && Set.of("dimension","tool","biome","effect").contains(key)) { literal = identifier(string(o,key)); hook.validate(key,literal); }
        else if (!prerequisite && key.equals("sneaking") && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) literal = value.getAsString();
        else if (!prerequisite && (key.equals("healthAtMost") || key.equals("healthAtLeast"))) { double n = number(o,key); if (n < 0) throw new IllegalArgumentException("Invalid health"); literal = Double.toString(n); }
        else if (!prerequisite && QuestExtensions.hasCondition(key)) {
            QuestExtensions.validateCondition(key,value); literal=value.toString();
            if (key.equals("equipment")) hook.validate("tool",value.getAsJsonObject().get("item").getAsString());
        }
        else throw new IllegalArgumentException("Unknown condition " + key);
        return new QuestDefinition.Condition(key,literal,List.copyOf(children));
    }
    private static void fields(JsonObject o, String... allowed) { var set = Set.of(allowed); for (String key : o.keySet()) if (!set.contains(key)) throw new IllegalArgumentException("Unknown field " + key); }
    private static String string(JsonObject o,String k) { var e = o.get(k); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank()) throw new IllegalArgumentException("Expected string: " + k); return e.getAsString(); }
    private static boolean bool(JsonObject o,String k) { var value=o.get(k); if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean: "+k); return value.getAsBoolean(); }
    private static String identifier(String id) { if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid identifier: " + id); return id; }
    private static JsonObject object(JsonObject o,String k) { if (!o.has(k) || !o.get(k).isJsonObject()) throw new IllegalArgumentException("Expected object: " + k); return o.getAsJsonObject(k); }
    private static JsonArray array(JsonObject o,String k) { if (!o.has(k) || !o.get(k).isJsonArray()) throw new IllegalArgumentException("Expected array: " + k); return o.getAsJsonArray(k); }
    private static double number(JsonObject o,String k) { var e = o.get(k); if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || !Double.isFinite(e.getAsDouble())) throw new IllegalArgumentException("Expected number: " + k); return e.getAsDouble(); }
    private static long integer(JsonObject o,String k,long min,long max) { number(o,k); try { long n = o.get(k).getAsBigDecimal().longValueExact(); if (n < min || n > max) throw new ArithmeticException(); return n; } catch (ArithmeticException ex) { throw new IllegalArgumentException("Invalid integer: " + k); } }
    private static Map<String,String> translations(JsonObject o) { Map<String,String> out = new HashMap<>(); for (String k : o.keySet()) out.put(k,string(o,k)); if (!out.containsKey("en_us")) throw new IllegalArgumentException("Missing en_us translation"); return Map.copyOf(out); }
}
