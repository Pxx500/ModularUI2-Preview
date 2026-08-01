package example;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.galaxia.core.config.ConfigMachines;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.TileEntityOxygenFiller;
import com.gtnewhorizons.galaxia.registry.block.tile.machine.gui.OxygenFillerGUI;

import dev.modularui.preview.PreviewEntrypoint;

public final class OxygenFillerPreview implements PreviewEntrypoint {

    @Override
    public String owner() {
        return "galaxia";
    }

    @Override
    public Class<?> previewedClass() {
        return OxygenFillerGUI.class;
    }

    @Override
    public Object createPanel(Context context) {
        ConfigMachines.filler.maxEnergyBuffer = 2_000;
        ConfigMachines.filler.maxOxygenBuffer = 10_000;

        TileEntityOxygenFiller tile = new TileEntityOxygenFiller();
        tile.storedEnergy = 1_250;
        tile.active = true;

        return OxygenFillerGUI.build(tile, null, (PanelSyncManager) context.panelSyncManager());
    }
}
