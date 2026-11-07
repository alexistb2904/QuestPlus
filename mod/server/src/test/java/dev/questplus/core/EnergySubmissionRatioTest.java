package dev.questplus.core;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnergySubmissionRatioTest {
	@TempDir
	Path root;

	private QuestRegistry load(int count, String tiers) throws Exception {
		Files.writeString(root.resolve("energy.json5"), """
				{schemaVersion:2,id:'test:energy',category:'test:category',icon:'minecraft:stone',title:{en_us:'Energy'},
				 progression:'%s',objectives:[{id:'energy',event:'energy_submit',target:'questplus:fe',count:%d,
				 data:{fePerEnergy:10}}],rewards:[]%s}
				""".formatted(tiers.isEmpty() ? "once" : "tiered", count, tiers));
		return QuestRegistry.load(root, (kind, id) -> {});
	}

	@Test
	void rejectsTargetsThatCannotBePaidInWholeNativeEnergyUnits() throws Exception {
		assertDoesNotThrow(() -> load(100, ""));
		assertThrows(IllegalArgumentException.class, () -> load(101, ""));
		assertThrows(IllegalArgumentException.class, () -> load(1, ""));
	}

	@Test
	void checksActualTierTargetsRatherThanTheUnscaledBaseCount() throws Exception {
		assertDoesNotThrow(() -> load(1, ",tiers:[{id:'first',threshold:10,rewards:[]},{id:'second',threshold:20,rewards:[]}]"));
		assertThrows(IllegalArgumentException.class, () -> load(1,
				",tiers:[{id:'first',threshold:10,rewards:[]},{id:'second',threshold:11,rewards:[]}]"));
	}
}
