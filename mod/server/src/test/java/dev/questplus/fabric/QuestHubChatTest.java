package dev.questplus.fabric;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class QuestHubChatTest {
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
