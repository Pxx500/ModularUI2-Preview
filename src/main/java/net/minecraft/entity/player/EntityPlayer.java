package net.minecraft.entity.player;

import java.util.UUID;

import net.minecraft.entity.EntityLivingBase;

/** Marker type used only to preserve ModularUI2's GuiData constructor descriptor. */
public class EntityPlayer extends EntityLivingBase {

    public final InventoryPlayer inventory = new InventoryPlayer(this);
    public final PlayerCapabilities capabilities = new PlayerCapabilities();

    public UUID getUniqueID() {
        return new UUID(0L, 1L);
    }
}
