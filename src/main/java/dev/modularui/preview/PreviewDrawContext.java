package dev.modularui.preview;

import dev.modularui.preview.assets.AssetResolver;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
        Polygon polygon = polygon(new double[] {
            left, top, 0,
            right, top, 0,
            right, bottom, 0,
            left, bottom, 0
        }, new int[] { 0, 1, 2, 3 });
        if (captureStencil(polygon)) return;
        Graphics2D graphics = requireGraphics();
        graphics.setColor(new Color(color, true));
        graphics.fillPolygon(polygon);
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

    public static void enable(int capability) {
        State state = requireState();
        if (capability == org.lwjgl.opengl.GL11.GL_STENCIL_TEST) state.stencilEnabled = true;
        if (capability == org.lwjgl.opengl.GL11.GL_LINE_SMOOTH) state.smoothLines = true;
    }

    public static void disable(int capability) {
        State state = requireState();
        if (capability == org.lwjgl.opengl.GL11.GL_LINE_SMOOTH) {
            state.smoothLines = false;
            return;
        }
        if (capability != org.lwjgl.opengl.GL11.GL_STENCIL_TEST) return;
        state.stencilEnabled = false;
        state.stencilClips.clear();
        state.graphics.setClip(state.originalClip);
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        State state = requireState();
        boolean drawingColors = red || green || blue || alpha;
        if (!drawingColors && state.stencilEnabled) {
            state.capturedStencil = new Area();
            return;
        }
        if (drawingColors && state.capturedStencil != null) finishStencilCapture(state);
    }

    public static void stencilOperation(int depthPass) {
        requireState().stencilDepthPass = depthPass;
    }

    public static void lineWidth(float width) {
        requireState().lineWidth = Math.max(1F, width);
    }

    public static void begin(int mode) {
        State state = requireState();
        state.immediateMode = mode;
        state.immediateVertices.clear();
    }

    public static void vertex(double x, double y) {
        State state = requireState();
        state.immediateVertices.add(x);
        state.immediateVertices.add(y);
        state.immediateVertices.add(0.0);
    }

    public static void end() {
        State state = requireState();
        if (state.immediateMode < 0) return;
        double[] positions = state.immediateVertices.stream().mapToDouble(Double::doubleValue).toArray();
        int count = positions.length / 3;
        int[] colors = new int[count];
        java.util.Arrays.fill(colors, state.color.getRGB());
        drawVertices(state.immediateMode, positions, null, colors, count);
        state.immediateMode = -1;
        state.immediateVertices.clear();
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
            drawTriangleStrip(positions, colors, count);
            return;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_LINES) {
            for (int index = 0; index + 1 < count; index += 2) drawLine(positions, index, index + 1);
            return;
        }
        if (mode == org.lwjgl.opengl.GL11.GL_LINE_STRIP || mode == org.lwjgl.opengl.GL11.GL_LINE_LOOP) {
            for (int index = 0; index + 1 < count; index++) drawLine(positions, index, index + 1);
            if (mode == org.lwjgl.opengl.GL11.GL_LINE_LOOP && count > 2) drawLine(positions, count - 1, 0);
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
        Polygon polygon = polygon(positions, indices);
        fillShape(polygon, colors, indices[0]);
    }

    private static void drawTriangleStrip(double[] positions, int[] colors, int count) {
        Area strip = new Area();
        for (int index = 0; index + 2 < count; index++) {
            int first = index % 2 == 0 ? index : index + 1;
            int second = index % 2 == 0 ? index + 1 : index;
            strip.add(new Area(triangle(positions, first, second, index + 2)));
        }
        fillShape(strip, colors, 0);
    }

    private static Shape triangle(double[] positions, int first, int second, int third) {
        Path2D.Double triangle = new Path2D.Double();
        Point2D firstPoint = transform(positions[first * 3], positions[first * 3 + 1]);
        Point2D secondPoint = transform(positions[second * 3], positions[second * 3 + 1]);
        Point2D thirdPoint = transform(positions[third * 3], positions[third * 3 + 1]);
        triangle.moveTo(firstPoint.getX(), firstPoint.getY());
        triangle.lineTo(secondPoint.getX(), secondPoint.getY());
        triangle.lineTo(thirdPoint.getX(), thirdPoint.getY());
        triangle.closePath();
        return triangle;
    }

    private static void fillShape(Shape shape, int[] colors, int colorIndex) {
        if (captureStencil(shape)) return;
        State state = requireState();
        Color drawColor = colors.length > colorIndex ? new Color(colors[colorIndex], true) : state.color;
        state.graphics.setColor(drawColor);
        state.graphics.fill(shape);
    }

    private static Polygon polygon(double[] positions, int[] indices) {
        Polygon polygon = new Polygon();
        for (int index : indices) {
            Point2D point = transform(positions[index * 3], positions[index * 3 + 1]);
            polygon.addPoint((int) Math.round(point.getX()), (int) Math.round(point.getY()));
        }
        return polygon;
    }

    private static boolean captureStencil(Shape shape) {
        State state = requireState();
        if (state.capturedStencil == null) return false;
        state.capturedStencil.add(new Area(shape));
        return true;
    }

    private static void finishStencilCapture(State state) {
        if (state.stencilDepthPass == org.lwjgl.opengl.GL11.GL_DECR) {
            if (!state.stencilClips.isEmpty()) state.stencilClips.pop();
        } else if (state.stencilDepthPass == org.lwjgl.opengl.GL11.GL_INCR) {
            Area clip = new Area(state.capturedStencil);
            if (!state.stencilClips.isEmpty()) clip.intersect(new Area(state.stencilClips.peek()));
            state.stencilClips.push(clip);
        }
        state.capturedStencil = null;
        if (state.stencilClips.isEmpty()) state.graphics.setClip(state.originalClip);
        else state.graphics.setClip(state.stencilClips.peek());
    }

    private static void drawLine(double[] positions, int first, int second) {
        Point2D from = transform(positions[first * 3], positions[first * 3 + 1]);
        Point2D to = transform(positions[second * 3], positions[second * 3 + 1]);
        State state = requireState();
        state.graphics.setColor(state.color);
        Object previousAntialiasing = state.graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        java.awt.Stroke previousStroke = state.graphics.getStroke();
        double framebufferScale = Math.sqrt(Math.abs(state.graphics.getTransform().getDeterminant()));
        float logicalWidth = (float) (state.lineWidth / Math.max(1D, framebufferScale));
        state.graphics.setStroke(new BasicStroke(logicalWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        state.graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            state.smoothLines ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        state.graphics.draw(new Line2D.Double(from, to));
        state.graphics.setStroke(previousStroke);
        if (previousAntialiasing != null) {
            state.graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialiasing);
        }
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
        private final Shape originalClip;
        private final Deque<AffineTransform> matrices = new ArrayDeque<>();
        private final Deque<Area> stencilClips = new ArrayDeque<>();
        private final Set<String> assetSources = new LinkedHashSet<>();
        private AffineTransform matrix = new AffineTransform();
        private Color color = Color.WHITE;
        private BufferedImage texture;
        private int immediateMode = -1;
        private final List<Double> immediateVertices = new ArrayList<>();
        private boolean stencilEnabled;
        private boolean smoothLines;
        private float lineWidth = 1F;
        private int stencilDepthPass = org.lwjgl.opengl.GL11.GL_KEEP;
        private Area capturedStencil;

        private State(Graphics2D graphics, AssetResolver assets) {
            this.graphics = graphics;
            this.assets = assets;
            this.originalClip = graphics.getClip();
        }
    }
}
