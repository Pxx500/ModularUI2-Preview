package com.gtnewhorizons.galaxia.client.gui.station;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.gtnewhorizons.galaxia.registry.celestial.*;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.*;
import com.gtnewhorizons.galaxia.registry.outpost.module.*;
import com.gtnewhorizons.galaxia.registry.outpost.recipe.*;
import com.gtnewhorizons.galaxia.registry.outpost.station.*;
import dev.modularui.preview.PreviewEntrypoint;

public final class GalaxiaRecipeDetailsPreview implements PreviewEntrypoint {
    public String owner() { return "galaxia"; }
    public Class<?> previewedClass() { return RecipeConfigModalWidget.class; }

    public Object createPanel(Context context) {
        try {
            Class<?> log = Class.forName("cpw.mods.fml.relauncher.FMLRelaunchLog");
            var side = log.getDeclaredField("side");
            side.setAccessible(true);
            side.set(null, cpw.mods.fml.relauncher.Side.CLIENT);
            Class<?> loader = Class.forName("cpw.mods.fml.common.Loader");
            loader.getMethod("beginVanillaBootstrap").invoke(null);
            Block.registerBlocks();
            loader.getMethod("endVanillaBootstrap").invoke(null);
            CelestialObject star = CelestialObject.builder().key(CelestialObjectId.NOVA_CAELUM)
                .objectClass(CelestialObject.Class.STAR).build();
            CelestialObject planet = CelestialObject.builder().key(CelestialObjectId.OVERWORLD)
                .parent(CelestialObjectId.NOVA_CAELUM).objectClass(CelestialObject.Class.PLANET).build();
            CelestialRegistry.register(star);
            CelestialRegistry.register(planet);
            var hierarchy = CelestialRegistry.class.getDeclaredField("hierarchy");
            hierarchy.setAccessible(true);
            hierarchy.set(null, CelestialHierarchy.builder().add(List.of(star, planet)).build());
            for (String flag : List.of("frozen", "bootstrapped")) {
                var field = CelestialRegistry.class.getDeclaredField(flag);
                field.setAccessible(true);
                field.setBoolean(null, true);
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        FacilityModuleRegistry.builder(FacilityModuleKind.CENTRIFUGE)
            .tiers(Map.of(ModuleTier.HV, new ModuleTierData(128L, 20, null, Map.of())))
            .recipe("gt.recipe.centrifuge").register();
        AutomatedFacility facility = new AutomatedFacility(CelestialAsset.ID.create(), CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION, Buildable.Status.OPERATIONAL);
        facility.applyCommand(new FacilityCommand.BuildModules(facility.assetId, FacilityModuleKind.CENTRIFUGE,
            FacilityModuleKind.CENTRIFUGE.defaultShape(),
            new com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent.BuildPhysicalSpec.Tier(ModuleTier.HV),
            null, true, List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))),
            new FacilityCommand.Authority(true, true));
        ModuleInstance module = facility.modules().getFirst();
        Fluid hydrogen = fluid("hydrogen", "Hydrogen");
        Fluid deuterium = fluid("deuterium", "Deuterium");
        Fluid tritium = fluid("tritium", "Tritium");
        Fluid helium = fluid("helium", "Helium");
        Fluid helium3 = fluid("helium_3", "Helium-3");
        // GT5-Unofficial 5.09.54.64, CentrifugeRecipes.java:504-523.
        // All three definitions have no item/circuit inputs and guaranteed fluid outputs.
        // Catalog indexes below are local preview positions, not server catalog identifiers.
        RecipeBook book = new RecipeBook(List.of(
            saved(0, hydrogen, 160, deuterium, 40, 20),
            saved(1, deuterium, 160, tritium, 40, 80),
            saved(2, helium, 80, helium3, 5, 80)), RecipeSchedulerMode.PRIORITY, NotDoablePolicy.SKIP);
        facility.applyCommand(new FacilityCommand.ReplaceRecipeBook(facility.assetId, module.id, book),
            FacilityCommand.Authority.NONE);
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialAssetStore.CLIENT.registerAssetInternal(UUID.randomUUID(), facility);
        ModularPanel panel = ModularPanel.defaultPanel("recipe_details_preview", 456, 336);
        ModuleConfigModalController controller = new ModuleConfigModalController(panel, facility.assetId, 8, 8);
        controller.openRecipeConfig(module.id);
        return panel;
    }
    private static SavedRecipe saved(int index, Fluid input, int inputAmount, Fluid output, int outputAmount, int eut) {
        RecipeSnapshot snapshot = RecipeSnapshot.resolved(
            (byte) com.gtnewhorizons.galaxia.compat.recipe.GTRecipeMapId.CENTRIFUGE.ordinal(), index, null, null,
            new FluidStack[] { new FluidStack(input, inputAmount) },
            new FluidStack[] { new FluidStack(output, outputAmount) }, 160, eut);
        return new SavedRecipe(snapshot, true, 0L, (byte) 1, (byte) 1, "");
    }

    private static Fluid fluid(String registryName, String defaultLocalName) {
        // MaterialsInit defines these default names dynamically during the game bootstrap.
        Fluid fluid = new Fluid(registryName) {
            @Override
            public String getLocalizedName(FluidStack stack) {
                return defaultLocalName;
            }
        };
        FluidRegistry.registerFluid(fluid);
        return fluid;
    }
}
