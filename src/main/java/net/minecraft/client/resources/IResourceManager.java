package net.minecraft.client.resources;

import java.io.IOException;
import net.minecraft.util.ResourceLocation;

/** Headless boundary for optional Minecraft resource metadata lookups. */
public interface IResourceManager {

    IResource getResource(ResourceLocation location) throws IOException;
}
