package net.minecraft.client.resources;

public final class I18n {

    private static final Locale LOCALE = new Locale();

    private I18n() {}

    public static String format(String key, Object... arguments) {
        return LOCALE.formatMessage(key, arguments);
    }
}
