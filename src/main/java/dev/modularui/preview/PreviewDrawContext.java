package dev.modularui.preview;

import dev.modularui.preview.assets.AssetResolver;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.util.ResourceLocation;

public final class PreviewDrawContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private PreviewDrawContext() {}

    public static void run(Graphics2D graphics, Runnable drawable) {
        run(graphics, null, drawable);
    }

    public static List<String> run(Graphics2D graphics, AssetResolver assets, Runnable drawable) {
        State previous = CURRENT.get();
        State state = new State(graphics, assets);
        CURRENT.set(state);
        try {
            drawable.run();
            return List.copyOf(state.assetSources);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        Graphics2D graphics = requireGraphics();
        graphics.setColor(new Color(color, true));
        graphics.fillRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public static int stringWidth(String text) {
        return MinecraftFont.stringWidth(text);
    }

    public static void drawString(String text, int x, int y, int color, boolean shadow) {
        Point2D point = transform(x, y);
        MinecraftFont.drawString(
            requireGraphics(),
            text,
            (int) Math.round(point.getX()),
            (int) Math.round(point.getY()),
            color,
            shadow);
    }

    public static void pushMatrix() {
        State state = requireState();
        state.matrices.push(new AffineTransform(state.matrix));
    }

    public static void popMatrix() {
        State state = requireState();
        if (state.matrices.isEmpty()) throw new IllegalStateException("Preview matrix stack underflow");
        state.matrix = state.matrices.pop();
    }

    public static void loadIdentity() {
        requireState().matrix = new AffineTransform();
    }

    public static void loadMatrix(FloatBuffer matrix) {
        requireState().matrix = affine(matrix);
    }

    public static void multiplyMatrix(FloatBuffer matrix) {
        requireState().matrix.concatenate(affine(matrix));
    }

    public static void translate(double x, double y) {
        requireState().matrix.translate(x, y);
    }

    public static void scale(double x, double y) {
        requireState().matrix.scale(x, y);
    }

    public static void rotate(double degrees) {
        requireState().matrix.rotate(Math.toRadians(degrees));
    }

    public static void color(float red, float green, float blue, float alpha) {
        requireState().color = new Color(clamp(red), clamp(green), clamp(blue), clamp(alpha));
    }

    public static int currentColor() {
        return requireState().color.getRGB();
    }

    public static void bindTexture(ResourceLocation location) {
        State state = requireState();
        if (state.assets == null) throw new IllegalStateException("No asset resolver is active for " + location);
        AssetResolver.ResolvedAsset asset = state.assets.find(location)
            .orElseThrow(() -> new IllegalArgumentException("Missing preview texture: " + location));
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(asset.bytes()));
            if (image == null) throw new IllegalArgumentException("Unsupported preview texture: " + asset.source());
            state.texture = image;
            state.assetSources.add(asset.source());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode preview texture: " + asset.source(), exception);
        }
    }

    public static void drawVertices(int mode, double[] positions, double[] textureCoordinates, int[] colors,
        int count) {
        if (count == 0) return;
        if (mode == org.lwjgl.opengl.GL11.GL_QUADS) {
            for (int offset = 0; offset + 3 < count; offset += 4) {
                if (textureCoordinates != null && requireState().texture != null) {
                    drawTexturedQuad(positions, textureCoordinates, offset);
                } else {
                    drawPolygon(positions, colors, offset, 4);
                }
            }
            return;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_TRIANGLES) {
            for (int offset = 0; offset + 2 < count; offset += 3) {
                drawPolygon(positions, colors, offset, 3);
            }
            return;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN) {
            for (int index = 1; index + 1 < count; index++) {
                drawPolygon(positions, colors, new int[] { 0, index, index + 1 });
            }
            return;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_TRIANGLE_STRIP) {
            for (int index = 0; index + 2 < count; index++) {
                drawPolygon(positions, colors, new int[] { index, index + 1, index + 2 });
            }
        }
    }

    public static void getMatrix(FloatBuffer target) {
        AffineTransform transform = requireState().matrix;
        double[] values = new double[6];
        transform.getMatrix(values);
        float[] matrix = {
            (float) values[0], (float) values[1], 0, 0,
            (float) values[2], (float) values[3], 0, 0,
            0, 0, 1, 0,
            (float) values[4], (float) values[5], 0, 1
        };
        target.put(matrix);
    }

    private static Graphics2D requireGraphics() {
        return requireState().graphics;
    }

    private static State requireState() {
        State state = CURRENT.get();
        if (state == null) throw new IllegalStateException("No active preview draw context");
        return state;
    }

    private static Point2D transform(double x, double y) {
        return requireState().matrix.transform(new Point2D.Double(x, y), null);
    }

    private static void drawPolygon(double[] positions, int[] colors, int offset, int length) {
        int[] indices = new int[length];
        for (int index = 0; index < length; index++) indices[index] = offset + index;
        drawPolygon(positions, colors, indices);
    }

    private static void drawPolygon(double[] positions, int[] colors, int[] indices) {
        Polygon polygon = new Polygon();
        for (int index : indices) {
            Point2D point = transform(positions[index * 3], positions[index * 3 + 1]);
            polygon.addPoint((int) Math.round(point.getX()), (int) Math.round(point.getY()));
        }
        State state = requireState();
        state.graphics.setColor(colors.length > indices[0] ? new Color(colors[indices[0]], true) : state.color);
        state.graphics.fillPolygon(polygon);
    }

    private static void drawTexturedQuad(double[] positions, double[] textureCoordinates, int offset) {
        State state = requireState();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minU = Double.POSITIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY;
        double maxU = Double.NEGATIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        for (int index = offset; index < offset + 4; index++) {
            Point2D point = transform(positions[index * 3], positions[index * 3 + 1]);
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
            minU = Math.min(minU, textureCoordinates[index * 2]);
            minV = Math.min(minV, textureCoordinates[index * 2 + 1]);
            maxU = Math.max(maxU, textureCoordinates[index * 2]);
            maxV = Math.max(maxV, textureCoordinates[index * 2 + 1]);
        }
        int sourceX0 = clamp((int) Math.floor(minU * state.texture.getWidth()), 0, state.texture.getWidth());
        int sourceY0 = clamp((int) Math.floor(minV * state.texture.getHeight()), 0, state.texture.getHeight());
        int sourceX1 = clamp((int) Math.ceil(maxU * state.texture.getWidth()), 0, state.texture.getWidth());
        int sourceY1 = clamp((int) Math.ceil(maxV * state.texture.getHeight()), 0, state.texture.getHeight());
        state.graphics.drawImage(
            state.texture,
            (int) Math.round(minX),
            (int) Math.round(minY),
            (int) Math.round(maxX),
            (int) Math.round(maxY),
            sourceX0,
            sourceY0,
            sourceX1,
            sourceY1,
            null);
    }

    private static AffineTransform affine(FloatBuffer source) {
        FloatBuffer matrix = source.duplicate();
        int position = matrix.position();
        float m00 = matrix.get(position);
        float m01 = matrix.get(position + 1);
        float m10 = matrix.get(position + 4);
        float m11 = matrix.get(position + 5);
        float m30 = matrix.get(position + 12);
        float m31 = matrix.get(position + 13);
        return new AffineTransform(m00, m01, m10, m11, m30, m31);
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class State {

        private final Graphics2D graphics;
        private final AssetResolver assets;
        private final Deque<AffineTransform> matrices = new ArrayDeque<>();
        private final Set<String> assetSources = new LinkedHashSet<>();
        private AffineTransform matrix = new AffineTransform();
        private Color color = Color.WHITE;
        private BufferedImage texture;

        private State(Graphics2D graphics, AssetResolver assets) {
            this.graphics = graphics;
            this.assets = assets;
        }
    }
}
