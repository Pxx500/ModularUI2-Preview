package net.minecraft.client.resources;

import java.util.IllegalFormatException;
import net.minecraft.util.StatCollector;

public class Locale {

    public String formatMessage(String key, Object... arguments) {
        String translated = StatCollector.translateToLocal(key);
        try {
            return String.format(translated, arguments);
        } catch (IllegalFormatException exception) {
            return translated;
        }
    }
}
