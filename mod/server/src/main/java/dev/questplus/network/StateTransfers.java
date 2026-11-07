package dev.questplus.network;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Server-thread queue: a recipient's fragments never interleave, including
 * notices.
 */
public final class StateTransfers {
	public static final int CHARS = 6000, MAX_MESSAGE_CHARS = 24 * 1024 * 1024;
	private static final int MAX_QUEUED_CHARS = 32 * 1024 * 1024;

	public record Fragment(long transfer, int index, int total, String data) {
	}

	private static final class Transfer {
		final String data;
		final long id;
		final int total;
		int offset, index;

		Transfer(String data, long id) {
			this.data = data;
			this.id = id;
			int count = 0;
			for (int start = 0; start < data.length(); count++)
				start = end(data, start);
			total = count;
		}
	}

	private final LinkedHashMap<UUID, ArrayDeque<Transfer>> pending = new LinkedHashMap<>();
	private int chars;
	private long sequence;

	public boolean hasPending(UUID id) {
		return pending.containsKey(id);
	}

	public int queuedChars() {
		return chars;
	}

	public boolean offer(UUID id, String data) {
		var queue = pending.get(id);
		if (data.isEmpty() || data.length() > MAX_MESSAGE_CHARS || chars + data.length() > MAX_QUEUED_CHARS
				|| queue != null && queue.size() >= 16)
			return false;
		if (queue == null) {
			queue = new ArrayDeque<>();
			pending.put(id, queue);
		}
		queue.addLast(new Transfer(data, ++sequence));
		chars += data.length();
		return true;
	}

	public void remove(UUID id) {
		var queue = pending.remove(id);
		if (queue != null)
			for (var item : queue)
				chars -= item.data.length();
	}

	public void clear() {
		pending.clear();
		chars = 0;
	}

	public void drain(int bytes, int perPlayerBytes, BiConsumer<UUID, Fragment> sender) {
		int visited = 0, count = pending.size(), fragments = 0;
		while (!pending.isEmpty() && visited++ < count && fragments < 256) {
			var iterator = pending.entrySet().iterator();
			var entry = iterator.next();
			iterator.remove();
			var queue = entry.getValue();
			int playerBytes = 0;
			try {
				while (!queue.isEmpty() && fragments < 256) {
					var item = queue.getFirst();
					int end = end(item.data, item.offset);
					String part = item.data.substring(item.offset, end);
					int cost = utf8Bytes(part) + 32;
					if (cost > bytes || playerBytes + cost > perPlayerBytes)
						break;
					sender.accept(entry.getKey(), new Fragment(item.id, item.index, item.total, part));
					item.offset = end;
					item.index++;
					bytes -= cost;
					playerBytes += cost;
					fragments++;
					if (end == item.data.length()) {
						queue.removeFirst();
						chars -= item.data.length();
					}
				}
			} finally {
				if (!queue.isEmpty())
					pending.put(entry.getKey(), queue);
			}
		}
	}

	private static int end(String data, int start) {
		int end = Math.min(data.length(), start + CHARS);
		if (end < data.length() && Character.isHighSurrogate(data.charAt(end - 1)))
			end--;
		return end;
	}

	public static int utf8Bytes(String text) {
		int bytes = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < 128)
				bytes++;
			else if (c < 2048)
				bytes += 2;
			else if (Character.isHighSurrogate(c) && i + 1 < text.length()
					&& Character.isLowSurrogate(text.charAt(i + 1))) {
				bytes += 4;
				i++;
			} else
				bytes += Character.isSurrogate(c) ? 1 : 3;
		}
		return bytes;
	}
}
