package net.minecraft.util;

import java.util.regex.Pattern;

public enum EnumChatFormatting {
    BLACK('0', 0),
    DARK_BLUE('1', 1),
    DARK_GREEN('2', 2),
    DARK_AQUA('3', 3),
    DARK_RED('4', 4),
    DARK_PURPLE('5', 5),
    GOLD('6', 6),
    GRAY('7', 7),
    DARK_GRAY('8', 8),
    BLUE('9', 9),
    GREEN('a', 10),
    AQUA('b', 11),
    RED('c', 12),
    LIGHT_PURPLE('d', 13),
    YELLOW('e', 14),
    WHITE('f', 15),
    OBFUSCATED('k', -1),
    BOLD('l', -1),
    STRIKETHROUGH('m', -1),
    UNDERLINE('n', -1),
    ITALIC('o', -1),
    RESET('r', -1);

    private static final Pattern FORMATTING_CODE = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private final char code;
    private final int colorIndex;

    EnumChatFormatting(char code, int colorIndex) {
        this.code = code;
        this.colorIndex = colorIndex;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public boolean isColor() {
        return colorIndex >= 0;
    }

    public boolean isFancyStyling() {
        return !isColor() && this != RESET;
    }

    public String getFriendlyName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static String getTextWithoutFormattingCodes(String text) {
        return text == null ? null : FORMATTING_CODE.matcher(text).replaceAll("");
    }

    @Override
    public String toString() {
        return "\u00A7" + code;
    }
}
