package dev.questplus.persistence;

import com.google.gson.JsonObject;
import java.util.UUID;

/** A block's resources share the quest transaction journal, not chunk-save timing. */
public record BlockAccount(int schemaVersion, UUID id, String dimension, long position, JsonObject data) {
    public BlockAccount {
        if (schemaVersion != 1 || id == null || dimension == null
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || data == null
                || data.toString().length() > 65536)
            throw new IllegalArgumentException("Invalid block account");
        data = data.deepCopy();
    }
}
