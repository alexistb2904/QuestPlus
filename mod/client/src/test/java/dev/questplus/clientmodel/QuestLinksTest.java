package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestLinksTest {
	@Test
	void findsLinksAndLeavesSentencePunctuationOut() {
		String text = "Read https://minecraft.wiki/w/Nether_portal, then (https://docs.example.org/guide).";
		var links = QuestLinks.find(text);
		assertEquals(List.of(URI.create("https://minecraft.wiki/w/Nether_portal"),
				URI.create("https://docs.example.org/guide")), links.stream().map(QuestLinks.Link::uri).toList());
		for (var link : links)
			assertEquals(link.uri().toString(), text.substring(link.start(), link.end()));
	}

	@Test
	void onlyPlainHttpsWithARealHostCanBeOpened() {
		assertNotNull(QuestLinks.parse("https://modrinth.com/mod/create"));
		assertNotNull(QuestLinks.parse("https://WWW.YouTube.com/watch?v=abc"));
		for (String url : List.of("http://minecraft.wiki/w/Stone", "https://github.com@evil.com/",
				"https://evil.com\\@github.com/", "file:///C:/Windows", "https:///github.com", "javascript:alert(1)"))
			assertNull(QuestLinks.parse(url), url);
		assertTrue(QuestLinks.find("See http://minecraft.wiki and ftp://example.com/x for more.").isEmpty());
	}
}
