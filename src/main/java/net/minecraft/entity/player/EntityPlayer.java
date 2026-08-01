package net.minecraft.entity.player;

import net.minecraft.entity.EntityLivingBase;

/** Marker type used only to preserve ModularUI2's GuiData constructor descriptor. */
public class EntityPlayer extends EntityLivingBase {

    public final InventoryPlayer inventory = new InventoryPlayer(this);
}
