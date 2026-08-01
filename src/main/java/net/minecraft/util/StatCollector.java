package net.minecraft.util;

public final class StatCollector {

    private StatCollector() {}

    public static boolean canTranslate(String key) {
        return false;
    }

    public static String translateToLocal(String key) {
        return key;
    }
}
