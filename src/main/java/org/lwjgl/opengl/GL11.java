package org.lwjgl.opengl;

import dev.modularui.preview.PreviewDrawContext;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Headless OpenGL 1.1 boundary used by the production ModularUI2 draw path. */
public final class GL11 {

    public static final int GL_POINTS = 0;
    public static final int GL_LINES = 1;
    public static final int GL_LINE_LOOP = 2;
    public static final int GL_LINE_STRIP = 3;
    public static final int GL_LINE_SMOOTH = 0x0B20;
    public static final int GL_TRIANGLES = 4;
    public static final int GL_TRIANGLE_STRIP = 5;
    public static final int GL_TRIANGLE_FAN = 6;
    public static final int GL_QUADS = 7;
    public static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    public static final int GL_STENCIL_BUFFER_BIT = 0x00000400;
    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    public static final int GL_ALPHA_TEST = 0x0BC0;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_COLOR_LOGIC_OP = 0x0BF2;
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_LIGHTING = 0x0B50;
    public static final int GL_STENCIL_TEST = 0x0B90;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_REPEAT = 0x2901;
    public static final int GL_TEXTURE_BINDING_2D = 0x8069;
    public static final int GL_MODELVIEW = 0x1700;
    public static final int GL_PROJECTION = 0x1701;
    public static final int GL_MODELVIEW_MATRIX = 0x0BA6;
    public static final int GL_PROJECTION_MATRIX = 0x0BA7;
    public static final int GL_VIEWPORT = 0x0BA2;
    public static final int GL_DEPTH_COMPONENT = 0x1902;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_FLAT = 0x1D00;
    public static final int GL_SMOOTH = 0x1D01;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_ALWAYS = 0x0207;
    public static final int GL_EQUAL = 0x0202;
    public static final int GL_LEQUAL = 0x0203;
    public static final int GL_KEEP = 0x1E00;
    public static final int GL_INCR = 0x1E02;
    public static final int GL_DECR = 0x1E03;
    public static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    public static final int GL_ALL_CLIENT_ATTRIB_BITS = -1;
    public static final int GL_COLOR_MATERIAL = 0x0B57;

    private GL11() {}

    public static void glPushMatrix() {
        PreviewDrawContext.pushMatrix();
    }

    public static void glPopMatrix() {
        PreviewDrawContext.popMatrix();
    }

    public static void glLoadIdentity() {
        PreviewDrawContext.loadIdentity();
    }

    public static void glLoadMatrix(FloatBuffer matrix) {
        PreviewDrawContext.loadMatrix(matrix);
    }

    public static void glLineWidth(float width) {
        PreviewDrawContext.lineWidth(width);
    }

    public static void glBegin(int mode) {
        PreviewDrawContext.begin(mode);
    }

    public static void glEnd() {
        PreviewDrawContext.end();
    }

    public static void glVertex2f(float x, float y) {
        PreviewDrawContext.vertex(x, y);
    }

    public static void glVertex2d(double x, double y) {
        PreviewDrawContext.vertex(x, y);
    }

    public static void glMultMatrix(FloatBuffer matrix) {
        PreviewDrawContext.multiplyMatrix(matrix);
    }

    public static void glTranslatef(float x, float y, float z) {
        PreviewDrawContext.translate(x, y);
    }

    public static void glTranslated(double x, double y, double z) {
        PreviewDrawContext.translate(x, y);
    }

    public static void glScalef(float x, float y, float z) {
        PreviewDrawContext.scale(x, y);
    }

    public static void glScaled(double x, double y, double z) {
        PreviewDrawContext.scale(x, y);
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        if (angle != 0 && (x != 0 || y != 0 || z <= 0)) {
            PreviewDrawContext.unsupported(
                "unsupported.rotation-axis: Rotation is approximated around the positive Z axis");
        }
        PreviewDrawContext.rotate(angle);
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        PreviewDrawContext.color(red, green, blue, alpha);
    }

    public static void glGetFloat(int name, FloatBuffer target) {
        PreviewDrawContext.getMatrix(target);
    }

    public static int glGetInteger(int name) {
        return 0;
    }

    public static void glGetInteger(int name, IntBuffer target) {
        while (target.hasRemaining()) target.put(0);
    }

    public static void glDisable(int capability) {
        PreviewDrawContext.disable(capability);
    }

    public static void glEnable(int capability) {
        PreviewDrawContext.enable(capability);
    }

    public static void glBlendFunc(int source, int destination) {
        if (source != GL_SRC_ALPHA || destination != GL_ONE_MINUS_SRC_ALPHA) {
            PreviewDrawContext.unsupported(
                "unsupported.blend-function: Custom blend factors are approximated with source-alpha blending");
        }
    }

    public static void glBindTexture(int target, int texture) {}

    public static void glClear(int mask) {}

    public static void glClearDepth(double depth) {}

    public static void glClearStencil(int value) {}

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        PreviewDrawContext.colorMask(red, green, blue, alpha);
    }

    public static void glDepthMask(boolean enabled) {}

    public static void glFrustum(double left, double right, double bottom, double top, double near, double far) {
        PreviewDrawContext.unsupported(
            "unsupported.perspective-projection: Perspective projection is not applied");
    }

    public static void glMatrixMode(int mode) {}

    public static void glOrtho(double left, double right, double bottom, double top, double near, double far) {}

    public static void glPopAttrib() {}

    public static void glPopClientAttrib() {}

    public static void glPushAttrib(int mask) {}

    public static void glPushClientAttrib(int mask) {}

    public static void glReadPixels(int x, int y, int width, int height, int format, int type, FloatBuffer target) {}

    public static void glScissor(int x, int y, int width, int height) {
        PreviewDrawContext.scissor(x, y, width, height);
    }

    public static void glShadeModel(int mode) {}

    public static void glStencilFunc(int function, int reference, int mask) {}

    public static void glStencilMask(int mask) {}

    public static void glStencilOp(int fail, int depthFail, int depthPass) {
        PreviewDrawContext.stencilOperation(depthPass);
    }

    public static void glTexParameteri(int target, int parameter, int value) {
        PreviewDrawContext.textureParameter(target, parameter, value);
    }

    public static void glViewport(int x, int y, int width, int height) {}
}
