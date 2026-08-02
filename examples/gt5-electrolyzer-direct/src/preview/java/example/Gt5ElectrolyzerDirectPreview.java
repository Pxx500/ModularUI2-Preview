package example;

import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;

import dev.modularui.preview.PreviewEntrypoint;

/** Local extraction of the GT5 Basic Electrolyzer ModularUI2 layout. */
public final class Gt5ElectrolyzerDirectPreview implements PreviewEntrypoint {

    private static final int SLOT_SIZE = 18;
    private static final int INVENTORY_LEFT = 7;
    private static final int INVENTORY_TOP = 82;

    private static final UITexture ITEM_SLOT = mui("textures/gui/slot/item.png");
    private static final UITexture FLUID_SLOT = mui("textures/gui/slot/fluid.png");
    private static final UITexture TOGGLE_BUTTON = UITexture.builder()
        .location("gregtech", "textures/gui/button/standard_toggle.png")
        .imageSize(SLOT_SIZE, SLOT_SIZE)
        .adaptable(1)
        .canApplyTheme()
        .build();
    private static final UITexture TOGGLE_BUTTON_RELEASED = TOGGLE_BUTTON.getSubArea(0F, 0F, 1F, 0.5F);
    private static final UITexture TOGGLE_BUTTON_SELECTED = TOGGLE_BUTTON.getSubArea(0F, 0.5F, 1F, 1F);

    private static final UITexture CHARGER = gt("textures/gui/overlay_slot/charger.png");
    private static final UITexture CANISTER = gt("textures/gui/overlay_slot/canister.png");
    private static final UITexture CHARGER_FLUID = gt("textures/gui/overlay_slot/charger_fluid.png");
    private static final UITexture CIRCUIT = gt("textures/gui/overlay_slot/int_circuit.png");
    private static final UITexture AUTOOUTPUT_FLUID = gt("textures/gui/overlay_button/autooutput_fluid.png");
    private static final UITexture AUTOOUTPUT_ITEM = gt("textures/gui/overlay_button/autooutput_item.png");
    private static final UITexture MUFFLE_OFF = gt("textures/gui/overlay_button/muffle_off.png");
    private static final UITexture MUFFLE_ON = gt("textures/gui/overlay_button/muffle_on.png");
    private static final UITexture POWER_OFF = gt("textures/gui/overlay_button/small_power_switch_off.png");
    private static final UITexture POWER_ON = gt("textures/gui/overlay_button/small_power_switch_on.png");
    private static final UITexture EXTRACT_PROGRESS = gt("textures/gui/progressbar/extract.png");

    private final BoolValue fluidAutoOutput = new BoolValue(false);
    private final BoolValue itemAutoOutput = new BoolValue(false);
    private final BoolValue muffled = new BoolValue(false);
    private final BoolValue powerEnabled = new BoolValue(true);

    @Override
    public String owner() {
        return "gregtech";
    }

    @Override
    public Object createPanel(Context context) {
        ModularPanel panel = ModularPanel.defaultPanel("basicmachine.electrolyzer.tier.01", 176, 166)
            .child(
                new TextWidget<>("Basic Electrolyzer")
                    .name("machine-title")
                    .pos(5, -10)
                    .size(100, 9)
                    .shadow(false))
            .child(slot("item-input-0", 34, 24, CHARGER))
            .child(slot("item-input-1", 52, 24, CANISTER))
            .child(
                new ProgressWidget()
                    .name("recipe-progress")
                    .pos(78, 24)
                    .size(20, 18)
                    .progress(0.62)
                    .texture(EXTRACT_PROGRESS, 20)
                    .direction(ProgressWidget.Direction.RIGHT));

        addOutputSlots(panel);
        addMachineControls(panel);
        addPlayerInventory(panel);
        return panel;
    }

    private static void addOutputSlots(ModularPanel panel) {
        int index = 0;
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 3; column++) {
                panel.child(slot("item-output-" + index++, 106 + column * SLOT_SIZE, 15 + row * SLOT_SIZE));
            }
        }
    }

    private void addMachineControls(ModularPanel panel) {
        panel.child(machineToggle("autooutput-fluid", 7, 62, SLOT_SIZE, fluidAutoOutput, AUTOOUTPUT_FLUID,
            AUTOOUTPUT_FLUID))
            .child(machineToggle("autooutput-item", 25, 62, SLOT_SIZE, itemAutoOutput, AUTOOUTPUT_ITEM,
                AUTOOUTPUT_ITEM))
            .child(texture("fluid-input", 52, 62, SLOT_SIZE, SLOT_SIZE, FLUID_SLOT, CHARGER_FLUID))
            .child(texture("charger", 79, 62, SLOT_SIZE, SLOT_SIZE, ITEM_SLOT, CHARGER))
            .child(texture("fluid-output", 106, 62, SLOT_SIZE, SLOT_SIZE, FLUID_SLOT))
            .child(slot("special-slot", 124, 62))
            .child(slot("circuit-slot", 151, 62, CIRCUIT))
            .child(smallMachineToggle("muffler", 160, 4, muffled, MUFFLE_OFF, MUFFLE_ON))
            .child(smallMachineToggle("power-switch", 160, 16, powerEnabled, POWER_OFF, POWER_ON));
    }

    private static ToggleButton machineToggle(String name, int x, int y, int size, BoolValue value,
        UITexture releasedOverlay, UITexture selectedOverlay) {
        return new ToggleButton().name(name)
            .pos(x, y)
            .size(size)
            .value(value)
            .background(false, TOGGLE_BUTTON_RELEASED)
            .background(true, TOGGLE_BUTTON_SELECTED)
            .overlay(false, releasedOverlay)
            .overlay(true, selectedOverlay);
    }

    private static ToggleButton smallMachineToggle(String name, int x, int y, BoolValue value,
        UITexture releasedOverlay, UITexture selectedOverlay) {
        return new ToggleButton().name(name)
            .pos(x, y)
            .size(12)
            .value(value)
            .background(false, GuiTextures.MC_BUTTON)
            .background(true, GuiTextures.MC_BUTTON_PRESSED)
            .hoverBackground(false, GuiTextures.MC_BUTTON_HOVERED)
            .hoverBackground(true, GuiTextures.MC_BUTTON_HOVERED_PRESSED)
            .overlay(false, releasedOverlay)
            .overlay(true, selectedOverlay);
    }

    private static void addPlayerInventory(ModularPanel panel) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                panel.child(slot("inventory-" + row + "-" + column, INVENTORY_LEFT + column * SLOT_SIZE,
                    INVENTORY_TOP + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            panel.child(slot("hotbar-" + column, INVENTORY_LEFT + column * SLOT_SIZE, 140));
        }
    }

    private static Widget<?> slot(String name, int x, int y, UITexture... overlays) {
        UITexture[] textures = new UITexture[overlays.length + 1];
        textures[0] = ITEM_SLOT;
        System.arraycopy(overlays, 0, textures, 1, overlays.length);
        return texture(name, x, y, SLOT_SIZE, SLOT_SIZE, textures);
    }

    private static Widget<?> texture(String name, int x, int y, int width, int height, UITexture... textures) {
        return new Widget<>()
            .name(name)
            .pos(x, y)
            .size(width, height)
            .background(textures);
    }

    private static UITexture mui(String path) {
        return UITexture.fullImage("modularui2", path);
    }

    private static UITexture gt(String path) {
        return UITexture.fullImage("gregtech", path);
    }
}
