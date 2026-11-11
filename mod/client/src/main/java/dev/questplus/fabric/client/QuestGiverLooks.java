package dev.questplus.fabric.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.questplus.fabric.QuestGiverNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.util.*;

/** Each NPC's appearance as the server last sent it; custom skins live as dynamic textures. */
final class QuestGiverLooks {
	record Look(boolean villager, String villagerType, Identifier skin) {
	}

	static final Identifier DEFAULT_SKIN = Identifier.fromNamespaceAndPath("questplus", "textures/entity/quest_giver.png");
	private static final Look DEFAULT = new Look(true, "plains", DEFAULT_SKIN);
	private static final Map<String, Look> LOOKS = new HashMap<>();

	private QuestGiverLooks() {
	}

	static Look of(String npc) {
		return LOOKS.getOrDefault(npc, DEFAULT);
	}

	static void receive(QuestGiverNetwork.Look packet) {
		if (packet.npc().isEmpty()) {
			clear();
			return;
		}
		var data = JsonParser.parseString(packet.data()).getAsJsonObject();
		Identifier skin = DEFAULT_SKIN;
		if (data.has("skin")) {
			skin = Identifier.fromNamespaceAndPath("questplus", "npc_skin/" + packet.npc());
			try {
				byte[] png = Base64.getDecoder().decode(data.get("skin").getAsString().substring("data:image/png;base64,".length()));
				var image = NativeImage.read(png);
				Minecraft.getInstance().getTextureManager().register(skin, new DynamicTexture(() -> "QuestPlus NPC skin", image));
			} catch (Exception unreadable) {
				// The server validated the PNG; a client that still cannot read it falls back to the mod's skin.
				skin = DEFAULT_SKIN;
			}
		}
		release(LOOKS.put(packet.npc(), new Look(data.get("model").getAsString().equals("villager"),
				data.get("villager").getAsString(), skin)), skin);
	}

	static void clear() {
		LOOKS.values().forEach(look -> release(look, null));
		LOOKS.clear();
	}

	private static void release(Look old, Identifier kept) {
		if (old != null && !old.skin().equals(DEFAULT_SKIN) && !old.skin().equals(kept))
			Minecraft.getInstance().getTextureManager().release(old.skin());
	}
}
