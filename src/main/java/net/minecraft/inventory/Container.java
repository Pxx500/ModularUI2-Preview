package net.minecraft.inventory;

import java.util.ArrayList;
import java.util.List;

/** Headless type boundary for Minecraft container screens. */
public class Container {

    public final List<Slot> inventorySlots = new ArrayList<>();

    protected Slot addSlotToContainer(Slot slot) {
        slot.slotNumber = inventorySlots.size();
        inventorySlots.add(slot);
        return slot;
    }
}
