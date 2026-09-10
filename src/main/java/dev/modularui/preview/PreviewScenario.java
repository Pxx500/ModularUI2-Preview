package dev.modularui.preview;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Immutable metadata and local-state factory for one production GUI preview. */
public final class PreviewScenario {

    private static final Pattern STABLE_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern STABLE_ID = Pattern.compile(
        "[a-z0-9]+(?:-[a-z0-9]+)*(?:/[a-z0-9]+(?:-[a-z0-9]+)*)+");

    private final String id;
    private final String description;
    private final String family;
    private final Class<?> previewedClass;
    private final Supplier<? extends PreviewEntrypoint> localStateFactory;
    private final List<String> tags;
    private final TimeoutCategory timeout;
    private final List<String> expectedAssets;
    private final String actions;
    private final KnownFailure knownFailure;

    private PreviewScenario(String id, String description, String family, Class<?> previewedClass,
        Supplier<? extends PreviewEntrypoint> localStateFactory, List<String> tags, TimeoutCategory timeout,
        List<String> expectedAssets, String actions, KnownFailure knownFailure) {
        this.id = stableId(id);
        this.description = requiredText(description, "Preview scenario description");
        this.family = stableName(family, "Preview scenario family");
        this.previewedClass = Objects.requireNonNull(previewedClass, "previewedClass");
        this.localStateFactory = Objects.requireNonNull(localStateFactory, "localStateFactory");
        this.tags = stableNames(tags, "Preview scenario tag");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.expectedAssets = expectedAssets.stream()
            .map(asset -> requiredText(asset, "Expected asset"))
            .distinct()
            .sorted()
            .toList();
        this.actions = validateActions(actions);
        this.knownFailure = knownFailure;
    }

    public static PreviewScenario define(String id, String description, String family, Class<?> previewedClass,
        Supplier<? extends PreviewEntrypoint> localStateFactory) {
        return new PreviewScenario(
            id,
            description,
            family,
            previewedClass,
            localStateFactory,
            List.of(),
            TimeoutCategory.DEFAULT,
            List.of(),
            null,
            null);
    }

    public PreviewScenario tags(String... tags) {
        return copy(stableNames(Arrays.asList(tags), "Preview scenario tag"), timeout, expectedAssets, actions);
    }

    public PreviewScenario timeout(TimeoutCategory timeout) {
        return copy(tags, Objects.requireNonNull(timeout, "timeout"), expectedAssets, actions);
    }

    public PreviewScenario expectAssets(String... expectedAssets) {
        return copy(tags, timeout, Arrays.asList(expectedAssets), actions);
    }

    public PreviewScenario actions(String actions) {
        return copy(tags, timeout, expectedAssets, actions);
    }

    /** Documents one exact failure without skipping the scenario or reporting it as passed. */
    public PreviewScenario knownFailure(String category, String causeMessage, String reason) {
        return new PreviewScenario(id, description, family, previewedClass, localStateFactory, tags, timeout,
            expectedAssets, actions, new KnownFailure(category, causeMessage, reason));
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public String family() {
        return family;
    }

    public Class<?> previewedClass() {
        return previewedClass;
    }

    public List<String> tags() {
        return tags;
    }

    public TimeoutCategory timeout() {
        return timeout;
    }

    public List<String> expectedAssets() {
        return expectedAssets;
    }

    public Optional<String> actions() {
        return Optional.ofNullable(actions);
    }

    public Metadata metadata() {
        return new Metadata(
            id,
            description,
            family,
            previewedClass.getName(),
            tags,
            timeout,
            expectedAssets,
            actions,
            knownFailure);
    }

    public PreviewEntrypoint createEntrypoint() {
        PreviewEntrypoint entrypoint = localStateFactory.get();
        if (entrypoint == null) {
            throw new IllegalArgumentException("Preview scenario returned null local state: " + id);
        }
        Class<?> actualPreviewedClass = entrypoint.previewedClass();
        if (actualPreviewedClass == null) {
            throw new IllegalArgumentException("Preview scenario returned a null previewed class: " + id);
        }
        if (!previewedClass.equals(actualPreviewedClass)) {
            throw new IllegalArgumentException(
                "Preview scenario " + id + " declares " + previewedClass.getName()
                    + " but its local state previews " + actualPreviewedClass.getName());
        }
        return entrypoint;
    }

    private PreviewScenario copy(List<String> tags, TimeoutCategory timeout, List<String> expectedAssets,
        String actions) {
        return new PreviewScenario(
            id,
            description,
            family,
            previewedClass,
            localStateFactory,
            tags,
            timeout,
            expectedAssets,
            actions,
            knownFailure);
    }

    private static String stableId(String value) {
        String id = requiredText(value, "Preview scenario ID");
        if (!STABLE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid preview scenario ID: " + id);
        }
        return id;
    }

    private static String stableName(String value, String label) {
        String name = requiredText(value, label);
        if (!STABLE_NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid " + label + ": " + name);
        return name;
    }

    private static List<String> stableNames(List<String> values, String label) {
        return values.stream()
            .map(value -> stableName(value, label))
            .distinct()
            .sorted()
            .toList();
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + " must be non-blank and trimmed");
        }
        return value;
    }

    private static String validateActions(String actions) {
        if (actions == null) return null;
        String value = requiredText(actions, "Preview scenario actions path");
        Path path = Path.of(value);
        if (path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Preview scenario actions path must stay within the project: " + value);
        }
        return path.normalize()
            .toString()
            .replace('\\', '/');
    }

    public enum TimeoutCategory {
        DEFAULT,
        EXTENDED
    }

    public record Metadata(String id, String description, String family, String previewedClass, List<String> tags,
        TimeoutCategory timeout, List<String> expectedAssets, String actions, KnownFailure knownFailure) {

        public Metadata(String id, String description, String family, String previewedClass, List<String> tags,
            TimeoutCategory timeout, List<String> expectedAssets, String actions) {
            this(id, description, family, previewedClass, tags, timeout, expectedAssets, actions, null);
        }

        public Metadata {
            tags = List.copyOf(tags);
            expectedAssets = List.copyOf(expectedAssets);
        }
    }

    public record KnownFailure(String category, String causeMessage, String reason) {
        public KnownFailure {
            requiredText(category, "Known failure category");
            requiredText(causeMessage, "Known failure cause message");
            requiredText(reason, "Known failure reason");
        }

        boolean matches(String actualCategory, Throwable failure) {
            if (!category.equals(actualCategory)) return false;
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (causeMessage.equals(cause.getMessage())) return true;
            }
            return false;
        }
    }
}
