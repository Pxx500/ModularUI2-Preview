package dev.modularui.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

final class RuntimeProfile {

    private static final String MODULAR_UI_MARKER = "com/cleanroommc/modularui/api/IGuiHolder.class";
    private static final String SUPPORTED_MODULAR_UI_VERSION = "2.3.84-1.7.10";
    private static final List<String> REQUIRED_MODULAR_UI_CLASSES = List.of(
        MODULAR_UI_MARKER,
        "com/cleanroommc/modularui/drawable/UITexture.class",
        "com/cleanroommc/modularui/drawable/AdaptableUITexture.class",
        "com/cleanroommc/modularui/screen/ModularPanel.class",
        "com/cleanroommc/modularui/screen/UISettings.class",
        "com/cleanroommc/modularui/widget/Widget.class");

    private RuntimeProfile() {}

    static List<Diagnostic> validate(Map<String, Path> classOwners) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        validateVersion(classOwners.get(MODULAR_UI_MARKER), diagnostics);
        for (String requiredClass : REQUIRED_MODULAR_UI_CLASSES) {
            if (!classOwners.containsKey(requiredClass)) {
                diagnostics.add(error(
                    "compatibility.modularui.missing-symbol",
                    "Required ModularUI2 class is missing: " + className(requiredClass)));
            }
        }
        return diagnostics;
    }

    private static void validateVersion(Path artifact, List<Diagnostic> diagnostics) {
        if (artifact == null || Files.isDirectory(artifact)) return;
        try (JarFile jar = new JarFile(artifact.toFile())) {
            String version = jar.getManifest() == null ? null
                : jar.getManifest()
                    .getMainAttributes()
                    .getValue(java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION);
            if (version == null) version = readModularUiVersion(jar);
            if (version == null) {
                diagnostics.add(error(
                    "compatibility.modularui.version-missing",
                    "Could not determine the ModularUI2 version in " + artifact));
            } else if (!SUPPORTED_MODULAR_UI_VERSION.equals(version)) {
                diagnostics.add(error(
                    "compatibility.modularui.version",
                    "ModularUI2 " + version + " is incompatible with runtime profile "
                        + SUPPORTED_MODULAR_UI_VERSION));
            }
        } catch (IOException | RuntimeException exception) {
            diagnostics.add(error(
                "compatibility.modularui.version-metadata",
                "Could not read ModularUI2 version metadata: " + artifact));
        }
    }

    private static String readModularUiVersion(JarFile jar) throws IOException {
        var metadata = jar.getJarEntry("mcmod.info");
        if (metadata == null || metadata.isDirectory()) return null;
        try (InputStream input = jar.getInputStream(metadata)) {
            JsonElement root = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return null;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                var object = element.getAsJsonObject();
                if (object.has("modid") && "modularui2".equals(object.get("modid").getAsString())
                    && object.has("version")) {
                    return object.get("version")
                        .getAsString();
                }
            }
            return null;
        }
    }

    private static String className(String entry) {
        return entry.substring(0, entry.length() - ".class".length())
            .replace('/', '.');
    }

    private static Diagnostic error(String code, String message) {
        return new Diagnostic(Severity.ERROR, code, message);
    }
}
