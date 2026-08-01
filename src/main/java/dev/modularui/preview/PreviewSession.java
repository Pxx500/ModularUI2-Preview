package dev.modularui.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** A live, isolated preview of one production ModularUI2 panel. */
public final class PreviewSession implements AutoCloseable {

    private final AutoCloseable runtime;
    private final AutoCloseable lifecycle;
    private final String entrypointClassName;
    private final Path entrypointCodeSource;
    private final String previewedClassName;
    private final Path previewedCodeSource;
    private final String panelName;
    private final String panelClassName;
    private final Bounds panelBounds;
    private final Path panelCodeSource;
    private final List<WidgetBounds> widgets;
    private final Supplier<PreviewResult> renderer;

    public PreviewSession(AutoCloseable runtime, AutoCloseable lifecycle, String entrypointClassName,
        Path entrypointCodeSource, String previewedClassName, Path previewedCodeSource, String panelName,
        String panelClassName, Bounds panelBounds, Path panelCodeSource, List<WidgetBounds> widgets,
        Supplier<PreviewResult> renderer) {
        this.runtime = runtime;
        this.lifecycle = lifecycle;
        this.entrypointClassName = entrypointClassName;
        this.entrypointCodeSource = entrypointCodeSource;
        this.previewedClassName = previewedClassName;
        this.previewedCodeSource = previewedCodeSource;
        this.panelName = panelName;
        this.panelClassName = panelClassName;
        this.panelBounds = panelBounds;
        this.panelCodeSource = panelCodeSource;
        this.widgets = List.copyOf(widgets);
        this.renderer = renderer;
    }

    public String entrypointClassName() {
        return entrypointClassName;
    }

    public Path entrypointCodeSource() {
        return entrypointCodeSource;
    }

    public String previewedClassName() {
        return previewedClassName;
    }

    public Path previewedCodeSource() {
        return previewedCodeSource;
    }

    public String panelName() {
        return panelName;
    }

    public String panelClassName() {
        return panelClassName;
    }

    public Bounds panelBounds() {
        return panelBounds;
    }

    public Path panelCodeSource() {
        return panelCodeSource;
    }

    public List<WidgetBounds> widgets() {
        return widgets;
    }

    public PreviewResult render() {
        return renderer.get();
    }

    @Override
    public void close() throws IOException {
        IOException failure = close(lifecycle, null);
        failure = close(runtime, failure);
        if (failure != null) throw failure;
    }

    private static IOException close(AutoCloseable closeable, IOException previous) {
        try {
            closeable.close();
            return previous;
        } catch (Exception exception) {
            IOException failure = exception instanceof IOException ioException
                ? ioException
                : new IOException("Could not close preview session", exception);
            if (previous != null) {
                previous.addSuppressed(failure);
                return previous;
            }
            return failure;
        }
    }
}
