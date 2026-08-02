package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class DistributionLayoutTest {

    @Test
    void zipContainsAPortableRunnableToolWithoutBuildMachinePaths() throws Exception {
        String archiveProperty = System.getProperty("preview.distribution.zip");
        assertNotNull(archiveProperty, "Gradle must provide the distribution archive under test");
        try (ZipFile archive = new ZipFile(Path.of(archiveProperty).toFile())) {
            String root = archive.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.contains("/"))
                .map(name -> name.substring(0, name.indexOf('/') + 1))
                .findFirst()
                .orElseThrow();
            Set<String> entries = archive.stream()
                .map(ZipEntry::getName)
                .filter(name -> name.startsWith(root))
                .map(name -> name.substring(root.length()))
                .collect(Collectors.toSet());

            assertContains(entries, "preview.bat");
            assertContains(entries, "preview.sh");
            assertContains(entries, "README.md");
            assertContains(entries, "LICENSE");
            assertContains(entries, "THIRD_PARTY_NOTICES.md");
            assertContains(entries, "LICENSES/LGPL-3.0-only.txt");
            assertContains(entries, "examples/starter-panel/preview.properties");
            assertContains(entries, "examples/gt5-electrolyzer-direct/preview.properties");
            assertTrue(entries.stream().anyMatch(name -> name.equals("bin/modularui2-preview.bat")));
            assertTrue(entries.stream().anyMatch(name -> name.startsWith("lib/") && name.endsWith(".jar")));
            assertFalse(entries.stream().anyMatch(name -> name.endsWith("java-executable.txt")));

            String checkout = Path.of("").toAbsolutePath().normalize().toString();
            assertNoTextEntryContains(archive, entry -> entry.getName().endsWith(".bat")
                || entry.getName().endsWith(".sh")
                || entry.getName().endsWith(".txt"), checkout);
        }
    }

    private static void assertContains(Set<String> entries, String required) {
        assertTrue(entries.contains(required), () -> "Missing distribution entry: " + required);
    }

    private static void assertNoTextEntryContains(ZipFile archive, Predicate<ZipEntry> filter, String value)
        throws IOException {
        for (ZipEntry entry : archive.stream().filter(filter).toList()) {
            String content = new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(content.contains(value), () -> "Distribution entry contains checkout path: " + entry.getName());
        }
    }
}
