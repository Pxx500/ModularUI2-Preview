package com.cleanroommc.modularui.api.drawable;

import java.util.Locale;

import com.cleanroommc.modularui.widgets.TextWidget;

import net.minecraft.util.StatCollector;

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

        private final String key;
        private final Object[] arguments;

        private LangKey(String key, Object[] arguments) {
            this.key = key;
            this.arguments = arguments.clone();
        }

        @Override
        public String get() {
            String translated = StatCollector.translateToLocal(key);
            return arguments.length == 0 ? translated : String.format(Locale.ROOT, translated, arguments);
        }
    }
}
