package net.minecraft.entity.player;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class InventoryPlayer implements IInventory {

    private static final int MAIN_INVENTORY_SIZE = 36;
    private static final int ARMOR_INVENTORY_SIZE = 4;
    private static final int INVENTORY_STACK_LIMIT = 64;

    public final EntityPlayer player;
    public final ItemStack[] mainInventory = new ItemStack[MAIN_INVENTORY_SIZE];
    public final ItemStack[] armorInventory = new ItemStack[ARMOR_INVENTORY_SIZE];
    private ItemStack itemStack;

    public InventoryPlayer(EntityPlayer player) {
        this.player = player;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setItemStack(ItemStack stack) {
        itemStack = stack;
    }

    @Override
    public int getSizeInventory() {
        return mainInventory.length + armorInventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot < mainInventory.length ? mainInventory[slot] : armorInventory[slot - mainInventory.length];
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < mainInventory.length) mainInventory[slot] = stack;
        else armorInventory[slot - mainInventory.length] = stack;
    }

    @Override
    public String getInventoryName() {
        return "container.inventory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
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
