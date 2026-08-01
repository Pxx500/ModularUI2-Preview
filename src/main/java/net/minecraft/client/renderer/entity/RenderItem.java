package net.minecraft.client.renderer.entity;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

public class RenderItem {

    public float zLevel;

    public void renderItemAndEffectIntoGUI(FontRenderer fontRenderer, TextureManager textureManager, ItemStack stack,
        int x, int y) {}

    public void renderItemOverlayIntoGUI(FontRenderer fontRenderer, TextureManager textureManager, ItemStack stack,
        int x, int y, String text) {}
}
