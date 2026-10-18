package example;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAsset;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialAssetStore;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObjectId;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialHierarchy;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialObject;
import com.gtnewhorizons.galaxia.registry.celestial.CelestialRegistry;
import com.gtnewhorizons.galaxia.registry.interfaces.Buildable;
import com.gtnewhorizons.galaxia.registry.outpost.AutomatedFacility;
import com.gtnewhorizons.galaxia.registry.outpost.FacilityCommand;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleKind;
import com.gtnewhorizons.galaxia.registry.outpost.module.FacilityModuleRegistry;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleInstance;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTier;
import com.gtnewhorizons.galaxia.registry.outpost.module.ModuleTierData;
import com.gtnewhorizons.galaxia.registry.outpost.module.types.ModuleMiner;
import com.gtnewhorizons.galaxia.registry.outpost.station.ModuleShape;
import com.gtnewhorizons.galaxia.registry.outpost.station.StationTileCoord;
import com.gtnewhorizons.galaxia.registry.outpost.station.settings.SettingsGroup;

import dev.modularui.preview.PreviewEntrypoint;

/** Invokes Galaxia's production miner settings modal with representative local client state. */
public final class GalaxiaModuleSettingsPreview implements PreviewEntrypoint {

    @Override
    public String owner() {
        return "galaxia";
    }

    @Override
    public Class<?> previewedClass() {
        try {
            return Class.forName("com.gtnewhorizons.galaxia.client.gui.station.MinerBlacklistConfigModalWidget");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object createPanel(Context context) {
        registerPreviewMiner();
        installPreviewHierarchy();
        AutomatedFacility facility = facility();
        CelestialAssetStore.CLIENT.clearInternal();
        CelestialAssetStore.CLIENT.registerAssetInternal(UUID.randomUUID(), facility);

        ModularPanel panel = ModularPanel.defaultPanel("galaxia_module_settings_preview", 420, 260);
        try {
            Class<?> type = Class
                .forName("com.gtnewhorizons.galaxia.client.gui.station.ModuleConfigModalController");
            Constructor<?> constructor = type.getDeclaredConstructor(
                ModularPanel.class,
                CelestialAsset.ID.class,
                int.class,
                int.class);
            constructor.setAccessible(true);
            Object controller = constructor.newInstance(panel, facility.assetId, 20, 20);
            Method open = type.getDeclaredMethod("openMinerBlacklist", int.class);
            open.setAccessible(true);
            open.invoke(controller, 0);
            return panel;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke Galaxia's production settings builder", e);
        }
    }

    private static AutomatedFacility facility() {
        AutomatedFacility facility = new AutomatedFacility(
            CelestialAsset.ID.create(),
            CelestialObjectId.OVERWORLD,
            CelestialAsset.Kind.AUTOMATED_STATION,
            Buildable.Status.OPERATIONAL);
        ModuleInstance first = null;
        SettingsGroup.ID firstGroup = null;
        for (int i = 0; i < 14; i++) {
            ModuleInstance module = FacilityModuleKind.MINER
                .create(StationTileCoord.of(1 + i, 0), ModuleShape.SINGLE, ModuleTier.EV);
            facility.addModule(module);
            facility.stationLayout().place(module);
            if (i == 0) first = module;
            if (i < 11) {
                facility.applyCommand(
                    new FacilityCommand.CreateSettingsGroup(facility.assetId, module.id, "Miner Group " + (i + 1)),
                    FacilityCommand.Authority.NONE);
                if (i == 0 && module.settingsBinding() instanceof ModuleInstance.SettingsBinding.Shared shared) {
                    firstGroup = shared.groupId();
                }
            } else {
                facility.applyCommand(
                    new FacilityCommand.JoinSettingsGroup(facility.assetId, module.id, firstGroup),
                    FacilityCommand.Authority.NONE);
            }
        }
        if (first == null) throw new IllegalStateException("Preview facility has no source module");
        return facility;
    }

    private static void registerPreviewMiner() {
        FacilityModuleRegistry.builder(FacilityModuleKind.MINER)
            .tiers(java.util.Map.of(ModuleTier.EV, new ModuleTierData(2_000L, 128L, 20, null, java.util.Map.of())))
            .behavior((module, asset) -> {})
            .factory(() -> new ModuleMiner(FacilityModuleKind.MINER))
            .settingsGroups()
            .register();
    }

    private static void installPreviewHierarchy() {
        CelestialObject star = CelestialObject.builder()
            .key(CelestialObjectId.NOVA_CAELUM)
            .objectClass(CelestialObject.Class.STAR)
            .build();
        CelestialObject overworld = CelestialObject.builder()
            .key(CelestialObjectId.OVERWORLD)
            .parent(CelestialObjectId.NOVA_CAELUM)
            .objectClass(CelestialObject.Class.PLANET)
            .build();
        try {
            CelestialRegistry.register(star);
            CelestialRegistry.register(overworld);
            java.lang.reflect.Field hierarchy = CelestialRegistry.class.getDeclaredField("hierarchy");
            hierarchy.setAccessible(true);
            hierarchy.set(null, CelestialHierarchy.builder().add(java.util.List.of(star, overworld)).build());
            java.lang.reflect.Field frozen = CelestialRegistry.class.getDeclaredField("frozen");
            frozen.setAccessible(true);
            frozen.setBoolean(null, true);
            java.lang.reflect.Field bootstrapped = CelestialRegistry.class.getDeclaredField("bootstrapped");
            bootstrapped.setAccessible(true);
            bootstrapped.setBoolean(null, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to install representative celestial hierarchy", e);
        }
    }
}
