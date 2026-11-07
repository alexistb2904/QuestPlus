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

	private static long arguments(String value) {
		return java.util.regex.Pattern.compile("%s").matcher(value).results().count();
	}
}
