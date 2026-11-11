package dev.questplus.fabric.client;

import dev.questplus.core.QuestDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Resolves the same localized objective target for the tablet and pinned HUD. */
final class QuestObjectiveNames {
	private QuestObjectiveNames() {
	}

	/** The objective's item: a block tag's first block, a spawn egg for a kill, else the target item or {@code fallback}. */
	static ItemStack icon(QuestDefinition.Objective o, java.util.function.Supplier<ItemStack> fallback) {
		var id = Identifier.tryParse(o.target().startsWith("#") ? o.target().substring(1) : o.target());
		var level = net.minecraft.client.Minecraft.getInstance().level;
		if (id != null && o.target().startsWith("#") && level != null) {
			var tag = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK)
					.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id));
			if (tag.isPresent())
				for (var holder : tag.get()) {
					var item = holder.value().asItem();
					if (item != net.minecraft.world.item.Items.AIR)
						return new ItemStack(item);
				}
		}
		if (id != null && o.event().equals("entity_kill")) {
			var entity = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
			if (entity.isPresent()) {
				var egg = net.minecraft.world.item.SpawnEggItem.byId(entity.get());
				if (egg.isPresent())
					return new ItemStack(egg.get().value());
			}
		}
		return id != null && !o.target().startsWith("#") && BuiltInRegistries.ITEM.containsKey(id)
				? new ItemStack(BuiltInRegistries.ITEM.getValue(id))
				: fallback.get();
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
		if (id != null && !tag && objective.event().equals("biome_visit")) {
			String key = "biome." + id.getNamespace() + "." + id.getPath();
			var language = net.minecraft.locale.Language.getInstance();
			if (language.has(key))
				return language.getOrDefault(key);
		}
		return id == null ? raw : id.getPath().replace('_', ' ').replace('/', ' ');
	}
}
