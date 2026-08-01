package dev.modularui.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record PreviewScreen(int width, int height, int requestedGuiScale, int backgroundColor) {

    public static final int AUTO = 0;

    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final int DEFAULT_BACKGROUND_COLOR = 0xFF101820;
    private static final int MAX_EXPLICIT_GUI_SCALE = 4;
    private static final int MIN_LOGICAL_WIDTH = 320;
    private static final int MIN_LOGICAL_HEIGHT = 240;
    private static final int RGB_HEX_LENGTH = 6;
    private static final int ARGB_HEX_LENGTH = 8;
    private static final int OPAQUE_ALPHA = 0xFF000000;
    private static final int HEX_RADIX = 16;

    public PreviewScreen(int width, int height, int requestedGuiScale) {
        this(width, height, requestedGuiScale, DEFAULT_BACKGROUND_COLOR);
    }

    public PreviewScreen {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Screen dimensions must be positive");
        if (requestedGuiScale < AUTO || requestedGuiScale > MAX_EXPLICIT_GUI_SCALE) {
            throw new IllegalArgumentException("GUI scale must be auto or between 1 and 4");
        }
    }

    public static PreviewScreen fullHd() {
        return new PreviewScreen(DEFAULT_WIDTH, DEFAULT_HEIGHT, AUTO);
    }

    public static PreviewScreen load(Path configuration) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configuration)) {
            properties.load(input);
        }
        int width = parsePositiveInteger(properties, "screen.width");
        int height = parsePositiveInteger(properties, "screen.height");
        int guiScale = parseGuiScale(required(properties, "gui.scale"));
        int background = parseColor(required(properties, "screen.background"));
        return new PreviewScreen(width, height, guiScale, background);
    }

    public ScreenLayout layout(int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) {
            throw new IllegalArgumentException("Panel dimensions must be positive");
        }
        int guiScale = effectiveGuiScale();
        int logicalWidth = divideRoundingUp(width, guiScale);
        int logicalHeight = divideRoundingUp(height, guiScale);
        Bounds panelLogical = new Bounds(
            (logicalWidth - panelWidth) / 2,
            (logicalHeight - panelHeight) / 2,
            panelWidth,
            panelHeight);
        return new ScreenLayout(
            width,
            height,
            guiScale,
            logicalWidth,
            logicalHeight,
            panelLogical,
            panelLogical.scale(guiScale));
    }

    private int effectiveGuiScale() {
        if (requestedGuiScale != AUTO) return requestedGuiScale;
        int scale = 1;
        while (width / (scale + 1) >= MIN_LOGICAL_WIDTH && height / (scale + 1) >= MIN_LOGICAL_HEIGHT) {
            scale++;
        }
        return scale;
    }

    private static int divideRoundingUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static int parsePositiveInteger(Properties properties, String key) {
        String value = required(properties, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) throw new IllegalArgumentException(key + " must be positive");
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer: " + value, exception);
        }
    }

    private static int parseGuiScale(String value) {
        if (value.equalsIgnoreCase("auto")) return AUTO;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("gui.scale must be auto or an integer from 1 to 4: " + value, exception);
        }
    }

    private static int parseColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != RGB_HEX_LENGTH && hex.length() != ARGB_HEX_LENGTH) {
            throw new IllegalArgumentException("screen.background must be #RRGGBB or #AARRGGBB: " + value);
        }
        try {
            int color = Integer.parseUnsignedInt(hex, HEX_RADIX);
            return hex.length() == RGB_HEX_LENGTH ? OPAQUE_ALPHA | color : color;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("screen.background is not a hexadecimal color: " + value, exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing preview property: " + key);
        return value.trim();
    }
}
