package cpw.mods.fml.relauncher;

/** Logical FML side values used by production code at client/server boundaries. */
public enum Side {
    CLIENT,
    SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isServer() {
        return this == SERVER;
    }
}
