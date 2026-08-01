package net.minecraft.client.renderer.texture;

import dev.modularui.preview.PreviewDrawContext;
import net.minecraft.util.ResourceLocation;

public class TextureManager {

    public void bindTexture(ResourceLocation location) {
        PreviewDrawContext.bindTexture(location);
    }
}
