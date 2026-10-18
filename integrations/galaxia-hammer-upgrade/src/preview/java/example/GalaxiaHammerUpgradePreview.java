package example;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialHierarchy;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.interfaces.IModuleComponent;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.HammerVariant;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleHammer;
import com.gtnewhorizons.galaxia.registry.outpost.logistics.AllowShootingConfig;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModulePlacement;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.orbital.OrbitalTransferPlanner;
import dev.modularui.preview.PreviewEntrypoint;

/** Runs the production HAMMER upgrade builder against representative local client state. */
public final class GalaxiaHammerUpgradePreview implements PreviewEntrypoint {
    private static final String GUI = "com.gtnewhorizons.galaxia.client.gui.station.";

    @Override
    public String owner() { return "galaxia"; }

    @Override
    public Class<?> previewedClass() {
        try {
            return Class.forName(GUI + "ModuleUpgradeModalWidget");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object createPanel(Context context) {
        installPreviewHierarchy();
        Map<ModuleTier, ModuleTierData> tiers = new EnumMap<>(ModuleTier.class);
        for (ModuleTier tier : ModuleTier.values()) {
            if (ModuleHammer.supportsTier(HammerVariant.BASE, tier) || ModuleHammer.supportsTier(HammerVariant.BIG, tier))
                tiers.put(tier, new ModuleTierData(0L, 20, null, Map.of()));
        }
        FacilityModuleRegistry.builder(FacilityModuleKind.HAMMER).tiers(tiers)
            .factory(() -> new ModuleHammer(FacilityModuleKind.HAMMER, AllowShootingConfig.ALWAYS,
                OrbitalTransferPlanner.RoutePriority.PRIORITIZE_TOF, false, HammerVariant.BASE, 64))
            .register();
        AutomatedFacility facility = new AutomatedFacility(CelestialAsset.ID.create(), CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION, Buildable.Status.OPERATIONAL);
        FacilityCommand.Result built = facility.applyCommand(new FacilityCommand.BuildModules(facility.assetId,
            FacilityModuleKind.HAMMER, FacilityModuleKind.HAMMER.defaultShape(),
            new IModuleComponent.BuildPhysicalSpec.Hammer(ModuleTier.LuV, HammerVariant.BIG), null, true,
            List.of(ModulePlacement.at(StationTileCoord.of(1, 0)))), new FacilityCommand.Authority(true, true));
        if (built.status() != FacilityCommand.Status.CHANGED) throw new IllegalStateException("Could not seed HAMMER");
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialAssetStore.CLIENT.registerAssetInternal(UUID.randomUUID(), facility);
        ModularPanel panel = ModularPanel.defaultPanel("galaxia_hammer_upgrade_preview", 420, 320);
        try {
            Class<?> overlay = Class.forName(GUI + "StationOverlayCoordinator");
            var overlayCtor = overlay.getDeclaredConstructor();
            overlayCtor.setAccessible(true);
            Class<?> controllerType = Class.forName(GUI + "ModuleConfigModalController");
            var ctor = controllerType.getDeclaredConstructor(ModularPanel.class, CelestialAsset.ID.class,
                int.class, int.class, Class.forName(GUI + "StationTilePickerController"), overlay);
            ctor.setAccessible(true);
            Object controller = ctor.newInstance(panel, facility.assetId, 15, 12, null, overlayCtor.newInstance());
            var open = controllerType.getDeclaredMethod("openUpgrade", ModuleInstance.ID.class);
            open.setAccessible(true);
            open.invoke(controller, facility.modules().getFirst().id);
            return panel;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not open production HAMMER upgrade", e);
        }
    }

    private static void installPreviewHierarchy() {
        CelestialObject star = CelestialObject.builder().key(CelestialObjectId.NOVA_CAELUM)
            .objectClass(CelestialObject.Class.STAR).build();
        CelestialObject planet = CelestialObject.builder().key(CelestialObjectId.OVERWORLD)
            .parent(CelestialObjectId.NOVA_CAELUM).objectClass(CelestialObject.Class.PLANET).build();
        CelestialRegistry.register(star);
        CelestialRegistry.register(planet);
        try {
            var hierarchy = CelestialRegistry.class.getDeclaredField("hierarchy");
            hierarchy.setAccessible(true);
            hierarchy.set(null, CelestialHierarchy.builder().add(List.of(star, planet)).build());
            for (String flag : List.of("frozen", "bootstrapped")) {
                var field = CelestialRegistry.class.getDeclaredField(flag);
                field.setAccessible(true);
                field.setBoolean(null, true);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not install local preview hierarchy", e);
        }
    }
}
