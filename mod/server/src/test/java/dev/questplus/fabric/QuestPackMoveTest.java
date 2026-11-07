package dev.questplus.fabric;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class QuestPackMoveTest {
	@TempDir
	Path root;

	private static OpenOption noShareDelete() throws Exception {
		return (OpenOption) Class.forName("com.sun.nio.file.ExtendedOpenOption").getField("NOSHARE_DELETE").get(null);
	}

	@Test
	void temporaryScannerLockDoesNotDisablePackInstallation() throws Exception {
		Path from = Files.createDirectory(root.resolve("stage")), to = root.resolve("definitions");
		Path content = Files.writeString(from.resolve("config.json"), "original");
		try (var channel = FileChannel.open(content, StandardOpenOption.READ, noShareDelete())) {
			var release = Thread.ofPlatform().start(() -> {
				try {
					Thread.sleep(100);
					channel.close();
				} catch (Exception e) { throw new RuntimeException(e); }
			});
			try { QuestHubFiles.move(from, to); } finally { release.join(); }
		}
		assertFalse(Files.exists(from));
		assertEquals("original", Files.readString(to.resolve("config.json")));
	}

	@Test
	void permanentLockStillFailsWithoutLosingTheSource() throws Exception {
		Path from = Files.createDirectory(root.resolve("stage")), to = root.resolve("definitions");
		Path content = Files.writeString(from.resolve("config.json"), "original");
		try (var channel = FileChannel.open(content, StandardOpenOption.READ, noShareDelete())) {
			assertThrows(AccessDeniedException.class, () -> QuestHubFiles.move(from, to));
		}
		assertEquals("original", Files.readString(content));
		assertFalse(Files.exists(to));
	}
}
