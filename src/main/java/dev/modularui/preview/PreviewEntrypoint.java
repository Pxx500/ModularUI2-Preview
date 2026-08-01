package dev.modularui.preview;

/** Builds the production ModularUI2 panel for a representative preview state. */
@FunctionalInterface
public interface PreviewEntrypoint {

    default String owner() {
        return "preview";
    }

    default Class<?> previewedClass() {
        return getClass();
    }

    Object createPanel(Context context);

    record Context(Object panelSyncManager) {}
}
