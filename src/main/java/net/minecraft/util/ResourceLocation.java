package net.minecraft.util;

import java.util.Locale;

public class ResourceLocation {

    private static final int HASH_MULTIPLIER = 31;

    protected final String resourceDomain;
    protected final String resourcePath;

    public ResourceLocation(String location) {
        this(0, splitObjectName(location));
    }

    public ResourceLocation(String resourceDomain, String resourcePath) {
        this(0, resourceDomain, resourcePath);
    }

    protected ResourceLocation(int ignored, String... parts) {
        this.resourceDomain = normalizeDomain(parts[0]);
        this.resourcePath = parts[1];
    }

    public String getResourceDomain() {
        return resourceDomain;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ResourceLocation other)) return false;
        return resourceDomain.equals(other.resourceDomain) && resourcePath.equals(other.resourcePath);
    }

    @Override
    public int hashCode() {
        return HASH_MULTIPLIER * resourceDomain.hashCode() + resourcePath.hashCode();
    }

    @Override
    public String toString() {
        return resourceDomain + ':' + resourcePath;
    }

    protected static String[] splitObjectName(String location) {
        String[] parts = { null, location };
        int separator = location.indexOf(':');
        if (separator >= 0) {
            parts[1] = location.substring(separator + 1);
            if (separator > 0) parts[0] = location.substring(0, separator);
        }
        return parts;
    }

    private static String normalizeDomain(String domain) {
        return domain == null || domain.isEmpty() ? "minecraft" : domain.toLowerCase(Locale.ROOT);
    }
}
