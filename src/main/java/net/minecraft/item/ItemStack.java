package net.minecraft.item;

import net.minecraft.block.Block;

public class ItemStack {

    public int stackSize;
    private Item item;
    private int itemDamage;

    public ItemStack() {
        this((Item) null, 0);
    }

    public ItemStack(Item item) {
        this(item, 1);
    }

    public ItemStack(Block block) {
        this(block, 1);
    }

    public ItemStack(Block block, int amount) {
        this(block, amount, 0);
    }

    public ItemStack(Block block, int amount, int damage) {
        this((Item) null, amount, damage);
    }

    public ItemStack(Item item, int amount) {
        this(item, amount, 0);
    }

    public ItemStack(Item item, int amount, int damage) {
        this.item = item;
        this.stackSize = amount;
        this.itemDamage = damage;
    }

    public Item getItem() {
        return item;
    }

    public String getDisplayName() {
        return item == null ? "" : item.getItemStackDisplayName(this);
    }

    public String getUnlocalizedName() {
        return item == null ? "item.null" : item.getUnlocalizedName(this);
    }

    public int getItemDamage() {
        return itemDamage;
    }

    public void setItemDamage(int damage) {
        itemDamage = damage;
    }

    public ItemStack copy() {
        return new ItemStack(item, stackSize, itemDamage);
    }

    public boolean isItemEqual(ItemStack other) {
        return other != null && item == other.item && itemDamage == other.itemDamage;
    }

    public boolean hasTagCompound() {
        return false;
    }
}
