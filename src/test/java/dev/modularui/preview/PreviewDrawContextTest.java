package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;

class PreviewDrawContextTest {

    @Test
    void clipsDrawCallsToAnActiveStencilRectangle() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            PreviewDrawContext.run(graphics, () -> {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
                GL11.glColorMask(false, false, false, false);
                drawQuad(5, 5, 15, 15, Color.WHITE.getRGB());
                GL11.glColorMask(true, true, true, true);

                PreviewDrawContext.drawRect(0, 0, 20, 20, Color.RED.getRGB());

                GL11.glDisable(GL11.GL_STENCIL_TEST);
                PreviewDrawContext.drawRect(0, 0, 2, 2, Color.BLUE.getRGB());
            });
        } finally {
            graphics.dispose();
        }

        assertEquals(Color.BLUE.getRGB(), image.getRGB(1, 1));
        assertEquals(0, image.getRGB(3, 3));
        assertEquals(Color.RED.getRGB(), image.getRGB(10, 10));
        assertEquals(0, image.getRGB(17, 17));
    }

    @Test
    void fillsAConnectedTriangleStripWithoutCrossingItsInterior() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            PreviewDrawContext.run(graphics, () -> PreviewDrawContext.drawVertices(
                GL11.GL_TRIANGLE_STRIP,
                new double[] {
                    2, 2, 0,
                    2, 8, 0,
                    8, 2, 0,
                    8, 8, 0,
                    14, 2, 0,
                    14, 8, 0
                },
                null,
                new int[] { Color.WHITE.getRGB(), Color.WHITE.getRGB(), Color.WHITE.getRGB(),
                    Color.WHITE.getRGB(), Color.WHITE.getRGB(), Color.WHITE.getRGB() },
                6));
        } finally {
            graphics.dispose();
        }

        assertNotEquals(0, image.getRGB(4, 5));
        assertNotEquals(0, image.getRGB(10, 5));
        assertEquals(0, image.getRGB(17, 5));
    }

    @Test
    void blendsAnAnnularTriangleStripOnceWithoutFillingItsCenter() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int color = new Color(110, 116, 128, 34).getRGB();
        try {
            PreviewDrawContext.run(graphics, () -> PreviewDrawContext.drawVertices(
                GL11.GL_TRIANGLE_STRIP,
                annularStrip(20, 20, 16, 10, 12),
                null,
                IntStream.generate(() -> color).limit(26).toArray(),
                26));
        } finally {
            graphics.dispose();
        }

        assertEquals(0, alphaAt(image, 20, 20));
        assertEquals(34, alphaAt(image, 33, 20));
        assertEquals(34, alphaAt(image, 31, 27));
    }

    @Test
    void appliesTheModularUiColorStateToImmediatePrimitives() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            PreviewDrawContext.run(graphics, () -> {
                com.cleanroommc.modularui.utils.GlStateManager.color(0.25F, 0.5F, 0.75F, 0.5F);
                GL11.glBegin(GL11.GL_TRIANGLE_FAN);
                GL11.glVertex2f(1, 1);
                GL11.glVertex2f(8, 1);
                GL11.glVertex2f(8, 8);
                GL11.glVertex2f(1, 8);
                GL11.glEnd();
            });
        } finally {
            graphics.dispose();
        }

        assertEquals(new Color(64, 128, 191, 128).getRGB(), image.getRGB(5, 5));
    }

    @Test
    void keepsOpenGlLineWidthInFramebufferPixelsWhenGuiCoordinatesAreScaled() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(2, 2);
        try {
            PreviewDrawContext.run(graphics, () -> {
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(1F);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex2d(1, 5.25);
                GL11.glVertex2d(9, 5.25);
                GL11.glEnd();
            });
        } finally {
            graphics.dispose();
        }

        long paintedRows = IntStream.range(0, image.getHeight())
            .filter(y -> IntStream.range(0, image.getWidth()).anyMatch(x -> (image.getRGB(x, y) >>> 24) != 0))
            .count();
        assertEquals(1, paintedRows);
    }

    private static void drawQuad(int left, int top, int right, int bottom, int color) {
        PreviewDrawContext.drawVertices(
            GL11.GL_QUADS,
            new double[] { left, top, 0, right, top, 0, right, bottom, 0, left, bottom, 0 },
            null,
            new int[] { color, color, color, color },
            4);
    }

    private static double[] annularStrip(
        double centerX, double centerY, double outerRadius, double innerRadius, int segments) {
        double[] positions = new double[(segments + 1) * 6];
        for (int segment = 0; segment <= segments; segment++) {
            double angle = segment * Math.PI * 2 / segments;
            int offset = segment * 6;
            positions[offset] = centerX + outerRadius * Math.cos(angle);
            positions[offset + 1] = centerY + outerRadius * Math.sin(angle);
            positions[offset + 3] = centerX + innerRadius * Math.cos(angle);
            positions[offset + 4] = centerY + innerRadius * Math.sin(angle);
        }
        return positions;
    }

    private static int alphaAt(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
