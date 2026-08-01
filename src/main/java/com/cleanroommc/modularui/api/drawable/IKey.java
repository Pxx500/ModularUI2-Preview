package com.cleanroommc.modularui.api.drawable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import com.cleanroommc.modularui.widgets.TextWidget;

public interface IKey {

    static IKey str(String text) {
        return new StringKey(text);
    }

    static IKey lang(String key) {
        return new LangKey(key, new Object[0]);
    }

    static IKey lang(String key, Object... arguments) {
        return new LangKey(key, arguments);
    }

    String get();

    default String getFormatted() {
        return get();
    }

    default TextWidget<?> asWidget() {
        return new TextWidget<>(this);
    }

    final class StringKey implements IKey {

        private final String text;

        private StringKey(String text) {
            this.text = text;
        }

        public String get() {
            return text;
        }
    }

    final class LangKey implements IKey {

        private static final Properties TRANSLATIONS = loadTranslations();

        private final String key;
        private final Object[] arguments;

        private LangKey(String key, Object[] arguments) {
            this.key = key;
            this.arguments = arguments.clone();
        }

        @Override
        public String get() {
            String translated = TRANSLATIONS.getProperty(key, key);
            return arguments.length == 0 ? translated : String.format(Locale.ROOT, translated, arguments);
        }

        private static Properties loadTranslations() {
            Properties translations = new Properties();
            try (var stream = IKey.class.getResourceAsStream("/assets/galaxia/lang/en_US.lang")) {
                if (stream != null) {
                    translations.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not load preview translations", exception);
            }
            return translations;
        }
    }
}
