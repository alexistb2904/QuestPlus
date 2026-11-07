package dev.questplus.clientdata;

import com.google.gson.JsonObject;
import dev.questplus.core.QuestDefinition;
import java.util.Set;

/**
 * Small display rules shared by the GUI and HUD; server projection remains
 * authoritative.
 */
public final class QuestPresentation {
	private QuestPresentation() {
	}

	public static String status(QuestDefinition quest, JsonObject progress) {
		if (flag(progress, "claimable") || progress.has("rewardState")
				&& "choice".equals(progress.getAsJsonObject("rewardState").get("status").getAsString()))
			return "reward_ready";
		if (flag(progress, "terminal") || quest.progression().equals("once") && flag(progress, "completed")
				|| flag(progress, "waitingRestart"))
			return "completed";
		if (!flag(progress, "unlocked"))
			return "locked";
		if (count(progress, "tier") > 0 || progress.has("counters") && progress.getAsJsonObject("counters").entrySet()
				.stream().anyMatch(entry -> entry.getValue().getAsLong() > 0))
			return "active";
		return "available";
	}

	public static long target(QuestDefinition quest, QuestDefinition.Objective objective, JsonObject progress) {
		if (!quest.progression().equals("tiered") || quest.tiers().isEmpty())
			return objective.count();
		long tier = Math.clamp(count(progress, "tier"), 0, quest.tiers().size() - 1L);
		return objective.count() * quest.tiers().get((int) tier).threshold();
	}

	/** Fallback objective text for quests without a localized custom label. */
	public static String objectiveText(QuestDefinition.Objective objective, String locale, String targetName) {
		return objectiveText(objective, locale, targetName, objective.count());
	}

	/** Use the current tier's target, not the base count from the authored objective. */
	public static String objectiveText(QuestDefinition.Objective objective, String locale, String targetName, long targetCount) {
		String lang = locale == null ? "en" : locale.toLowerCase(java.util.Locale.ROOT);
		int column = lang.startsWith("fr") ? 1 : lang.startsWith("es") ? 2 : lang.startsWith("zh") ? 3 : 0;
		String[] verbs = switch (objective.event()) {
		case "block_break" -> new String[] { "Break", "Casser", "Romper", "破坏" };
		case "block_place" -> new String[] { "Place", "Placer", "Colocar", "放置" };
		case "block_interact" -> new String[] { "Interact with", "Interagir avec", "Interactuar con", "交互" };
		case "item_obtained" -> new String[] { "Obtain", "Obtenir", "Conseguir", "获取" };
		case "item_craft" -> new String[] { "Craft", "Fabriquer", "Fabricar", "合成" };
		case "item_smelt" -> new String[] { "Smelt", "Cuire", "Fundir", "熔炼" };
		case "item_use" -> new String[] { "Use", "Utiliser", "Usar", "使用" };
		case "item_consume" -> new String[] { "Consume", "Consommer", "Consumir", "食用" };
		case "fish_item", "fish_entity" -> new String[] { "Fish", "Pêcher", "Pescar", "钓起" };
		case "trade" -> new String[] { "Trade", "Échanger", "Comerciar", "交易" };
		case "entity_kill" -> new String[] { "Defeat", "Vaincre", "Derrotar", "击败" };
		case "tame_animal" -> new String[] { "Tame", "Apprivoiser", "Domesticar", "驯服" };
		case "breed_animal" -> new String[] { "Breed", "Faire reproduire", "Criar", "繁殖" };
		case "npc_interact" -> new String[] { "Talk to", "Parler à", "Hablar con", "交谈" };
		case "biome_visit", "structure_visit" -> new String[] { "Visit", "Visiter", "Visitar", "探索" };
		case "dimension_enter" -> new String[] { "Enter", "Entrer dans", "Entrar en", "进入" };
		case "fluid_submit" -> new String[] { "Submit fluid (mB)", "Remettre du fluide (mB)", "Entregar fluido (mB)",
				"提交流体（毫桶）" };
		case "energy_submit" -> new String[] { "Submit energy (FE)", "Remettre de l’énergie (FE)",
				"Entregar energía (FE)", "提交能量（FE）" };
		case "fluid_transfer" -> new String[] { "Transfer fluid", "Transférer le fluide", "Transferir fluido", "传输流体" };
		case "energy_transfer" -> new String[] { "Transfer FE", "Transférer FE", "Transferir FE", "传输能量" };
		case "inventory_count" -> new String[] { "Hold", "Posséder", "Tener", "持有" };
		case "item_submit" -> new String[] { "Submit", "Soumettre", "Entregar", "提交" };
		case "item_turn_in" -> new String[] { "Turn in", "Remettre", "Entregar y consumir", "交付并消耗" };
		case "travel_distance" -> new String[] { "Travel", "Parcourir", "Recorrer", "行进" };
		case "location" -> new String[] { "Reach", "Atteindre", "Llegar a", "抵达" };
		case "player_death" -> new String[] { "Die", "Mourir", "Morir", "死亡" };
		case "manual" -> new String[] { "Acknowledge", "Valider", "Confirmar", "确认" };
		default -> new String[] { "Complete", "Accomplir", "Completar", "完成" };
		};
		if (objective.event().equals("travel_distance")) {
			String meters = java.math.BigDecimal.valueOf(targetCount / 100.0).stripTrailingZeros()
					.toPlainString();
			return new String[] { "Travel ", "Parcourir ", "Recorrer ", "行进 " }[column] + meters + " m";
		}
		if (objective.event().equals("custom_stat"))
			return new String[] { "Reach stat ", "Atteindre la statistique ", "Alcanzar estadística ",
					"达到统计值 " }[column] + objective.target() + " ≥ " + targetCount;
		if (objective.event().equals("advancement_complete"))
			return new String[] { "Complete advancement ", "Compléter le progrès ", "Completar progreso ",
					"完成进度 " }[column] + objective.target();
		if (objective.event().equals("xp_levels"))
			return new String[] { "Reach XP level ", "Atteindre le niveau XP ", "Alcanzar nivel XP ",
					"达到经验等级 " }[column] + targetCount;
		if (objective.event().equals("xp_submit_levels"))
			return new String[] { "Submit XP levels: ", "Remettre des niveaux XP : ", "Entregar niveles XP: ",
					"提交经验等级：" }[column] + targetCount;
		if (objective.event().equals("xp_submit_points"))
			return new String[] { "Submit XP points: ", "Remettre des points XP : ", "Entregar puntos XP: ",
					"提交经验点数：" }[column] + targetCount;
		if (objective.event().equals("location"))
			return new String[] { "Reach location", "Atteindre la position", "Llegar a la posición", "到达指定位置" }[column];
		if (Set.of("observe_block", "observe_entity").contains(objective.event()))
			return new String[] { "Observe ", "Observer ", "Observar ", "观察 " }[column] + targetName + " · "
					+ (targetCount / 20.0) + " s";
		if (objective.event().equals("stage"))
			return new String[] { "Have stage ", "Posséder le stage ", "Tener etapa ", "拥有阶段 " }[column]
					+ objective.target();
		if (objective.event().equals("manual"))
			return new String[] { "Confirm", "Confirmer", "Confirmar", "确认" }[column];
		if (objective.event().equals("player_death"))
			return new String[] { "Die", "Mourir", "Morir", "死亡" }[column];
		String target = targetName == null || targetName.isBlank() ? objective.target() : targetName;
		if (Set.of("block_break", "block_place", "block_interact", "item_obtained", "item_craft",
				"item_smelt", "item_use", "item_consume", "inventory_count", "item_submit", "item_turn_in",
				"entity_kill", "tame_animal", "breed_animal", "fish_item", "fish_entity").contains(objective.event()))
			target = countedTarget(target, lang, targetCount);
		return verbs[column] + " " + targetCount + " " + target;
	}

