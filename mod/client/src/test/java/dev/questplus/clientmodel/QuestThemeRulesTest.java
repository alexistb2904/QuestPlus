package dev.questplus.clientmodel;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestThemeRulesTest {
	@Test
	void globalTagAndOriginalFtbIdsResolveWithoutExecutingContent() {
		var rules = QuestThemeRules.parse("""
				[*]
				base: #112233
				text_color: {{base}}
				[#mining]
				text_color: #445566
				[ABCDEF0123456789]
				text_color: #aabbcc
				""");
		assertEquals(0xff112233, QuestThemeRules.color(rules.properties("test:other", List.of()), "text_color", 0));
		assertEquals(0xff445566,
				QuestThemeRules.color(rules.properties("test:other", List.of("mining")), "text_color", 0));
		assertEquals(0xffaabbcc, QuestThemeRules
				.color(rules.properties("questplus:ftb/chapter/abcdef0123456789", List.of("mining")), "text_color", 0));
		assertEquals(42, QuestThemeRules.color(Map.of("a", "{{b}}", "b", "{{a}}"), "a", 42));
		assertThrows(IllegalArgumentException.class, () -> QuestThemeRules.parse("x".repeat(65537)));
		assertThrows(IllegalArgumentException.class, () -> QuestThemeRules.parse("<script>run()</script>"));
	}

	@Test
	void customShapeMaskIsBoundedAndUsedForHitTesting() {
		String name = "test:cutout";
		try {
			QuestShape.install(name, List.of(new QuestShape.Span(0, 8, 16), new QuestShape.Span(24, 32, 16)));
			assertTrue(QuestShape.contains(name, -.75, 0));
			assertFalse(QuestShape.contains(name, 0, 0));
			assertTrue(QuestShape.contains(name, .75, 0));
			assertFalse(QuestShape.contains(name, .75, .75));
			assertThrows(IllegalArgumentException.class,
					() -> QuestShape.install(name, List.of(new QuestShape.Span(0, 33, 0))));
		} finally {
			QuestShape.removeCustom(name);
		}
		assertTrue(QuestShape.contains(name, 0, 0));
	}
}
