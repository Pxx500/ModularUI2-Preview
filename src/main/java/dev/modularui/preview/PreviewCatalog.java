package dev.modularui.preview;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A discoverable set of named preview scenarios. */
@FunctionalInterface
public interface PreviewCatalog {

    List<PreviewScenario> scenarios();

    default List<PreviewScenario> validatedScenarios() {
        List<PreviewScenario> scenarios = List.copyOf(scenarios());
        Set<String> ids = new HashSet<>();
        for (PreviewScenario scenario : scenarios) {
            if (scenario == null) throw new IllegalArgumentException("Preview catalog contains a null scenario");
            if (!ids.add(scenario.id())) {
                throw new IllegalArgumentException("Duplicate preview scenario ID: " + scenario.id());
            }
        }
        return scenarios.stream()
            .sorted(java.util.Comparator.comparing(PreviewScenario::id))
            .toList();
    }

    default PreviewScenario requireScenario(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A preview scenario is required. Run 'preview list <project-directory>'.");
        }
        return validatedScenarios().stream()
            .filter(scenario -> scenario.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown preview scenario: " + id));
    }
}
