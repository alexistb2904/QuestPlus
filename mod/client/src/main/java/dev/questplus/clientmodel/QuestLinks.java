package dev.questplus.clientmodel;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class QuestLinks {
	private static final Pattern URL = Pattern.compile("https://[^\\s§<>\"']+");

	public record Link(int start, int end, URI uri) {
	}

	private QuestLinks() {
	}

	public static List<Link> find(String text) {
		List<Link> out = new ArrayList<>();
		var matcher = URL.matcher(text);
		while (matcher.find()) {
			int end = matcher.end();
			while (".,:;!?)]".indexOf(text.charAt(end - 1)) >= 0)
				end--;
			var uri = parse(text.substring(matcher.start(), end));
			if (uri != null)
				out.add(new Link(matcher.start(), end, uri));
		}
		return out;
	}

	public static URI parse(String url) {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			return null;
		}
		return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null ? uri : null;
	}
}
