package net.minecraft.util;

import java.util.HashMap;
import java.util.Map;

/** Translation-map boundary retained for production code that accesses Minecraft 1.7.10 through reflection. */
public final class StringTranslate {

    private static final StringTranslate instance = new StringTranslate();
    private final Map<String, String> languageList = new HashMap<>();
}
