package dev.questplus.clientdata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientLanguageTest {
	private JsonObject read(String language) throws Exception {
		var resource = getClass().getResourceAsStream("/assets/questplus/lang/" + language + ".json");
		assertNotNull(resource, language);
		try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	@Test
	void frenchAndEnglishCoverTheSameClientUiAndFormattingArguments() throws Exception {
		var english = read("en_us");
		var french = read("fr_fr");
		Set<String> englishClient = english.keySet().stream().filter(k -> k.startsWith("questplus.client."))
				.collect(java.util.stream.Collectors.toSet());
		Set<String> frenchClient = french.keySet().stream().filter(k -> k.startsWith("questplus.client."))
				.collect(java.util.stream.Collectors.toSet());
		assertFalse(englishClient.isEmpty());
		assertEquals(englishClient, frenchClient);
		for (String key : englishClient) {
			String en = english.get(key).getAsString(), fr = french.get(key).getAsString();
			assertFalse(en.isBlank(), key);
			assertFalse(fr.isBlank(), key);
			assertEquals(arguments(en), arguments(fr), "format arguments for " + key);
		}
	}

	@Test
	void teamControlsAndRolesAreTranslatedInAllFourLocales() throws Exception {
		for (String locale : java.util.List.of("en_us", "fr_fr", "es_es", "zh_cn")) {
			var translations = read(locale);
			for (String key : java.util.List.of("team_options", "promote", "demote", "rename", "revoke",
					"role_owner", "role_manager", "role_member", "sharing_on", "sharing_off", "share_on", "share_off",
					"scope_team", "scope_personal", "sent_invitations", "sharing_hint")) {
				assertTrue(translations.has("questplus.client." + key), locale + ": " + key);
				assertFalse(translations.get("questplus.client." + key).getAsString().isBlank(), locale + ": " + key);
			}
		}
	}

	@Test
	void creativeTabAndTabletUseQuestPlusBrandInEveryLanguage() throws Exception {
		var tablets = java.util.Map.of(
				"en_us", "Quest Tablet",
				"fr_fr", "Tablette Quest",
				"es_es", "Tableta Quest",
				"zh_cn", "Quest 任务平板");
		for (var entry : tablets.entrySet()) {
			var translations = read(entry.getKey());
			assertEquals("Quest Plus", translations.get("itemGroup.questplus.quest").getAsString(), entry.getKey());
			assertEquals(entry.getValue(), translations.get("item.questplus.tablet").getAsString(), entry.getKey());
		}
	}

	private static long arguments(String value) {
		return java.util.regex.Pattern.compile("%s").matcher(value).results().count();
	}
}
