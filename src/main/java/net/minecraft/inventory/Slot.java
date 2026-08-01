package net.minecraft.inventory;

/** Headless type boundary for ModularUI2 slot method signatures. */
public class Slot {

    public final IInventory inventory;
    public final int slotIndex;
    public int xDisplayPosition;
    public int yDisplayPosition;
    public int slotNumber;

    public Slot(IInventory inventory, int slotIndex, int xDisplayPosition, int yDisplayPosition) {
        this.inventory = inventory;
        this.slotIndex = slotIndex;
        this.xDisplayPosition = xDisplayPosition;
        this.yDisplayPosition = yDisplayPosition;
    }

    public int getSlotIndex() {
        return slotIndex;
    }
}
