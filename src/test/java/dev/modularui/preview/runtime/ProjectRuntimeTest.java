package dev.modularui.preview.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import example.ProjectClass;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectRuntimeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsRealModularUiClassesAheadOfToolShadows() throws Exception {
        Path modularUiJar = Path.of(System.getProperty("modularui.test.jar"));

        try (ProjectRuntime runtime = ProjectRuntime.open(List.of(modularUiJar))) {
            Class<?> loadedType = runtime.loadClass(IDrawable.class.getName());

            assertNotSame(IDrawable.class, loadedType);
            assertEquals(modularUiJar.toRealPath(), sourcePath(loadedType).toRealPath());
        }
    }

    @Test
    void isolatesProjectCodeWhileKeepingHeadlessNamespacesParentOwned() throws Exception {
        Path projectJar = temporaryDirectory.resolve("project.jar");
        writeClasses(projectJar, ProjectClass.class, ResourceLocation.class);

        try (ProjectRuntime runtime = ProjectRuntime.open(List.of(projectJar))) {
            Class<?> projectType = runtime.loadClass(ProjectClass.class.getName());
            Class<?> headlessType = runtime.loadClass(ResourceLocation.class.getName());

            assertNotSame(ProjectClass.class, projectType);
            assertSame(ResourceLocation.class, headlessType);
        }
    }

    private static Path sourcePath(Class<?> type) throws URISyntaxException {
        return Path.of(
            type.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static void writeClasses(Path file, Class<?>... types) throws Exception {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
            for (Class<?> type : types) {
                String entryName = type.getName()
                    .replace('.', '/') + ".class";
                jar.putNextEntry(new JarEntry(entryName));
                try (InputStream bytes = type.getClassLoader()
                    .getResourceAsStream(entryName)) {
                    jar.write(bytes.readAllBytes());
                }
                jar.closeEntry();
            }
        }
    }
}
