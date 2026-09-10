package dev.modularui.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final PreviewScenario.Metadata scenario;
    private final Thread ownerThread;
    private final Interaction interaction;
    private final Supplier<PreviewResult> renderer;
    private List<WidgetBounds> widgets;
    private PreviewResult lastRender;

    public PreviewSession(AutoCloseable runtime, AutoCloseable lifecycle, String entrypointClassName,
        Path entrypointCodeSource, String previewedClassName, Path previewedCodeSource, String panelName,
        String panelClassName, Bounds panelBounds, Path panelCodeSource, List<WidgetBounds> widgets,
        PreviewScenario.Metadata scenario, Interaction interaction, Supplier<PreviewResult> renderer) {
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
        this.scenario = scenario;
        this.ownerThread = Thread.currentThread();
        this.widgets = List.copyOf(widgets);
        this.interaction = interaction;
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

    public Optional<PreviewScenario.Metadata> scenario() {
        return Optional.ofNullable(scenario);
    }

    public List<WidgetBounds> widgets() {
        return widgets;
    }

    public void moveMouse(int screenX, int screenY) {
        ensureOwnerThread();
        interaction.moveMouse(screenX, screenY);
    }

    public boolean press(MouseButton button) {
        ensureOwnerThread();
        return interaction.press(Objects.requireNonNull(button, "button"));
    }

    public boolean release(MouseButton button) {
        ensureOwnerThread();
        return interaction.release(Objects.requireNonNull(button, "button"));
    }

    public boolean click(MouseButton button) {
        boolean pressed = press(button);
        boolean released = release(button);
        return pressed || released;
    }

    public boolean scroll(ScrollDirection direction, int amount) {
        ensureOwnerThread();
        if (amount <= 0) throw new IllegalArgumentException("Scroll amount must be positive");
        return interaction.scroll(Objects.requireNonNull(direction, "direction"), amount);
    }

    public PreviewResult render() {
        ensureOwnerThread();
        PreviewResult result = renderer.get();
        widgets = result.widgets();
        lastRender = result;
        return result;
    }

    PreviewResult lastRender() {
        return lastRender;
    }

    void validateRender(PreviewResult result) {
        if (scenario != null && !scenario.previewedClass().equals(previewedClassName)) {
            throw new RenderFailure("missing_class", "Expected production class " + scenario.previewedClass()
                + " but loaded " + previewedClassName);
        }
        if (!result.warnings().isEmpty()) {
            throw new RenderFailure("unexpected_warning", "Render warnings: " + result.warnings());
        }
        if (result.widgets().isEmpty()) {
            throw new RenderFailure("incomplete_bounds", "Render did not report any widget bounds");
        }
        if (scenario == null) return;
        List<String> missing = scenario.expectedAssets().stream()
            .filter(expected -> result.assetSources().stream().noneMatch(actual -> matchesAsset(expected, actual)))
            .toList();
        if (!missing.isEmpty()) {
            throw new RenderFailure("missing_asset", "Expected assets were not rendered: " + missing);
        }
    }

    private static boolean matchesAsset(String expected, String actual) {
        String normalized = expected.replace('\\', '/');
        int namespace = normalized.indexOf(':');
        if (namespace > 0) {
            normalized = "assets/" + normalized.substring(0, namespace) + "/" + normalized.substring(namespace + 1);
        }
        return actual.replace('\\', '/').endsWith(normalized);
    }

    static final class RenderFailure extends IllegalStateException {
        private final String category;

        RenderFailure(String category, String message) {
            super(message);
            this.category = category;
        }

        String category() {
            return category;
        }
    }

    @Override
    public void close() throws IOException {
        ensureOwnerThread();
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

    private void ensureOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Preview session operations must run on the thread that opened the session");
        }
    }

    public interface Interaction {

        void moveMouse(int screenX, int screenY);

        boolean press(MouseButton button);

        boolean release(MouseButton button);

        boolean scroll(ScrollDirection direction, int amount);
    }
}