	private static String countedTarget(String name, String locale, long count) {
		if (count <= 1 || locale.startsWith("zh") || name.isBlank())
			return name;
		String lower = name.substring(0, 1).toLowerCase(java.util.Locale.ROOT) + name.substring(1);
		if (locale.startsWith("en")) {
			if (Set.of("stone", "cobblestone", "deepslate").contains(lower.toLowerCase(java.util.Locale.ROOT)))
				return lower + " blocks";
			int space = lower.lastIndexOf(' ');
			String stem = lower.substring(0, space + 1), word = lower.substring(space + 1);
			if (word.endsWith("s"))
				return lower;
			if (word.endsWith("y") && word.length() > 1 && !"aeiou".contains(word.substring(word.length() - 2, word.length() - 1)))
				return stem + word.substring(0, word.length() - 1) + "ies";
			if (word.endsWith("ch") || word.endsWith("sh") || word.endsWith("x") || word.endsWith("z"))
				return lower + "es";
			return lower + "s";
		}
		int space = lower.indexOf(' ');
		String word = space < 0 ? lower : lower.substring(0, space);
		String suffix = space < 0 ? "" : lower.substring(space);
		if (locale.startsWith("fr"))
			return (word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ? word : word + "s") + suffix;
		if (locale.startsWith("es")) {
			if (word.endsWith("s"))
				return lower;
			return word + ("aeiouáéíóú".indexOf(word.charAt(word.length() - 1)) >= 0 ? "s" : "es") + suffix;
		}
		return name;
	}

	/** Do not display the same sentence twice for a single-objective quest. */
	public static boolean redundantDescription(String description, java.util.List<String> labels) {
		if (description == null || description.isBlank())
			return true;
		String normalized = normalizeText(description);
		return labels.stream().anyMatch(label -> normalized.equals(normalizeText(label)));
	}

	private static String normalizeText(String text) {
		return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT)
				.replaceAll("[\\p{Punct}\\s]+", " ").trim();
	}

	private static boolean flag(JsonObject json, String key) {
		return json.has(key) && json.get(key).getAsBoolean();
	}

	private static long count(JsonObject json, String key) {
		return json.has(key) ? json.get(key).getAsLong() : 0;
	}
}
