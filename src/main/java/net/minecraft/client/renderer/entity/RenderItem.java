package net.minecraft.client.renderer.entity;

import dev.modularui.preview.PreviewDrawContext;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;

public class RenderItem {

    private static final RenderItem INSTANCE = new RenderItem();

    public float zLevel;

    public static RenderItem getInstance() {
        return INSTANCE;
    }

    public void renderItemAndEffectIntoGUI(FontRenderer fontRenderer, TextureManager textureManager, ItemStack stack,
        int x, int y) {
        reportSkippedItem(stack);
    }

    public void renderItemOverlayIntoGUI(FontRenderer fontRenderer, TextureManager textureManager, ItemStack stack,
        int x, int y, String text) {
        reportSkippedItem(stack);
    }

    private static void reportSkippedItem(ItemStack stack) {
        if (stack != null && (stack.getItem() != null || stack.stackSize > 0)) {
            PreviewDrawContext.unsupported(
                "unsupported.item-rendering: Item icons, effects and overlays are not rendered");
        }
    }
}
