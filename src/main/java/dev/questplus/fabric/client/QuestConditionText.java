package dev.questplus.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.questplus.core.QuestDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Human-readable, localized objective modifiers (never expose raw condition JSON in the UI). */
final class QuestConditionText {
    private QuestConditionText() {}

    static String withConditions(String objective, QuestDefinition.Condition conditions, Function<String,String> questTitle) {
        return conditions == null ? objective : objective + " · " + describe(conditions,questTitle);
    }

    static String describe(QuestDefinition.Condition c, Function<String,String> questTitle) {
        if (c == null) return "";
        if ("not".equals(c.operator())) {
            if (c.children().isEmpty()) return "";
            var child=c.children().getFirst();
            if ("sneaking".equals(child.operator()))
                return tr(Boolean.parseBoolean(child.value()) ? "sneaking.false" : "sneaking.true");
            return tr("not",describe(child,questTitle));
        }
        if ("and".equals(c.operator()) || "or".equals(c.operator())) {
            String conjunction=tr("join."+c.operator());
            return String.join(" "+conjunction+" ",c.children().stream().map(x->describe(x,questTitle)).toList());
        }
        String value=Objects.toString(c.value(),"");
        return switch (c.operator()) {
            case "sneaking" -> tr(Boolean.parseBoolean(value) ? "sneaking.true" : "sneaking.false");
            // Minecraft health values are half-hearts (2 health = 1 heart).
            case "healthAtMost" -> tr("healthAtMost",hearts(value));
            case "healthAtLeast" -> tr("healthAtLeast",hearts(value));
            case "effect" -> tr("effect",name(value));
            case "tool" -> tr("tool",name(value));
            case "biome" -> tr("biome",name(value));
            case "dimension" -> tr("dimension",name(value));
            case "equipment" -> equipment(value);
            case "position" -> position(value);
            case "context" -> context(value);
            case "quest" -> tr("quest",questTitle.apply(value));
            default -> tr("other",c.operator(),name(value));
        };
    }

    private static String tr(String key,Object... args) {
        return Component.translatable("questplus.client.condition.detail."+key,args).getString();
    }

    private static String hearts(String health) {
        try {
            return BigDecimal.valueOf(Double.parseDouble(health)/2).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) { return health; }
    }

    private static String name(String id) {
        var resource=Identifier.tryParse(id);
        if (resource!=null && BuiltInRegistries.ITEM.containsKey(resource))
            return new ItemStack(BuiltInRegistries.ITEM.getValue(resource)).getHoverName().getString();
        return (resource==null?id:resource.getPath()).replace('_',' ').replace('/',' ');
    }

    private static String equipment(String value) {
        try {
            JsonObject json=JsonParser.parseString(value).getAsJsonObject();
            String item=name(json.get("item").getAsString());
            String slot=json.get("slot").getAsString();
            return tr("equipment",item,tr("slot."+slot));
        } catch (RuntimeException invalid) { return tr("other","equipment",value); }
    }

    private static String position(String value) {
        try {
            JsonObject json=JsonParser.parseString(value).getAsJsonObject();
            List<String> bounds=new ArrayList<>();
            for (String axis:List.of("x","y","z")) {
                String min=axis+"Min",max=axis+"Max";
                if (json.has(min) && json.has(max)) bounds.add(axis.toUpperCase(Locale.ROOT)+" "+json.get(min).getAsString()+"–"+json.get(max).getAsString());
                else if (json.has(min)) bounds.add(axis.toUpperCase(Locale.ROOT)+" ≥"+json.get(min).getAsString());
                else if (json.has(max)) bounds.add(axis.toUpperCase(Locale.ROOT)+" ≤"+json.get(max).getAsString());
            }
            return tr("position",String.join(", ",bounds));
        } catch (RuntimeException invalid) { return tr("other","position",value); }
    }

    private static String context(String value) {
        try {
            JsonObject json=JsonParser.parseString(value).getAsJsonObject();
            return tr("context",json.get("key").getAsString(),json.get("equals").getAsString());
        } catch (RuntimeException invalid) { return tr("other","context",value); }
    }
}
