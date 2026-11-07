package dev.questplus.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoSyncTest {
	@TempDir
	Path dir;

	@Test
	void globalOptionIsExplicitAndValidated() throws Exception {
		Path config = dir.resolve("questplus/sync.json");
		assertFalse(QuestHubFiles.autoSyncEnabled(config));
		assertTrue(Files.exists(config));
		Files.writeString(config, "{\"autoSyncDefaults\":true}");
		assertTrue(QuestHubFiles.autoSyncEnabled(config));
		Files.writeString(config, "{\"autoSyncDefaults\":\"true\"}");
		assertThrows(Exception.class, () -> QuestHubFiles.autoSyncEnabled(config));
	}
}
