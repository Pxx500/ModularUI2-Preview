package net.minecraft.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public class InventoryBasic implements IInventory {

    private static final int INVENTORY_STACK_LIMIT = 64;

    private final String name;
    private final boolean customName;
    private final ItemStack[] contents;

    public InventoryBasic(String name, boolean customName, int size) {
        this.name = name;
        this.customName = customName;
        this.contents = new ItemStack[size];
    }

    @Override
    public int getSizeInventory() {
        return contents.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return contents[slot];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack stack = contents[slot];
        contents[slot] = null;
        return stack;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return decrStackSize(slot, Integer.MAX_VALUE);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        contents[slot] = stack;
    }

    @Override
    public String getInventoryName() {
        return name;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return customName;
    }

    @Override
    public int getInventoryStackLimit() {
        return INVENTORY_STACK_LIMIT;
    }

    @Override
    public void markDirty() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return true;
    }
}
