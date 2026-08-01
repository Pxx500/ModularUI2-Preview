package cpw.mods.fml.common;

import java.util.Map;

/** Minimal mod lookup used by ModularUI2 while running outside Forge. */
public final class Loader {

    private static final Loader INSTANCE = new Loader();
    private static final ModContainer ACTIVE_MOD_CONTAINER = () -> "preview";

    private Loader() {}

    public static Loader instance() {
        return INSTANCE;
    }

    public static boolean isModLoaded(String modId) {
        return false;
    }

    public Map<String, ModContainer> getIndexedModList() {
        return Map.of();
    }

    public ModContainer activeModContainer() {
        return ACTIVE_MOD_CONTAINER;
    }
}
