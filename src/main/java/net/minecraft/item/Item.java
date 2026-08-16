package net.minecraft.item;

import net.minecraft.util.RegistryNamespaced;

public class Item {

    public static final RegistryNamespaced itemRegistry = new RegistryNamespaced();

    private String unlocalizedName = "item.unknown";

    public static int getIdFromItem(Item item) {
        return itemRegistry.getIDForObject(item);
    }

    public Item setUnlocalizedName(String name) {
        unlocalizedName = name.startsWith("item.") ? name : "item." + name;
        return this;
    }

    public String getUnlocalizedName() {
        return unlocalizedName;
    }

    public String getUnlocalizedName(ItemStack stack) {
        return getUnlocalizedName();
    }

    public String getItemStackDisplayName(ItemStack stack) {
        return net.minecraft.util.StatCollector.translateToLocal(getUnlocalizedName(stack) + ".name")
            .trim();
    }

    public int getDamage(ItemStack stack) {
        return stack.getItemDamage();
    }

    public void setDamage(ItemStack stack, int damage) {
        stack.setItemDamage(damage);
    }

    public boolean getHasSubtypes() {
        return false;
    }
}
