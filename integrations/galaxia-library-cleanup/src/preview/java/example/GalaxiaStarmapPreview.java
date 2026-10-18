package example;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.client.gui.orbitalGUI.GalacticChartGui;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.celestial.asteroid.AsteroidFieldOrbitResolver;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalMechanics;

import dev.modularui.preview.PreviewEntrypoint;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

import gregtech.api.enums.Materials;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Runs Galaxia's production Starmap builder against representative local client state. */
public final class GalaxiaStarmapPreview implements PreviewEntrypoint {

    @Override
    public String owner() {
        return "galaxia";
    }

    @Override
    public Class<?> previewedClass() {
        return GalacticChartGui.class;
    }

    @Override
    public Object createPanel(Context context) {
        initializeFmlSide();
        setVanillaBootstrap(true);
        try {
            if (Block.blockRegistry.getObject("water") == null) {
                Block.registerBlocks();
            }
            Class.forName("net.minecraft.init.Blocks", true, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to initialize vanilla block constants", e);
        } finally {
            setVanillaBootstrap(false);
        }
        if (Materials.Air.getGas(1L) == null) {
            Fluid air = new Fluid("air");
            FluidRegistry.registerFluid(air);
            Materials.Air.mGas = air;
        }
        OrbitalMechanics.registerMinorBodyResolver(AsteroidFieldOrbitResolver.INSTANCE);
        CelestialRegistry.freezeAndBake();

        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        player.dimension = 0;

        return new GalacticChartGui().build((PanelSyncManager) context.panelSyncManager(), player);
    }

    private static void setVanillaBootstrap(boolean active) {
        try {
            Class<?> loader = Class.forName("cpw.mods.fml.common.Loader");
            loader.getMethod(active ? "beginVanillaBootstrap" : "endVanillaBootstrap").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Preview Loader does not support vanilla registry bootstrapping", e);
        }
    }

    private static void initializeFmlSide() {
        try {
            Class<?> log = Class.forName("cpw.mods.fml.relauncher.FMLRelaunchLog");
            java.lang.reflect.Field field = log.getDeclaredField("side");
            field.setAccessible(true);
            if (field.get(null) == null) {
                field.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to initialize the local FML side", e);
        }
    }

}
