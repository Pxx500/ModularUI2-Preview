package dev.modularui.preview;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.ScrollWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;

public final class UiPreviewRenderer {

    private static final Color PANEL_COLOR = new Color(32, 32, 32, 255);
    private static final Color BUTTON_COLOR = new Color(93, 116, 153, 255);
    private static final Color BORDER_COLOR = new Color(145, 167, 205, 255);
    private static final int TEXT_SIZE = 9;
    private static final String MODULAR_UI_PACKAGE = "com.cleanroommc.modularui.";
    private static final GuiContext GUI_CONTEXT = new GuiContext();
    private static final WidgetTheme WIDGET_THEME = new WidgetTheme();

    public PreviewResult render(ModularPanel panel, PreviewScreen screen) {
        int panelWidth = panel.previewWidth(0);
        int panelHeight = panel.previewHeight(0);
        ScreenLayout layout = screen.layout(panelWidth, panelHeight);
        BufferedImage logicalImage = new BufferedImage(
            layout.logicalWidth(),
            layout.logicalHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = logicalImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(new Color(screen.backgroundColor(), true));
        graphics.fillRect(0, 0, logicalImage.getWidth(), logicalImage.getHeight());
        graphics.setColor(PANEL_COLOR);
        Bounds panelBounds = layout.panelLogical();
        graphics.fillRect(panelBounds.x(), panelBounds.y(), panelBounds.width(), panelBounds.height());

        List<WidgetBounds> widgets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        RenderState state = new RenderState(graphics, layout, widgets, warnings);
        Bounds screenClip = new Bounds(0, 0, layout.logicalWidth(), layout.logicalHeight());
        drawWidget(
            state,
            panel,
            new Frame(panelBounds.x(), panelBounds.y(), panelWidth, panelHeight),
            "0",
            screenClip);
        graphics.dispose();

        BufferedImage image = layout.toFramebuffer(logicalImage);
        return new PreviewResult(image, layout, List.copyOf(widgets), List.copyOf(warnings));
    }

    private void drawWidget(RenderState state, Widget<?> widget, Frame parent, String path, Bounds activeClip) {
        Frame frame = resolveFrame(widget, parent);
        Bounds logical = new Bounds(frame.x(), frame.y(), frame.width(), frame.height());
        Bounds local = logical.translate(-state.layout().panelLogical().x(), -state.layout().panelLogical().y());
        state.widgets()
            .add(
                new WidgetBounds(
                    path,
                    widget.getClass()
                        .getSimpleName(),
                    local,
                    logical,
                    logical.scale(state.layout().guiScale()),
                    !logical.intersection(activeClip)
                        .isEmpty(),
                    !activeClip.contains(logical)));
        drawBackgrounds(state, widget, frame);
        drawText(state.graphics(), widget, frame);
        drawChildren(state, widget, frame, path, activeClip);
        drawOverlays(state, widget, frame);
    }

    private Frame resolveFrame(Widget<?> widget, Frame parent) {
        int width = widget.previewWidth(parent.width());
        int height = widget.previewHeight(parent.height());
        int x = parent.x() + widget.previewX(parent.width(), width);
        int y = parent.y() + widget.previewY(parent.height(), height);
        return new Frame(x, y, width, height);
    }

    private void recordCustomWidgetWarning(List<String> warnings, Widget<?> widget) {
        if (!widget.getClass()
            .getName()
            .startsWith(MODULAR_UI_PACKAGE)) {
            warnings.add(
                "Custom widget rendered as placeholder: " + widget.getClass()
                    .getName());
        }
    }

    private void drawBackgrounds(RenderState state, Widget<?> widget, Frame frame) {
        recordCustomWidgetWarning(state.warnings(), widget);
        if (widget.previewBackgrounds()
            .isEmpty()) {
            if (widget instanceof ButtonWidget<?>) drawDefaultButton(state.graphics(), frame);
            return;
        }
        drawDrawables(state, widget.previewBackgrounds(), widget, frame, "background");
    }

    private void drawDefaultButton(Graphics2D graphics, Frame frame) {
        graphics.setColor(BUTTON_COLOR);
        graphics.fillRect(frame.x(), frame.y(), frame.width(), frame.height());
        graphics.setColor(BORDER_COLOR);
        graphics.drawRect(frame.x(), frame.y(), Math.max(0, frame.width() - 1), Math.max(0, frame.height() - 1));
    }

    private void drawText(Graphics2D graphics, Widget<?> widget, Frame frame) {
        if (!(widget instanceof TextWidget<?>textWidget)) return;
        int top = frame.y() + Math.max(0, (frame.height() - TEXT_SIZE) / 2);
        PreviewDrawContext.run(
            graphics,
            () -> PreviewDrawContext.drawString(
                textWidget.previewText(),
                frame.x(),
                top,
                textWidget.previewColor(),
                textWidget.previewShadow()));
    }

    private void drawChildren(RenderState state, Widget<?> widget, Frame frame, String path, Bounds activeClip) {
        if (!(widget instanceof ParentWidget<?>parentWidget)) return;
        Graphics2D childGraphics = state.graphics();
        Bounds childClip = activeClip;
        if (widget instanceof ScrollWidget<?>) {
            childGraphics = (Graphics2D) state.graphics()
                .create();
            childGraphics.clipRect(frame.x(), frame.y(), frame.width(), frame.height());
            childClip = activeClip.intersection(new Bounds(frame.x(), frame.y(), frame.width(), frame.height()));
        }
        RenderState childState = new RenderState(childGraphics, state.layout(), state.widgets(), state.warnings());
        List<Widget<?>> children = parentWidget.previewChildren();
        for (int index = 0; index < children.size(); index++) {
            drawWidget(childState, children.get(index), frame, path + "/" + index, childClip);
        }
        if (childGraphics != state.graphics()) childGraphics.dispose();
    }

    private void drawOverlays(RenderState state, Widget<?> widget, Frame frame) {
        drawDrawables(state, widget.previewOverlays(), widget, frame, "overlay");
    }

    private void drawDrawables(RenderState state, List<IDrawable> drawables, Widget<?> widget, Frame frame,
        String layer) {
        for (IDrawable drawable : drawables) {
            try {
                PreviewDrawContext.run(
                    state.graphics(),
                    () -> drawable
                        .draw(GUI_CONTEXT, frame.x(), frame.y(), frame.width(), frame.height(), WIDGET_THEME));
            } catch (RuntimeException | LinkageError exception) {
                state.warnings()
                    .add(
                        "Could not render " + layer
                            + " for "
                            + widget.getClass()
                                .getSimpleName()
                            + " at ("
                            + frame.x()
                            + ", "
                            + frame.y()
                            + "): "
                            + exception.getClass()
                                .getSimpleName());
            }
        }
    }

    private record Frame(int x, int y, int width, int height) {}

    private record RenderState(
        Graphics2D graphics,
        ScreenLayout layout,
        List<WidgetBounds> widgets,
        List<String> warnings) {}
}
