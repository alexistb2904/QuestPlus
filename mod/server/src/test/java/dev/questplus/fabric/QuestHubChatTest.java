package dev.questplus.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class QuestHubChatTest {
	@Test
	void unavailableLocalApiExplainsHowToEnableSoloEditing() {
		var error = new java.util.concurrent.CompletionException(new java.net.ConnectException());
		String message = QuestHubService.requestError(URI.create("http://localhost:8787"), error);
		assertTrue(message.contains("http://localhost:8787"));
		assertTrue(message.contains("dev.bat api"));
		assertTrue(message.contains("Solo"));
		assertFalse(message.contains("ConnectException"));
		String remote = QuestHubService.requestError(URI.create("https://quests.example.com"), error);
		assertTrue(remote.contains("https://quests.example.com"));
		assertFalse(remote.contains("dev.bat"));
		assertFalse(QuestHubService.requestError(URI.create("http://localhost:8787"),
				new java.util.concurrent.CompletionException(new IllegalStateException("test"))).contains("IllegalStateException"));
	}

	@Test
	void releaseDefaultUsesTheHostedEditorAndExplicitConfigurationStillWins() {
		assertEquals(URI.create("https://quest.alexistb.com/"), QuestHubService.configuredOrigin(new com.google.gson.JsonObject()));
		var config = new com.google.gson.JsonObject();
		config.addProperty("url", "http://localhost:8787");
		assertEquals(URI.create("http://localhost:8787"), QuestHubService.configuredOrigin(config));
		for (String invalid : new String[] { "http://quests.example.com", "https://", "https://user:secret@example.com",
				"https://example.com/api", "https://example.com?key=secret", "file:///tmp" }) {
			config.addProperty("url", invalid);
			assertThrows(IllegalArgumentException.class, () -> QuestHubService.configuredOrigin(config), invalid);
		}
	}

	@Test
	void networkErrorsExplainDnsTimeoutAndTlsWithoutJavaClassNames() {
		URI origin = URI.create("https://quest.alexistb.com/");
		assertTrue(QuestHubService.requestError(origin, new java.net.UnknownHostException()).contains("DNS"));
		assertTrue(QuestHubService.requestError(origin, new java.nio.channels.UnresolvedAddressException()).contains("DNS"));
		assertTrue(QuestHubService.requestError(origin, new java.net.http.HttpTimeoutException("test")).contains("timed out"));
		assertTrue(QuestHubService.requestError(origin, new javax.net.ssl.SSLHandshakeException("test")).contains("certificate"));
		assertFalse(QuestHubService.requestError(origin, new RuntimeException("sensitive details")).contains("sensitive details"));
	}

	@Test
	void proxyAndAuthenticationFailuresStayReadableEvenWhenTheResponseIsHtml() {
		assertTrue(QuestHubService.responseError(401, "<html>denied</html>").contains("session credentials"));
		assertTrue(QuestHubService.responseError(403, "{}").contains("denied"));
		assertTrue(QuestHubService.responseError(429, "{}").contains("retry"));
		assertTrue(QuestHubService.responseError(503, "<html>offline</html>").contains("unavailable"));
		assertTrue(QuestHubService.responseError(404, "{\"error\":\"Session expired\"}").contains("expired"));
		assertEquals("Missing quests, categories or config", QuestHubService.responseError(400,
				"{\"error\":\"Missing quests, categories or config\"}"));
		assertFalse(QuestHubService.responseError(502, "<html>secret proxy details</html>").contains("secret"));
	}


	@Test
	void authenticationIsScopedToAnIndividualSession() {
		String legacy = "optional-self-hosted-legacy-key-of-32-characters";
		String session = "session-owned-token-not-visible-in-the-browser";
		assertNull(QuestHubService.credentialFor("/api/server/sessions", null, null));
		assertEquals(legacy, QuestHubService.credentialFor("/api/server/sessions", legacy, null));
		assertEquals(session, QuestHubService.credentialFor("/api/server/poll", legacy, session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/approve", legacy, session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/revoke", legacy, session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/ack", legacy, session));
		assertNull(QuestHubService.credentialFor("/api/packs/example", legacy, session));
		assertNull(QuestHubService.credentialFor("/api/editor/sessions/example", legacy, session));
		assertNull(QuestHubService.credentialFor("/api/server/poll", legacy, null));
	}

	@Test
	void editorLinkOpensTheWebsiteWithColorAndHoverText() {
		String url = "https://quests.example.com/?session=abc";
		var message = QuestHubService.editorLink(url);
		var link = message.getSiblings().getFirst();
		assertEquals(new ClickEvent.OpenUrl(URI.create(url)), link.getStyle().getClickEvent());
		assertEquals(0x55ffff, link.getStyle().getColor().getValue());
		assertTrue(link.getStyle().isUnderlined());
		assertNotNull(link.getStyle().getHoverEvent());
		assertThrows(IllegalArgumentException.class, () -> QuestHubService.editorLink("javascript:alert(1)"));
	}

	@Test
	void approvalFillsTheCommandAndKeepsTheOperatorInControl() {
		String command = "/questplus editor approve 123456";
		var action = QuestHubService.commandLink(net.minecraft.network.chat.Component.literal("Approve"), command);
		assertEquals(new ClickEvent.SuggestCommand(command), action.getStyle().getClickEvent());
		var message = QuestHubService.chat(action);
		assertEquals("[QuestPlus] Approve", message.getString());
		assertEquals(0xffaa00, message.getStyle().getColor().getValue());
	}
}
