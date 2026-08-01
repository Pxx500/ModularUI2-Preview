package net.minecraft.client;

import java.io.FileNotFoundException;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;

public class Minecraft {

    private static final Minecraft INSTANCE = new Minecraft();

    public final FontRenderer fontRenderer = new FontRenderer();
    public final TextureManager renderEngine = new TextureManager();
    public final EntityClientPlayerMP thePlayer = new EntityClientPlayerMP();
    private final IResourceManager resourceManager = location -> {
        throw new FileNotFoundException(location.toString());
    };

    public static Minecraft getMinecraft() {
        return INSTANCE;
    }

    public IResourceManager getResourceManager() {
        return resourceManager;
    }

    public static long getSystemTime() {
        return System.currentTimeMillis();
    }
}
