package net.minecraft.launchwrapper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Minimal launch state read by ModularUI2 while running outside Minecraft. */
public final class Launch {

    public static final File minecraftHome = new File(System.getProperty("java.io.tmpdir"), "modularui2-preview");
    public static final Map<String, Object> blackboard = new HashMap<>();

    static {
        blackboard.put("fml.deobfuscatedEnvironment", true);
    }

    private Launch() {}
}
