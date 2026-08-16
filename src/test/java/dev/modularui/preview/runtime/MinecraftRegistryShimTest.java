package dev.modularui.preview.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.RegistryNamespaced;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

class MinecraftRegistryShimTest {

    @Test
    void resolvesVanillaNamesFromForgeRegistryWrites() {
        RegistryNamespaced registry = new RegistryNamespaced();
        Object grass = new Object();

        registry.putObject("minecraft:grass", grass);

        assertSame(grass, registry.getObject("grass"));
        assertTrue(registry.containsKey("grass"));
    }

    @Test
    void exposesItemStackDamageThroughTheForgeItemAbi() {
        Item item = new Item();
        ItemStack stack = new ItemStack(item, 1, 3);

        assertEquals(3, item.getDamage(stack));
        item.setDamage(stack, 7);
        assertEquals(7, stack.getItemDamage());
    }

    @Test
    void exposesTheSharedMinecraftItemRenderer() {
        assertSame(RenderItem.getInstance(), RenderItem.getInstance());
    }

    @Test
    void resolvesItemIdsAndReportsNoHeadlessKeyboardModifiers() {
        Item item = new Item();
        Item.itemRegistry.addObject(9000, "preview_test", item);

        assertEquals(9000, Item.getIdFromItem(item));
        assertFalse(GuiScreen.isShiftKeyDown());
        assertFalse(Keyboard.isKeyDown(42));
    }
}
