package cpw.mods.fml.common;

import java.util.Map;

/** Minimal mod lookup used by ModularUI2 while running outside Forge. */
public final class Loader {

    private static Loader instance = new Loader();
    private static final ModContainer ACTIVE_MOD_CONTAINER = () -> "preview";
    private static final ModContainer MINECRAFT_CONTAINER = () -> "minecraft";
    private static boolean bootstrappingVanilla;
    @SuppressWarnings("unused")
    private java.util.List<ModContainer> mods = java.util.List.of();
    @SuppressWarnings("unused")
    private Map<String, ModContainer> namedMods = Map.of();
    @SuppressWarnings("unused")
    private Object modController;

    private Loader() {}

    public static Loader instance() {
        return instance;
    }

    public static boolean isModLoaded(String modId) {
        return false;
    }

    public Map<String, ModContainer> getIndexedModList() {
        return namedMods;
    }

    public ModContainer activeModContainer() {
        return bootstrappingVanilla ? MINECRAFT_CONTAINER : ACTIVE_MOD_CONTAINER;
    }

    public ICrashCallable getCallableCrashInformation() {
        return new ICrashCallable() {

            @Override
            public String call() {
                return "";
            }

            @Override
            public String getLabel() {
                return "FML";
            }
        };
    }

    public static void beginVanillaBootstrap() {
        bootstrappingVanilla = true;
    }

    public static void endVanillaBootstrap() {
        bootstrappingVanilla = false;
    }
}
