package net.minecraft.client;

import net.minecraft.client.gui.FontRenderer;

public class Minecraft {

    private static final Minecraft INSTANCE = new Minecraft();

    public final FontRenderer fontRenderer = new FontRenderer();

    public static Minecraft getMinecraft() {
        return INSTANCE;
    }
}
