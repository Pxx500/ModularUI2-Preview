package dev.modularui.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

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
    private static final String FONT_RESOURCE = "/assets/minecraft/textures/font/ascii.png.base64";
    private static final BufferedImage FONT_TEXTURE = loadFontTexture();
    private static final int[] CHARACTER_WIDTHS = measureCharacters();

    private MinecraftFont() {}

    static int stringWidth(String text) {
        int width = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character < CHARACTER_WIDTHS.length) width += CHARACTER_WIDTHS[character];
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
            if (character >= CHARACTER_WIDTHS.length) continue;
            drawGlyph(graphics, character, cursor, y);
            cursor += CHARACTER_WIDTHS[character];
        }
    }

    private static void drawGlyph(Graphics2D graphics, int character, int x, int y) {
        int sourceX = character % GLYPHS_PER_ROW * GLYPH_SIZE;
        int sourceY = character / GLYPHS_PER_ROW * GLYPH_SIZE;
        for (int glyphY = 0; glyphY < GLYPH_SIZE; glyphY++) {
            for (int glyphX = 0; glyphX < GLYPH_SIZE; glyphX++) {
                int alpha = FONT_TEXTURE.getRGB(sourceX + glyphX, sourceY + glyphY) >>> ALPHA_SHIFT;
                if (alpha != 0) graphics.fillRect(x + glyphX, y + glyphY, PIXEL_SIZE, PIXEL_SIZE);
            }
        }
    }

    private static int[] measureCharacters() {
        int[] widths = new int[CHARACTER_COUNT];
        for (int character = 0; character < widths.length; character++) {
            widths[character] = character == ' ' ? SPACE_WIDTH : measureCharacter(character);
        }
        return widths;
    }

    private static int measureCharacter(int character) {
        int sourceX = character % GLYPHS_PER_ROW * GLYPH_SIZE;
        int sourceY = character / GLYPHS_PER_ROW * GLYPH_SIZE;
        for (int glyphX = GLYPH_SIZE - PIXEL_SIZE; glyphX >= 0; glyphX--) {
            for (int glyphY = 0; glyphY < GLYPH_SIZE; glyphY++) {
                int alpha = FONT_TEXTURE.getRGB(sourceX + glyphX, sourceY + glyphY) >>> ALPHA_SHIFT;
                if (alpha != 0) return glyphX + GLYPH_ADVANCE_PADDING;
            }
        }
        return EMPTY_GLYPH_WIDTH;
    }

    private static BufferedImage loadFontTexture() {
        try (InputStream encoded = MinecraftFont.class.getResourceAsStream(FONT_RESOURCE)) {
            if (encoded == null) throw new IllegalStateException("Minecraft font resource is unavailable: " + FONT_RESOURCE);
            try (InputStream decoded = Base64.getMimeDecoder()
                .wrap(encoded)) {
                return ImageIO.read(decoded);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read Minecraft font resource", exception);
        }
    }
}
