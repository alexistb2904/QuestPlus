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
		assertTrue(QuestHubService.responseError(403, "<html>Attention Required! | Cloudflare</html>").contains("Cloudflare"));
		assertTrue(QuestHubService.responseError(403, "{\"error\":\"Editor not authorized\"}").contains("Editor not authorized"));
		assertTrue(QuestHubService.responseError(429, "{}").contains("retry"));
		assertTrue(QuestHubService.responseError(503, "<html>offline</html>").contains("unavailable"));
		assertTrue(QuestHubService.responseError(404, "{\"error\":\"Session expired\"}").contains("expired"));
		assertEquals("Missing quests, categories or config", QuestHubService.responseError(400,
				"{\"error\":\"Missing quests, categories or config\"}"));
		assertFalse(QuestHubService.responseError(502, "<html>secret proxy details</html>").contains("secret"));
	}

	@Test
	void authenticationIsScopedToAnIndividualSession() {
		String session = "session-owned-token-not-visible-in-the-browser";
		assertNull(QuestHubService.credentialFor("/api/server/sessions", null));
		assertNull(QuestHubService.credentialFor("/api/server/sessions", session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/poll", session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/approve", session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/revoke", session));
		assertEquals(session, QuestHubService.credentialFor("/api/server/ack", session));
		assertNull(QuestHubService.credentialFor("/api/packs/example", session));
		assertNull(QuestHubService.credentialFor("/api/editor/sessions/example", session));
		assertNull(QuestHubService.credentialFor("/api/server/poll", null));
	}

	@Test
	void httpRequestsMatchTheTokenOnlyHubProtocol() {
		URI hub = URI.create("https://quest.alexistb.com/");
		var data = new com.google.gson.JsonObject();
		data.addProperty("serverName", "Test");
		String token = "s".repeat(43);

		var create = QuestHubService.buildRequest(hub, "POST", "/api/server/sessions", data, token);
		assertEquals("POST", create.method());
		assertEquals(URI.create("https://quest.alexistb.com/api/server/sessions"), create.uri());
		assertTrue(create.headers().firstValue("Authorization").isEmpty());
		assertEquals("application/json; charset=utf-8", create.headers().firstValue("Content-Type").orElseThrow());
		assertTrue(create.bodyPublisher().isPresent());

		var poll = QuestHubService.buildRequest(hub, "GET", "/api/server/poll?session=" + "a".repeat(32), null, token);
		assertEquals("GET", poll.method());
		assertTrue(poll.bodyPublisher().isEmpty());
		assertEquals("Bearer " + token, poll.headers().firstValue("Authorization").orElseThrow());

		for (String endpoint : new String[] { "/api/server/approve", "/api/server/ack", "/api/server/revoke" }) {
			var request = QuestHubService.buildRequest(hub, "POST", endpoint, data, token);
			assertEquals("Bearer " + token, request.headers().firstValue("Authorization").orElseThrow());
		}
		assertTrue(QuestHubService.buildRequest(hub, "GET", "/api/packs/" + "a".repeat(32), null, token)
				.headers().firstValue("Authorization").isEmpty());
	}

	@Test
	void sessionCreationOnlyAcceptsTheCurrentApiResponse() {
		String id = "i".repeat(32), token = "t".repeat(43);
		var response = new com.google.gson.JsonObject();
		response.addProperty("id", id);
		response.addProperty("serverToken", token);
		response.addProperty("url", "https://quest.alexistb.com/?hub=https%3A%2F%2Fquest.alexistb.com&session=" + id);
		var parsed = QuestHubService.parseSession(response);
		assertEquals(id, parsed.id());
		assertEquals(token, parsed.token());

		var oldApi = response.deepCopy();
		oldApi.remove("serverToken");
		assertThrows(IllegalArgumentException.class, () -> QuestHubService.parseSession(oldApi));
		var invalidToken = response.deepCopy();
		invalidToken.addProperty("serverToken", "not-a-session-token");
		assertThrows(IllegalArgumentException.class, () -> QuestHubService.parseSession(invalidToken));
		var mismatchedUrl = response.deepCopy();
		mismatchedUrl.addProperty("url", "https://quest.alexistb.com/?session=" + "x".repeat(32));
		assertThrows(IllegalArgumentException.class, () -> QuestHubService.parseSession(mismatchedUrl));
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
