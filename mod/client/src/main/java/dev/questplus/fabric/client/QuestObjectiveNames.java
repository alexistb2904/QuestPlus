package dev.questplus.fabric.client;

import dev.questplus.core.QuestDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Resolves the same localized objective target for the tablet and pinned HUD. */
final class QuestObjectiveNames {
	private QuestObjectiveNames() {
	}

	static String target(QuestDefinition.Objective objective) {
		String raw = objective.target();
		boolean tag = raw.startsWith("#");
		Identifier id = Identifier.tryParse(tag ? raw.substring(1) : raw);
		if (id != null && !tag) {
			if (objective.event().equals("entity_kill")) {
				var entity = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
				if (entity.isPresent())
					return entity.get().getDescription().getString();
			}
			if (BuiltInRegistries.ITEM.containsKey(id))
				return new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName().getString();
			if (BuiltInRegistries.BLOCK.containsKey(id))
				return BuiltInRegistries.BLOCK.getValue(id).getName().getString();
		}
		return id == null ? raw : id.getPath().replace('_', ' ').replace('/', ' ');
	}
}
