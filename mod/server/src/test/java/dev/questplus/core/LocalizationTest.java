package dev.questplus.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class LocalizationTest {
	@Test
	void usesAnyAuthoredLocaleWhenPreferredAndEnglishAreMissing() {
		assertEquals("Bonjour", Localization.text(Map.of("fr_fr", "Bonjour"), "en_us", Map.of()));
		assertEquals("Español", Localization.text(Map.of("es_es", "Español", "en_us", " "), "zh_cn", Map.of()));
		assertEquals("Hello", Localization.text(Map.of("es_es", "Hola", "en_us", "Hello"), "zh_cn", Map.of()));
		assertEquals("Hola", Localization.text(Map.of("es_es", "Hola", "en_us", "Hello"), "es_es", Map.of()));
		assertEquals("", Localization.text(Map.of("en_us", ""), "fr_fr", Map.of()));
	}

	@Test
	void substitutionsStillWorkForFallbackStrings() {
		assertEquals("Bravo Alex", Localization.text(Map.of("fr_fr", "Bravo {name}"), "zh_cn", Map.of("name", "Alex")));
	}
}
