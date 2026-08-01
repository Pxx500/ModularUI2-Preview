package net.minecraft.client.renderer;

import dev.modularui.preview.PreviewDrawContext;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.opengl.GL11;

/** Collects the vertices emitted by the real Minecraft/ModularUI2 draw path. */
public class Tessellator {

    public static final Tessellator instance = new Tessellator();

    private final List<Vertex> vertices = new ArrayList<>();
    private int mode = GL11.GL_QUADS;
    private int color = 0xFFFFFFFF;
    private double textureU;
    private double textureV;
    private double translationX;
    private double translationY;
    private double translationZ;
    private boolean nextVertexTextured;

    public void startDrawingQuads() {
        startDrawing(GL11.GL_QUADS);
    }

    public void startDrawing(int drawMode) {
        mode = drawMode;
        vertices.clear();
        color = PreviewDrawContext.currentColor();
    }

    public void setColorRGBA(int red, int green, int blue, int alpha) {
        color = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public void setTextureUV(double u, double v) {
        textureU = u;
        textureV = v;
        nextVertexTextured = true;
    }

    public void addVertex(double x, double y, double z) {
        vertices.add(new Vertex(
            x + translationX,
            y + translationY,
            z + translationZ,
            textureU,
            textureV,
            color,
            nextVertexTextured));
        nextVertexTextured = false;
    }

    public int draw() {
        double[] positions = new double[vertices.size() * 3];
        double[] textureCoordinates = new double[vertices.size() * 2];
        int[] colors = new int[vertices.size()];
        for (int index = 0; index < vertices.size(); index++) {
            Vertex vertex = vertices.get(index);
            positions[index * 3] = vertex.x;
            positions[index * 3 + 1] = vertex.y;
            positions[index * 3 + 2] = vertex.z;
            textureCoordinates[index * 2] = vertex.u;
            textureCoordinates[index * 2 + 1] = vertex.v;
            colors[index] = vertex.color;
        }
        boolean textured = vertices.stream().anyMatch(Vertex::textured);
        PreviewDrawContext.drawVertices(
            mode,
            positions,
            textured ? textureCoordinates : null,
            colors,
            vertices.size());
        int count = vertices.size();
        vertices.clear();
        return count;
    }

    public void setBrightness(int brightness) {}

    public void setTranslation(double x, double y, double z) {
        translationX = x;
        translationY = y;
        translationZ = z;
    }

    private record Vertex(double x, double y, double z, double u, double v, int color, boolean textured) {}
}
