package dev.modularui.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.imageio.ImageIO;

final class MinecraftFont {

    private static final int GLYPH_SIZE = 8;
    private static final int GLYPHS_PER_ROW = 16;
    private static final int CHARACTER_COUNT = 256;
    private static final int SPACE_WIDTH = 4;
    private static final int GLYPH_ADVANCE_PADDING = 2;
    private static final int EMPTY_GLYPH_WIDTH = 1;
    private static final int PIXEL_SIZE = 1;
    private static final int ALPHA_SHIFT = 24;
    private static final int ALPHA_MASK = 0xFF000000;
    private static final int SHADOW_RGB_MASK = 0xFCFCFC;
    private static final int SHADOW_DARKEN_SHIFT = 2;
    private static final String MINECRAFT_JAR_ENVIRONMENT = "MODULARUI2_PREVIEW_MINECRAFT_JAR";
    private static final String GRADLE_HOME_ENVIRONMENT = "GRADLE_USER_HOME";
    private static final String FONT_ENTRY = "assets/minecraft/textures/font/ascii.png";
    private static final Path RFG_CLIENT = Path.of(
        "caches",
        "retro_futura_gradle",
        "mc-vanilla",
        "1.7.10",
        "client.jar");

    private MinecraftFont() {}

    static int stringWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < FontData.CHARACTER_WIDTHS.length) width += FontData.CHARACTER_WIDTHS[character];
        }
        return width;
    }

    static void drawString(Graphics2D graphics, String text, int x, int y, int color, boolean shadow) {
        if (shadow) drawGlyphs(graphics, text, x + PIXEL_SIZE, y + PIXEL_SIZE, shadowColor(color));
        drawGlyphs(graphics, text, x, y, color);
    }

    private static int shadowColor(int color) {
        return color & ALPHA_MASK | (color & SHADOW_RGB_MASK) >> SHADOW_DARKEN_SHIFT;
    }

    private static void drawGlyphs(Graphics2D graphics, String text, int x, int y, int color) {
        int cursor = x;
        graphics.setColor(new Color(color, true));
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character >= FontData.CHARACTER_WIDTHS.length) continue;
            drawGlyph(graphics, character, cursor, y);
            cursor += FontData.CHARACTER_WIDTHS[character];
        }
    }

    private static void drawGlyph(Graphics2D graphics, int character, int x, int y) {
        int sourceX = character % GLYPHS_PER_ROW * GLYPH_SIZE;
        int sourceY = character / GLYPHS_PER_ROW * GLYPH_SIZE;
        for (int glyphY = 0; glyphY < GLYPH_SIZE; glyphY++) {
            for (int glyphX = 0; glyphX < GLYPH_SIZE; glyphX++) {
                int alpha = FontData.TEXTURE.getRGB(sourceX + glyphX, sourceY + glyphY) >>> ALPHA_SHIFT;
                if (alpha != 0) graphics.fillRect(x + glyphX, y + glyphY, PIXEL_SIZE, PIXEL_SIZE);
            }
        }
    }

    private static int[] measureCharacters(BufferedImage texture) {
        int[] widths = new int[CHARACTER_COUNT];
        for (int character = 0; character < widths.length; character++) {
            widths[character] = character == ' ' ? SPACE_WIDTH : measureCharacter(texture, character);
        }
        return widths;
    }

    private static int measureCharacter(BufferedImage texture, int character) {
        int sourceX = character % GLYPHS_PER_ROW * GLYPH_SIZE;
        int sourceY = character / GLYPHS_PER_ROW * GLYPH_SIZE;
        for (int glyphX = GLYPH_SIZE - PIXEL_SIZE; glyphX >= 0; glyphX--) {
            for (int glyphY = 0; glyphY < GLYPH_SIZE; glyphY++) {
                int alpha = texture.getRGB(sourceX + glyphX, sourceY + glyphY) >>> ALPHA_SHIFT;
                if (alpha != 0) return glyphX + GLYPH_ADVANCE_PADDING;
            }
        }
        return EMPTY_GLYPH_WIDTH;
    }

    static Path resolveClientJar(Map<String, String> environment, Path userHome) {
        List<Path> candidates = new ArrayList<>();
        String override = environment.get(MINECRAFT_JAR_ENVIRONMENT);
        if (override != null && !override.isBlank()) candidates.add(Path.of(override));

        String configuredGradleHome = environment.get(GRADLE_HOME_ENVIRONMENT);
        Path gradleHome = configuredGradleHome == null || configuredGradleHome.isBlank()
            ? userHome.resolve(".gradle")
            : Path.of(configuredGradleHome);
        candidates.add(gradleHome.resolve(RFG_CLIENT));

        return candidates.stream()
            .map(path -> path.toAbsolutePath().normalize())
            .filter(Files::isRegularFile)
            .findFirst()
            .orElseThrow(() -> missingClient(candidates));
    }

    static BufferedImage loadFontTexture(Path clientJar) {
        try (JarFile client = new JarFile(clientJar.toFile())) {
            JarEntry entry = client.getJarEntry(FONT_ENTRY);
            if (entry == null) {
                throw new IllegalStateException("Minecraft client JAR does not contain " + FONT_ENTRY + ": " + clientJar);
            }
            try (InputStream input = client.getInputStream(entry)) {
                BufferedImage texture = ImageIO.read(input);
                if (texture == null) throw new IllegalStateException("Minecraft font is not a readable PNG: " + clientJar);
                return texture;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the Minecraft font from " + clientJar, exception);
        }
    }

    private static IllegalStateException missingClient(List<Path> candidates) {
        String checked = candidates.stream()
            .map(path -> path.toAbsolutePath().normalize().toString())
            .collect(java.util.stream.Collectors.joining(System.lineSeparator() + "  - ", "  - ", ""));
        return new IllegalStateException(
            "Minecraft 1.7.10 client.jar is required for the game font. Checked:" + System.lineSeparator()
                + checked + System.lineSeparator()
                + "Set " + MINECRAFT_JAR_ENVIRONMENT + " to the client.jar path if RetroFuturaGradle uses another cache.");
    }

    private static final class FontData {

        private static final BufferedImage TEXTURE = loadFontTexture(resolveClientJar(
            System.getenv(),
            Path.of(System.getProperty("user.home"))));
        private static final int[] CHARACTER_WIDTHS = measureCharacters(TEXTURE);

        private FontData() {}
    }
}
