package dev.modularui.preview;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

public final class UiPreviewRunner {

    public PreviewResult preview(String className, Path outputDirectory) throws IOException {
        return preview(className, outputDirectory, PreviewScreen.fullHd());
    }

    public PreviewResult preview(String className, Path outputDirectory, PreviewScreen screen) throws IOException {
        IGuiHolder<GuiData> holder = loadHolder(className);
        ModularPanel panel = holder.buildUI(new GuiData(null), new PanelSyncManager(null, true), new UISettings());
        if (panel == null) {
            throw new IllegalArgumentException("Preview class returned null instead of a ModularPanel: " + className);
        }

        PreviewResult result = new UiPreviewRenderer().render(panel, screen);
        Files.createDirectories(outputDirectory);
        ImageIO.write(
            result.image(),
            "png",
            outputDirectory.resolve("preview.png")
                .toFile());
        Files.writeString(outputDirectory.resolve("bounds.json"), toJson(className, result), StandardCharsets.UTF_8);
        return result;
    }

    @SuppressWarnings("unchecked")
    private IGuiHolder<GuiData> loadHolder(String className) {
        Class<?> previewClass;
        try {
            previewClass = Class.forName(
                className,
                true,
                Thread.currentThread()
                    .getContextClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Preview class was not found: " + className, exception);
        }
        if (!IGuiHolder.class.isAssignableFrom(previewClass)) {
            throw new IllegalArgumentException("Preview class must implement IGuiHolder: " + className);
        }

        try {
            Constructor<?> constructor = previewClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (IGuiHolder<GuiData>) constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                "Preview class needs a no-argument constructor: " + className,
                exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("Could not create preview class: " + className, exception);
        }
    }

    private String toJson(String className, PreviewResult result) {
        ScreenLayout layout = result.layout();
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"schemaVersion\": 1");
        json.append(",\n  \"previewClass\": \"")
            .append(escapeJson(className))
            .append("\"");
        json.append(",\n  \"status\": \"")
            .append(result.warnings().isEmpty() ? "complete" : "warnings")
            .append("\"");
        json.append(",\n  \"screen\": {\"width\": ")
            .append(layout.screenWidth())
            .append(", \"height\": ")
            .append(layout.screenHeight())
            .append(", \"guiScale\": ")
            .append(layout.guiScale())
            .append(", \"logicalWidth\": ")
            .append(layout.logicalWidth())
            .append(", \"logicalHeight\": ")
            .append(layout.logicalHeight())
            .append('}');
        Bounds panelLogical = layout.panelLogical();
        json.append(",\n  \"panel\": {\"local\": ");
        appendBounds(json, new Bounds(0, 0, panelLogical.width(), panelLogical.height()));
        json.append(", \"logical\": ");
        appendBounds(json, panelLogical);
        json.append(", \"screen\": ");
        appendBounds(json, layout.panelScreen());
        json.append('}');
        json.append(",\n  \"widgets\": [");
        appendWidgets(json, result.widgets());
        json.append("\n  ],\n  \"warnings\": [");
        appendWarnings(json, result.warnings());
        json.append("\n  ]\n}\n");
        return json.toString();
    }

    private void appendWidgets(StringBuilder json, List<WidgetBounds> widgets) {
        for (int index = 0; index < widgets.size(); index++) {
            WidgetBounds widget = widgets.get(index);
            json.append(index == 0 ? "\n" : ",\n");
            json.append("    {\"path\": \"")
                .append(escapeJson(widget.path()))
                .append("\", \"type\": \"")
                .append(escapeJson(widget.type()))
                .append("\"");
            json.append(", \"local\": ");
            appendBounds(json, widget.local());
            json.append(", \"logical\": ");
            appendBounds(json, widget.logical());
            json.append(", \"screen\": ");
            appendBounds(json, widget.screen());
            json.append(", \"visible\": ")
                .append(widget.visible());
            json.append(", \"clipped\": ")
                .append(widget.clipped())
                .append('}');
        }
    }

    private void appendBounds(StringBuilder json, Bounds bounds) {
        json.append("{\"x\": ")
            .append(bounds.x())
            .append(", \"y\": ")
            .append(bounds.y())
            .append(", \"width\": ")
            .append(bounds.width())
            .append(", \"height\": ")
            .append(bounds.height())
            .append('}');
    }

    private void appendWarnings(StringBuilder json, List<String> warnings) {
        for (int index = 0; index < warnings.size(); index++) {
            json.append(index == 0 ? "\n" : ",\n");
            json.append("    \"")
                .append(escapeJson(warnings.get(index)))
                .append("\"");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
