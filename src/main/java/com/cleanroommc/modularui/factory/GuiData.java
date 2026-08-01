package com.cleanroommc.modularui.factory;

import net.minecraft.entity.player.EntityPlayer;

public class GuiData {

    private final EntityPlayer player;

    public GuiData(EntityPlayer player) {
        this.player = player;
    }

    public EntityPlayer getPlayer() {
        return player;
    }
}
