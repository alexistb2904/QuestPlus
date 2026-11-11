package dev.questplus.fabric.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LootCrateAssetTest {
	@Test
	void crateModelHasHingedLidGlowingGemAndNoZFighting() throws Exception {
		var boxes = QuestLootChestRenderer.load(JsonParser
				.parseString(Files.readString(Path.of("client/src/main/resources/assets/questplus/models/item/loot_crate.json")))
				.getAsJsonObject());
		assertTrue(boxes.keySet().stream().anyMatch(n -> n.startsWith("gem")), "gem glows in the reveal");
		boxes.forEach((name, box) -> {
			if (name.startsWith("lid"))
				assertTrue(box.to()[2] <= 14, name + " must stay in front of the hinge at z=14");
			for (float[] uv : box.uv())
				assertNotNull(uv, name + " has an untextured face");
		});
		var list = new ArrayList<>(boxes.entrySet());
		for (int i = 0; i < list.size(); i++)
			for (int j = i + 1; j < list.size(); j++)
				for (int axis = 0; axis < 3; axis++)
					for (int side = 0; side < 2; side++) {
						var a = list.get(i).getValue();
						var b = list.get(j).getValue();
						float pa = side == 0 ? a.from()[axis] : a.to()[axis], pb = side == 0 ? b.from()[axis] : b.to()[axis];
						assertFalse(Math.abs(pa - pb) < 1e-4 && overlap(a, b, (axis + 1) % 3) && overlap(a, b, (axis + 2) % 3),
								list.get(i).getKey() + " and " + list.get(j).getKey() + " z-fight on axis " + axis);
					}
	}

	private static boolean overlap(QuestLootChestRenderer.Box a, QuestLootChestRenderer.Box b, int axis) {
		return Math.min(a.to()[axis], b.to()[axis]) - Math.max(a.from()[axis], b.from()[axis]) > 1e-4;
	}
}
