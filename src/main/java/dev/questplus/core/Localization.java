package dev.questplus.core;
import java.util.*;
public final class Localization {
    private Localization() {}
    public static String text(Map<String,String> values, String locale, Map<String,String> placeholders) {
        String result = values.getOrDefault(locale, values.getOrDefault("en_us", ""));
        for (var entry : placeholders.entrySet()) result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        return result;
    }
}
