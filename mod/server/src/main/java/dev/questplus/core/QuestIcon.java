package dev.questplus.core;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Validates compact PNG icons before they can enter a quest projection. */
public final class QuestIcon {
	private static final String PREFIX = "data:image/png;base64,";

	private QuestIcon() {
	}

	public static boolean isImage(String value) {
		return value != null && value.startsWith(PREFIX);
	}

	public static void validateImage(String value) {
		if (!isImage(value) || value.length() > 21870
				|| !value.substring(PREFIX.length()).matches("[A-Za-z0-9+/]+={0,2}"))
			throw new IllegalArgumentException("Invalid PNG icon");
		try {
			byte[] data = Base64.getDecoder().decode(value.substring(PREFIX.length()));
			if (data.length > 16384 || data.length < 24 || data[0] != (byte) 0x89 || data[1] != 0x50 || data[2] != 0x4e
					|| data[3] != 0x47 || data[4] != 13 || data[5] != 10 || data[6] != 26 || data[7] != 10)
				throw new IllegalArgumentException("Invalid PNG icon");
			try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
				var readers = ImageIO.getImageReaders(stream);
				if (!readers.hasNext())
					throw new IllegalArgumentException("Invalid PNG icon");
				var reader = readers.next();
				try {
					reader.setInput(stream);
					int width = reader.getWidth(0), height = reader.getHeight(0);
					if (width < 1 || height < 1 || width > 128 || height > 128 || reader.read(0) == null)
						throw new IllegalArgumentException("PNG icon must be at most 128 × 128");
				} finally {
					reader.dispose();
				}
			}
		} catch (java.io.IOException | IllegalArgumentException error) {
			throw new IllegalArgumentException("Invalid PNG icon", error);
		}
	}
}
