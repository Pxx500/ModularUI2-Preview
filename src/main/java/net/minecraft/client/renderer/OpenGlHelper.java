package net.minecraft.client.renderer;

import dev.modularui.preview.PreviewDrawContext;
import org.lwjgl.opengl.GL11;

public final class OpenGlHelper {

    public static int lightmapTexUnit;

    private OpenGlHelper() {}

    public static void glBlendFunc(int source, int destination, int sourceAlpha, int destinationAlpha) {
        GL11.glBlendFunc(source, destination);
        if (sourceAlpha != 1 || (destinationAlpha != 0 && destinationAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA)) {
            PreviewDrawContext.unsupported(
                "unsupported.blend-function: Custom blend factors are approximated with source-alpha blending");
        }
    }

    public static void setLightmapTextureCoords(int textureUnit, float x, float y) {}
}
