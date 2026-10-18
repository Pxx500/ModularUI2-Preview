package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;

class MinecraftStubAbiTest {

    @Test
    void itemStackAcceptsTheVanillaItemConstructorShape() throws Exception {
        assertNotNull(ItemStack.class.getConstructor(Item.class));
    }

    @Test
    void itemStackAcceptsTheVanillaBlockConstructorShape() throws Exception {
        assertNotNull(ItemStack.class.getConstructor(Block.class));
    }
}
