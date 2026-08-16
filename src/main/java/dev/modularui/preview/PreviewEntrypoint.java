package dev.modularui.preview;

import java.util.Objects;
import java.util.function.Function;

/** Builds the production ModularUI2 panel for a representative preview state. */
@FunctionalInterface
public interface PreviewEntrypoint {

    static PreviewEntrypoint of(Class<?> previewedClass, Function<Context, Object> panelFactory) {
        Objects.requireNonNull(previewedClass, "previewedClass");
        Objects.requireNonNull(panelFactory, "panelFactory");
        return new PreviewEntrypoint() {

            @Override
            public Class<?> previewedClass() {
                return previewedClass;
            }

            @Override
            public Object createPanel(Context context) {
                return panelFactory.apply(context);
            }
        };
    }

    default String owner() {
        return "preview";
    }

    default Class<?> previewedClass() {
        return getClass();
    }

    Object createPanel(Context context);

    record Context(Object panelSyncManager) {}
}
