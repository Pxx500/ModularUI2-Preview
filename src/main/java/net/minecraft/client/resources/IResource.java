package net.minecraft.client.resources;

import net.minecraft.client.resources.data.IMetadataSection;

/** Headless boundary for resources returned by Minecraft's resource manager. */
public interface IResource {

    boolean hasMetadata();

    <T extends IMetadataSection> T getMetadata(String sectionName);
}
