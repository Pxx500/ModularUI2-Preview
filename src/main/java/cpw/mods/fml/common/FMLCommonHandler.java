package cpw.mods.fml.common;

import cpw.mods.fml.relauncher.Side;

/** Headless client-side boundary used by production mod code during preview rendering. */
public final class FMLCommonHandler {

    private static final FMLCommonHandler INSTANCE = new FMLCommonHandler();

    private FMLCommonHandler() {}

    public static FMLCommonHandler instance() {
        return INSTANCE;
    }

    public Side getSide() {
        return Side.CLIENT;
    }

    public Side getEffectiveSide() {
        return Side.CLIENT;
    }
}
