package dev.modularui.preview;

import dev.modularui.preview.PreviewEngine.Diagnostic;
import dev.modularui.preview.PreviewEngine.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

final class ExtensionServices {

    private static final String SERVICE_ENTRY =
        "META-INF/services/dev.modularui.preview.api.PreviewExtension";

    private ExtensionServices() {}

    static List<Diagnostic> validate(List<Path> extensions, Map<String, Path> classOwners) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Path extension : extensions) {
            try {
                for (String provider : readProviders(extension)) {
                    String providerEntry = provider.replace('.', '/') + ".class";
                    if (!classOwners.containsKey(providerEntry)) {
                        diagnostics.add(new Diagnostic(
                            Severity.ERROR,
                            "extension.service.failure",
                            "Extension provider " + provider + " declared by " + extension + " was not found"));
                    }
                }
            } catch (IOException exception) {
                diagnostics.add(new Diagnostic(
                    Severity.ERROR,
                    "extension.service.failure",
                    "Could not inspect extension services in " + extension));
            }
        }
        return diagnostics;
    }

    private static List<String> readProviders(Path extension) throws IOException {
        String contents;
        if (Files.isDirectory(extension)) {
            Path descriptor = extension.resolve(SERVICE_ENTRY);
            if (!Files.isRegularFile(descriptor)) return List.of();
            contents = Files.readString(descriptor, StandardCharsets.UTF_8);
        } else {
            try (JarFile jar = new JarFile(extension.toFile())) {
                var descriptor = jar.getJarEntry(SERVICE_ENTRY);
                if (descriptor == null || descriptor.isDirectory()) return List.of();
                try (InputStream input = jar.getInputStream(descriptor)) {
                    contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return contents.lines()
            .map(ExtensionServices::removeComment)
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .distinct()
            .sorted()
            .toList();
    }

    private static String removeComment(String line) {
        int comment = line.indexOf('#');
        return comment < 0 ? line : line.substring(0, comment);
    }
}
