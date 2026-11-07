package dev.questplus.network;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StateTransfersTest {
	@Test
	void unicodeFragmentsRespectBothBudgetsAndNeverInterleaveNotices() {
		var queue = new StateTransfers();
		var a = UUID.randomUUID();
		var b = UUID.randomUUID();
		String data = "é中😀a".repeat(12000);
		assertEquals(data.getBytes(StandardCharsets.UTF_8).length, StateTransfers.utf8Bytes(data));
		assertTrue(queue.offer(a, data));
		assertTrue(queue.offer(a, "notice"));
		assertTrue(queue.offer(b, "other"));
		Map<UUID, List<StateTransfers.Fragment>> received = new HashMap<>();
		for (int tick = 0; queue.queuedChars() > 0 && tick < 100; tick++) {
			int[] total = { 0 };
			Map<UUID, Integer> perPlayer = new HashMap<>();
			queue.drain(48 * 1024, 24 * 1024, (id, part) -> {
				int bytes = part.data().getBytes(StandardCharsets.UTF_8).length + 32;
				total[0] += bytes;
				perPlayer.merge(id, bytes, Integer::sum);
				received.computeIfAbsent(id, k -> new ArrayList<>()).add(part);
				assertTrue(part.data().length() <= 6000);
				assertFalse(Character.isHighSurrogate(part.data().charAt(part.data().length() - 1)));
			});
			assertTrue(total[0] <= 48 * 1024);
			perPlayer.values().forEach(bytes -> assertTrue(bytes <= 24 * 1024));
			if (tick == 0)
				assertEquals("other", received.get(b).getFirst().data());
		}
		assertEquals(0, queue.queuedChars());
		var parts = received.get(a);
		assertEquals("notice", parts.getLast().data());
		var state = parts.subList(0, parts.size() - 1);
		assertEquals(data, String.join("", state.stream().map(StateTransfers.Fragment::data).toList()));
		for (int i = 0; i < state.size(); i++) {
			assertEquals(i, state.get(i).index());
			assertEquals(state.size(), state.get(i).total());
			assertEquals(state.getFirst().transfer(), state.get(i).transfer());
		}
	}

	@Test
	void admissionAndDisconnectBoundRetainedMemory() {
		var queue = new StateTransfers();
		var id = UUID.randomUUID();
		String large = "x".repeat(17 * 1024 * 1024);
		assertTrue(queue.offer(id, large));
		assertFalse(queue.offer(UUID.randomUUID(), large));
		queue.remove(id);
		assertEquals(0, queue.queuedChars());
		for (int i = 0; i < 16; i++)
			assertTrue(queue.offer(id, "small"));
		assertFalse(queue.offer(id, "extra"));
		queue.clear();
		assertEquals(0, queue.queuedChars());
		assertFalse(queue.hasPending(id));
		assertFalse(queue.offer(id, "x".repeat(StateTransfers.MAX_MESSAGE_CHARS + 1)));
	}

	@Test
	void aThousandRecipientsMakeProgressUnderTheGlobalBudget() {
		var queue = new StateTransfers();
		Set<UUID> expected = new HashSet<>(), sent = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			var id = new UUID(0, i);
			expected.add(id);
			assertTrue(queue.offer(id, "x".repeat(6000)));
		}
		for (int tick = 0; tick < 25 && queue.queuedChars() > 0; tick++)
			queue.drain(256 * 1024, 64 * 1024, (id, part) -> assertTrue(sent.add(id)));
		assertEquals(expected, sent);
		assertEquals(0, queue.queuedChars());
	}
}
