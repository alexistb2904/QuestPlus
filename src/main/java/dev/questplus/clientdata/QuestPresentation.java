package dev.questplus.clientdata;

import com.google.gson.JsonObject;
import dev.questplus.core.QuestDefinition;

/** Small display rules shared by the GUI and HUD; server projection remains authoritative. */
public final class QuestPresentation {
    private QuestPresentation() {}

    public static String status(QuestDefinition quest, JsonObject progress) {
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

    /** Text is generated at render time in the player's language, never persisted. */
    public static String objectiveText(QuestDefinition.Objective objective,String locale,String targetName) {
        boolean fr=locale!=null && locale.toLowerCase(java.util.Locale.ROOT).startsWith("fr");
        String verb=switch(objective.event()) {
            case "block_break" -> fr?"Casser":"Break";
            case "block_place" -> fr?"Placer":"Place";
            case "block_interact" -> fr?"Interagir avec":"Interact with";
            case "item_obtained" -> fr?"Obtenir":"Obtain";
            case "item_craft" -> fr?"Fabriquer":"Craft";
            case "item_smelt" -> fr?"Cuire":"Smelt";
            case "item_use" -> fr?"Utiliser":"Use";
            case "item_consume" -> fr?"Consommer":"Consume";
            case "fish_item","fish_entity" -> fr?"Pêcher":"Fish";
            case "trade" -> fr?"Échanger":"Trade";
            case "entity_kill" -> fr?"Vaincre":"Defeat";
            case "tame_animal" -> fr?"Apprivoiser":"Tame";
            case "breed_animal" -> fr?"Faire reproduire":"Breed";
            case "npc_interact" -> fr?"Parler à":"Talk to";
            case "biome_visit","structure_visit" -> fr?"Visiter":"Visit";
            case "dimension_enter" -> fr?"Entrer dans":"Enter";
            case "inventory_count" -> fr?"Posséder":"Hold";
            case "travel_distance" -> fr?"Parcourir":"Travel";
            case "location" -> fr?"Atteindre":"Reach";
            case "player_death" -> fr?"Mourir":"Die";
            case "manual" -> fr?"Valider":"Acknowledge";
            default -> fr?"Accomplir":"Complete";
        };
        String target=targetName==null || targetName.isBlank()?objective.target():targetName;
        return verb+" "+objective.count()+" "+target;
    }

    /** Do not display the same sentence twice for a single-objective quest. */
    public static boolean redundantDescription(String description,java.util.List<String> labels) {
        if (description==null || description.isBlank()) return true;
        if (labels.size()!=1) return false;
        return normalizeText(description).equals(normalizeText(labels.getFirst()));
    }
    private static String normalizeText(String text) {
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT).replaceAll("[\\p{Punct}\\s]+"," ").trim();
    }

    private static boolean flag(JsonObject json,String key) { return json.has(key) && json.get(key).getAsBoolean(); }
    private static long count(JsonObject json,String key) { return json.has(key) ? json.get(key).getAsLong() : 0; }
}
