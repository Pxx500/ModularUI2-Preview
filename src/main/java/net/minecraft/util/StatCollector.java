package net.minecraft.util;

import java.util.Map;

public final class StatCollector {

    private static final ThreadLocal<Map<String, String>> TRANSLATIONS = ThreadLocal.withInitial(Map::of);
    private static final StringTranslate fallbackTranslator = new StringTranslate();

    private StatCollector() {}

    public static void installTranslations(Map<String, String> translations) {
        TRANSLATIONS.set(Map.copyOf(translations));
    }

    public static void clearTranslations() {
        TRANSLATIONS.remove();
    }

    public static boolean canTranslate(String key) {
        return TRANSLATIONS.get().containsKey(key);
    }

    public static String translateToLocal(String key) {
        return TRANSLATIONS.get().getOrDefault(key, key);
    }

    public static String translateToLocalFormatted(String key, Object... arguments) {
        return String.format(translateToLocal(key), arguments);
    }
}
