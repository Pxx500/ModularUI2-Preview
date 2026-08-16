package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewScenarioTest {

    @Test
    void rejectsMalformedCatalogMetadataThroughThePublishedCatalogContract() {
        IllegalArgumentException malformedId = assertThrows(
            IllegalArgumentException.class,
            () -> scenario("Machines/Running"));
        assertTrue(malformedId.getMessage().contains("Invalid preview scenario ID"));

        PreviewScenario duplicate = scenario("machines/running");
        PreviewCatalog catalog = () -> List.of(duplicate, duplicate);
        IllegalArgumentException duplicateId = assertThrows(
            IllegalArgumentException.class,
            catalog::validatedScenarios);
        assertEquals("Duplicate preview scenario ID: machines/running", duplicateId.getMessage());
    }

    @Test
    void rejectsActionScriptsThatEscapeThePreviewProject() {
        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class,
            () -> scenario("machines/running").actions("../outside.txt"));

        assertTrue(failure.getMessage().contains("must stay within the project"));
    }

    @Test
    void createsConciseProductionEntrypoints() {
        Object panel = new Object();
        Object syncManager = new Object();
        PreviewEntrypoint entrypoint = PreviewEntrypoint.of(
            PreviewScenarioTest.class,
            context -> {
                assertEquals(syncManager, context.panelSyncManager());
                return panel;
            });

        assertEquals(PreviewScenarioTest.class, entrypoint.previewedClass());
        assertEquals(panel, entrypoint.createPanel(new PreviewEntrypoint.Context(syncManager)));
    }

    private static PreviewScenario scenario(String id) {
        return PreviewScenario.define(
            id,
            "running machine",
            "machines",
            PreviewScenarioTest.class,
            () -> new PreviewEntrypoint() {

                @Override
                public Class<?> previewedClass() {
                    return PreviewScenarioTest.class;
                }

                @Override
                public Object createPanel(Context context) {
                    throw new UnsupportedOperationException("Metadata tests do not create a panel");
                }
            });
    }
}
