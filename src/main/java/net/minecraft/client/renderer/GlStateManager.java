package net.minecraft.client.renderer;

import org.lwjgl.opengl.GL11;

/** Minecraft 1.7 render-state bridge used by production UI code. */
public final class GlStateManager {

    private GlStateManager() {}

    public static void color(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static void enableBlend() {
        GL11.glEnable(GL11.GL_BLEND);
    }

    public static void disableBlend() {
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void blendFunc(int source, int destination) {
        GL11.glBlendFunc(source, destination);
    }

    public static void enableTexture2D() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void disableTexture2D() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    public static void pushMatrix() {
        GL11.glPushMatrix();
    }

    public static void popMatrix() {
        GL11.glPopMatrix();
    }
}
