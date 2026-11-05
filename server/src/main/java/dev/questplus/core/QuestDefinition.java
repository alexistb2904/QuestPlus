package dev.questplus.core;

import java.util.*;
import com.google.gson.JsonArray;

public record QuestDefinition(String id, String category, String icon, Map<String,String> title,
    Map<String,String> description, List<Objective> objectives, Condition requires,
    List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
    String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
    String kind, String acknowledgement, boolean hidden, int size, Map<String,JsonArray> descriptionRich, int objectiveThreshold, boolean progressBeforeUnlock) {
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
        Map<String,String> description, List<Objective> objectives, Condition requires,
        List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
        String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
        String kind, String acknowledgement, boolean hidden, int size, Map<String,JsonArray> descriptionRich, int objectiveThreshold) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,
            objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,hidden,size,descriptionRich,objectiveThreshold,false);
    }
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
        Map<String,String> description, List<Objective> objectives, Condition requires,
        List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
        String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
        String kind, String acknowledgement, boolean hidden, int size, Map<String,JsonArray> descriptionRich) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,
            objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,hidden,size,descriptionRich,1);
    }
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
        Map<String,String> description, List<Objective> objectives, Condition requires,
        List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
        String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
        String kind, String acknowledgement, boolean hidden, int size) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,
            objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,hidden,size,Map.of());
    }
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
        Map<String,String> description, List<Objective> objectives, Condition requires,
        List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
        String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
        String kind, String acknowledgement, boolean hidden) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,
            objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,hidden,32);
    }
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
        Map<String,String> description, List<Objective> objectives, Condition requires,
        List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
        String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
        String kind, String acknowledgement) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,
            objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,false,32);
    }
    public record Objective(String id, String event, String target, long count, Map<String,String> text, Condition conditions, boolean optional) {
        public Objective(String id,String event,String target,long count,Map<String,String> text,Condition conditions) {
            this(id,event,target,count,text,conditions,false);
        }
    }
    public record Reward(String type, String item, int count, int amount, String data, Map<String,String> text) {
        public Reward(String type,String item,int count,int amount) { this(type,item,count,amount,"{}",Map.of()); }
    }
    public record Tier(String id,long threshold,List<Reward> rewards) {}
    public record Layout(double x, double y) {}
    public record Condition(String operator, String value, List<Condition> children) {
        public boolean matches(Map<String,Object> context, Set<String> completed) {
            return switch (operator) {
                case "and" -> children.stream().allMatch(c -> c.matches(context, completed));
                case "or" -> children.stream().anyMatch(c -> c.matches(context, completed));
                case "not" -> !children.getFirst().matches(context, completed);
                case "quest" -> completed.contains(value);
                case "healthAtMost" -> context.get("health") instanceof Number n && n.doubleValue() <= Double.parseDouble(value);
                case "healthAtLeast" -> context.get("health") instanceof Number n && n.doubleValue() >= Double.parseDouble(value);
                case "sneaking" -> context.get("sneaking") instanceof Boolean b && b == Boolean.parseBoolean(value);
                case "effect" -> context.get("effects") instanceof Set<?> effects && effects.contains(value);
                case "equipment", "position", "context" -> QuestExtensions.testCondition(operator,value,context);
                default -> QuestExtensions.hasCondition(operator) ? QuestExtensions.testCondition(operator,value,context) : Objects.equals(context.get(operator), value);
            };
        }
        public void questReferences(Set<String> out) {
            if (operator.equals("quest")) out.add(value);
            children.forEach(c -> c.questReferences(out));
        }
    }
}
