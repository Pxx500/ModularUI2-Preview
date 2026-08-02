package net.minecraft.client.resources;

public interface IReloadableResourceManager extends IResourceManager {

    void registerReloadListener(IResourceManagerReloadListener listener);
}
