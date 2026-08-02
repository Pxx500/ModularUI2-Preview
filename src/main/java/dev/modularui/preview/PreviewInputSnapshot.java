package dev.modularui.preview;

import dev.modularui.preview.project.PreviewProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

record PreviewInputSnapshot(Map<Path, FileStamp> files) {

    PreviewInputSnapshot {
        files = Map.copyOf(files);
    }

    static PreviewInputSnapshot capture(Path projectRoot, Path configuration) {
        PreviewProject project = PreviewProject.open(projectRoot);
        Map<Path, FileStamp> files = new LinkedHashMap<>();
        project.watchedInputs(configuration).forEach(path -> capture(path, files));
        return new PreviewInputSnapshot(files);
    }

    static PreviewInputSnapshot synthetic(String value) {
        return new PreviewInputSnapshot(Map.of(Path.of(value), new FileStamp(true, 0, value.length())));
    }

    private static void capture(Path path, Map<Path, FileStamp> files) {
        Path normalized = path.toAbsolutePath()
            .normalize();
        if (Files.notExists(normalized)) {
            files.put(normalized, FileStamp.MISSING);
            return;
        }
        files.put(normalized, stamp(normalized));
        if (!Files.isDirectory(normalized)) return;
        try (Stream<Path> descendants = Files.walk(normalized)) {
            descendants.filter(Files::isRegularFile)
                .sorted()
                .forEach(file -> files.put(file.toAbsolutePath().normalize(), stamp(file)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview input: " + normalized, exception);
        }
    }

    private static FileStamp stamp(Path path) {
        try {
            return new FileStamp(
                true,
                Files.getLastModifiedTime(path).toMillis(),
                Files.isRegularFile(path) ? Files.size(path) : -1);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview input: " + path, exception);
        }
    }

    record FileStamp(boolean exists, long modifiedMillis, long size) {

        private static final FileStamp MISSING = new FileStamp(false, 0, 0);
    }
}
