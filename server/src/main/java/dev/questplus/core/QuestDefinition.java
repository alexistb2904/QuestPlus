package dev.questplus.core;

import java.util.*;
import com.google.gson.JsonArray;

public record QuestDefinition(String id, String category, String icon, Map<String,String> title,
    Map<String,String> description, List<Objective> objectives, Condition requires,
    List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
    String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
    String kind, String acknowledgement, boolean hidden, int size, Map<String,JsonArray> descriptionRich, int objectiveThreshold, boolean progressBeforeUnlock, Presentation presentation) {
    public QuestDefinition(String id, String category, String icon, Map<String,String> title,
    Map<String,String> description, List<Objective> objectives, Condition requires,
    List<Reward> rewards, String progression, Layout layout, Map<String,String> narrative, String fingerprint,
    String objectiveMode, List<Tier> tiers, String afterLast, long cooldownSeconds, long timeLimitSeconds, boolean noDeath,
    String kind, String acknowledgement, boolean hidden, int size, Map<String,JsonArray> descriptionRich, int objectiveThreshold, boolean progressBeforeUnlock) {
        this(id,category,icon,title,description,objectives,requires,rewards,progression,layout,narrative,fingerprint,objectiveMode,tiers,afterLast,cooldownSeconds,timeLimitSeconds,noDeath,kind,acknowledgement,hidden,size,descriptionRich,objectiveThreshold,progressBeforeUnlock,Presentation.DEFAULT);
    }
    public QuestDefinition { if(presentation==null)presentation=Presentation.DEFAULT; }
    public record Presentation(Map<String,String> subtitle,List<String> tags,String shape,double iconScale,boolean optional,
        boolean disableCompletionToast,boolean hideDetailsUntilUnlocked,boolean hideTextUntilCompleted,
        boolean invisibleUntilCompleted,int invisibleUntilTasks,boolean hideDependencyLines,boolean hideDependentLines,boolean hideUntilDependenciesVisible) {
        public Presentation(Map<String,String> subtitle,List<String> tags,String shape,double iconScale,boolean optional,boolean disableCompletionToast,
            boolean hideDetailsUntilUnlocked,boolean hideTextUntilCompleted,boolean invisibleUntilCompleted,int invisibleUntilTasks,boolean hideDependencyLines,boolean hideDependentLines) {
            this(subtitle,tags,shape,iconScale,optional,disableCompletionToast,hideDetailsUntilUnlocked,hideTextUntilCompleted,invisibleUntilCompleted,invisibleUntilTasks,hideDependencyLines,hideDependentLines,false);
        }
        public static final Presentation DEFAULT=new Presentation(Map.of(),List.of(),"square",1,false,false,false,false,false,0,false,false);
    }
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
    public record Objective(String id, String event, String target, long count, Map<String,String> text, Condition conditions, boolean optional,String data) {
        public Objective(String id,String event,String target,long count,Map<String,String> text,Condition conditions,boolean optional) {
            this(id,event,target,count,text,conditions,optional,"{}");
        }
        public Objective(String id,String event,String target,long count,Map<String,String> text,Condition conditions) {
            this(id,event,target,count,text,conditions,false,"{}");
        }
    }
    public record Reward(String type, String item, int count, int amount, String data, Map<String,String> text, RewardPolicy policy) {
        public Reward { if(policy==null)policy=RewardPolicy.DEFAULT; }
        public Reward(String type,String item,int count,int amount,String data,Map<String,String> text) { this(type,item,count,amount,data,text,RewardPolicy.DEFAULT); }
        public Reward(String type,String item,int count,int amount) { this(type,item,count,amount,"{}",Map.of()); }
        public Reward withPolicy(RewardPolicy value) { return new Reward(type,item,count,amount,data,text,value); }
    }
    public record RewardPolicy(boolean teamReward,String autoClaim,boolean excludeFromClaimAll,UUID teamScope) {
        public RewardPolicy(boolean teamReward,String autoClaim,boolean excludeFromClaimAll) {this(teamReward,autoClaim,excludeFromClaimAll,null);}
        public static final RewardPolicy DEFAULT=new RewardPolicy(false,"disabled",false);
        public RewardPolicy { if(autoClaim==null)autoClaim="disabled"; if(!Set.of("disabled","enabled","no_toast","invisible").contains(autoClaim)||teamScope!=null&&!teamReward)throw new IllegalArgumentException("Invalid reward policy"); }
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
                case "started" -> completed.contains("@started:"+value);
                case "at_least" -> children.stream().filter(c->c.matches(context,completed)).count()>=Integer.parseInt(value);
                case "healthAtMost" -> context.get("health") instanceof Number n && n.doubleValue() <= Double.parseDouble(value);
                case "healthAtLeast" -> context.get("health") instanceof Number n && n.doubleValue() >= Double.parseDouble(value);
                case "sneaking" -> context.get("sneaking") instanceof Boolean b && b == Boolean.parseBoolean(value);
                case "effect" -> context.get("effects") instanceof Set<?> effects && effects.contains(value);
                case "equipment", "position", "context" -> QuestExtensions.testCondition(operator,value,context);
                default -> QuestExtensions.hasCondition(operator) ? QuestExtensions.testCondition(operator,value,context) : Objects.equals(context.get(operator), value);
            };
        }
        public void questReferences(Set<String> out) {
            if (operator.equals("quest") || operator.equals("started")) out.add(value);
            children.forEach(c -> c.questReferences(out));
        }
    }
}
