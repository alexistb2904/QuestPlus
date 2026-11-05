package dev.questplus.clientdata;

import com.google.gson.JsonObject;
import dev.questplus.core.QuestDefinition;

/** Small display rules shared by the GUI and HUD; server projection remains authoritative. */
public final class QuestPresentation {
    private QuestPresentation() {}

    public static String status(QuestDefinition quest, JsonObject progress) {
        if (flag(progress,"claimable")) return "reward_ready";
        if (flag(progress,"terminal") || quest.progression().equals("once") && flag(progress,"completed")
                || flag(progress,"waitingRestart")) return "completed";
        if (!flag(progress,"unlocked")) return "locked";
        if (count(progress,"tier")>0 || progress.has("counters") && progress.getAsJsonObject("counters").entrySet()
                .stream().anyMatch(entry -> entry.getValue().getAsLong()>0)) return "active";
        return "available";
    }

    public static long target(QuestDefinition quest, QuestDefinition.Objective objective, JsonObject progress) {
        if (!quest.progression().equals("tiered") || quest.tiers().isEmpty()) return objective.count();
        long tier=Math.clamp(count(progress,"tier"),0,quest.tiers().size()-1L);
        return objective.count()*quest.tiers().get((int)tier).threshold();
    }

    /** Fallback objective text for quests without a localized custom label. */
    public static String objectiveText(QuestDefinition.Objective objective,String locale,String targetName) {
        String lang=locale==null?"en":locale.toLowerCase(java.util.Locale.ROOT);
        int column=lang.startsWith("fr")?1:lang.startsWith("es")?2:lang.startsWith("zh")?3:0;
        String[] verbs=switch(objective.event()) {
            case "block_break" -> new String[]{"Break","Casser","Romper","破坏"};
            case "block_place" -> new String[]{"Place","Placer","Colocar","放置"};
            case "block_interact" -> new String[]{"Interact with","Interagir avec","Interactuar con","交互"};
            case "item_obtained" -> new String[]{"Obtain","Obtenir","Conseguir","获取"};
            case "item_craft" -> new String[]{"Craft","Fabriquer","Fabricar","合成"};
            case "item_smelt" -> new String[]{"Smelt","Cuire","Fundir","熔炼"};
            case "item_use" -> new String[]{"Use","Utiliser","Usar","使用"};
            case "item_consume" -> new String[]{"Consume","Consommer","Consumir","食用"};
            case "fish_item","fish_entity" -> new String[]{"Fish","Pêcher","Pescar","钓起"};
            case "trade" -> new String[]{"Trade","Échanger","Comerciar","交易"};
            case "entity_kill" -> new String[]{"Defeat","Vaincre","Derrotar","击败"};
            case "tame_animal" -> new String[]{"Tame","Apprivoiser","Domesticar","驯服"};
            case "breed_animal" -> new String[]{"Breed","Faire reproduire","Criar","繁殖"};
            case "npc_interact" -> new String[]{"Talk to","Parler à","Hablar con","交谈"};
            case "biome_visit","structure_visit" -> new String[]{"Visit","Visiter","Visitar","探索"};
            case "dimension_enter" -> new String[]{"Enter","Entrer dans","Entrar en","进入"};
            case "inventory_count" -> new String[]{"Hold","Posséder","Tener","持有"};
            case "item_submit" -> new String[]{"Submit","Soumettre","Entregar","提交"};
            case "item_turn_in" -> new String[]{"Turn in","Remettre","Entregar y consumir","交付并消耗"};
            case "travel_distance" -> new String[]{"Travel","Parcourir","Recorrer","行进"};
            case "location" -> new String[]{"Reach","Atteindre","Llegar a","抵达"};
            case "player_death" -> new String[]{"Die","Mourir","Morir","死亡"};
            case "manual" -> new String[]{"Acknowledge","Valider","Confirmar","确认"};
            default -> new String[]{"Complete","Accomplir","Completar","完成"};
        };
        if (objective.event().equals("travel_distance")) {
            String meters=java.math.BigDecimal.valueOf(objective.count()/100.0).stripTrailingZeros().toPlainString();
            return new String[]{"Travel ","Parcourir ","Recorrer ","行进 "}[column]+meters+" m";
        }
        if (objective.event().equals("custom_stat"))
            return new String[]{"Reach statistic ","Atteindre la statistique ","Alcanzar estadística ","达到统计值 "}[column]+objective.target()+": "+objective.count();
        if (objective.event().equals("xp_levels"))
            return new String[]{"Reach XP level ","Atteindre le niveau XP ","Alcanzar nivel XP ","达到经验等级 "}[column]+objective.count();
        if (objective.event().equals("location"))
            return new String[]{"Reach location","Atteindre la position","Llegar a la posición","到达指定位置"}[column];
        if (objective.event().equals("manual"))
            return new String[]{"Confirm","Confirmer","Confirmar","确认"}[column];
        if (objective.event().equals("player_death"))
            return new String[]{"Die","Mourir","Morir","死亡"}[column];
        String target=targetName==null||targetName.isBlank()?objective.target():targetName;
        return verbs[column]+" "+objective.count()+" "+target;
    }

    /** Do not display the same sentence twice for a single-objective quest. */
    public static boolean redundantDescription(String description,java.util.List<String> labels) {
        if (description==null || description.isBlank()) return true;
        String normalized=normalizeText(description);
        return labels.stream().anyMatch(label -> normalized.equals(normalizeText(label)));
    }
    private static String normalizeText(String text) {
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{Punct}\\s]+"," ").trim();
    }

    private static boolean flag(JsonObject json,String key) { return json.has(key) && json.get(key).getAsBoolean(); }
    private static long count(JsonObject json,String key) { return json.has(key) ? json.get(key).getAsLong() : 0; }
}
