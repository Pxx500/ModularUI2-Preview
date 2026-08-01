package net.minecraft.client.gui;

import dev.modularui.preview.PreviewDrawContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.EnumChatFormatting;

public class FontRenderer {

    private static final int DEFAULT_FONT_HEIGHT = 9;
    private static final int ALPHA_MASK = 0xFF000000;
    private static final String COLOR_CODE_KEYS = "0123456789abcdef";
    private static final int[] COLOR_CODES = {
        0x000000,
        0x0000AA,
        0x00AA00,
        0x00AAAA,
        0xAA0000,
        0xAA00AA,
        0xFFAA00,
        0xAAAAAA,
        0x555555,
        0x5555FF,
        0x55FF55,
        0x55FFFF,
        0xFF5555,
        0xFF55FF,
        0xFFFF55,
        0xFFFFFF
    };

    public int FONT_HEIGHT = DEFAULT_FONT_HEIGHT;

    public int getStringWidth(String text) {
        String plainText = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return PreviewDrawContext.stringWidth(plainText == null ? "" : plainText);
    }

    public int drawStringWithShadow(String text, int x, int y, int color) {
        return drawString(text, x, y, color, true);
    }

    public int drawString(String text, int x, int y, int color) {
        return drawString(text, x, y, color, false);
    }

    public int drawString(String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) return x;
        int cursor = x;
        int currentColor = color;
        int segmentStart = 0;
        for (int index = 0; index < text.length() - 1; index++) {
            if (text.charAt(index) != '\u00A7') continue;
            cursor = drawSegment(text.substring(segmentStart, index), cursor, y, currentColor, shadow);
            char code = Character.toLowerCase(text.charAt(index + 1));
            int colorIndex = COLOR_CODE_KEYS.indexOf(code);
            if (colorIndex >= 0) {
                int alpha = color & ALPHA_MASK;
                currentColor = alpha | COLOR_CODES[colorIndex];
            } else if (code == 'r') {
                currentColor = color;
            }
            index++;
            segmentStart = index + 1;
        }
        return drawSegment(text.substring(segmentStart), cursor, y, currentColor, shadow);
    }

    private int drawSegment(String text, int x, int y, int color, boolean shadow) {
        if (text.isEmpty()) return x;
        PreviewDrawContext.drawString(text, x, y, color, shadow);
        return x + PreviewDrawContext.stringWidth(text);
    }

    public String trimStringToWidth(String text, int width) {
        return trimStringToWidth(text, width, false);
    }

    public String trimStringToWidth(String text, int width, boolean reverse) {
        if (text == null || text.isEmpty() || width <= 0) return "";
        int measured = 0;
        int index = reverse ? text.length() - 1 : 0;
        int step = reverse ? -1 : 1;
        boolean formatting = false;
        int boundary = reverse ? text.length() : 0;
        while (index >= 0 && index < text.length()) {
            char character = text.charAt(index);
            if (character == '\u00A7') {
                formatting = true;
            } else if (formatting) {
                formatting = false;
            } else {
                measured += getStringWidth(String.valueOf(character));
                if (measured > width) break;
            }
            boundary = reverse ? index : index + 1;
            index += step;
        }
        return reverse ? text.substring(boundary) : text.substring(0, boundary);
    }

    public List<String> listFormattedStringToWidth(String text, int width) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            int split = sizeStringToWidth(remaining, width);
            if (split >= remaining.length()) {
                lines.add(remaining);
                break;
            }
            String line = remaining.substring(0, split);
            char separator = remaining.charAt(split);
            lines.add(line);
            remaining = EnumChatFormatting.getTextWithoutFormattingCodes(remaining.substring(split + (separator == ' ' || separator == '\n' ? 1 : 0)));
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    public int sizeStringToWidth(String text, int width) {
        int measured = 0;
        int lastSpace = -1;
        boolean formatting = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\n') return index;
            if (character == '\u00A7') {
                formatting = true;
                continue;
            }
            if (formatting) {
                formatting = false;
                continue;
            }
            if (character == ' ') lastSpace = index;
            measured += getStringWidth(String.valueOf(character));
            if (measured > width) return lastSpace >= 0 ? lastSpace : index;
        }
        return text.length();
    }
}
