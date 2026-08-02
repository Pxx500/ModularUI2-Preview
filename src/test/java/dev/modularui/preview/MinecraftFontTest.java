package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftFontTest {

    private static final String FONT_ENTRY = "assets/minecraft/textures/font/ascii.png";

    @TempDir
    Path temporaryDirectory;

    @Test
    void explicitMinecraftClientOverrideTakesPriorityOverTheRfgCache() throws Exception {
        Path explicitClient = Files.createFile(temporaryDirectory.resolve("explicit-client.jar"));
        Path gradleHome = temporaryDirectory.resolve("gradle-home");
        Path cachedClient = createCachedClient(gradleHome);

        Path resolved = MinecraftFont.resolveClientJar(
            Map.of(
                "MODULARUI2_PREVIEW_MINECRAFT_JAR", explicitClient.toString(),
                "GRADLE_USER_HOME", gradleHome.toString()),
            temporaryDirectory.resolve("user-home"));

        assertEquals(explicitClient, resolved);
        assertTrue(Files.isRegularFile(cachedClient));
    }

    @Test
    void discoversTheMinecraftClientInTheConfiguredRfgCache() throws Exception {
        Path gradleHome = temporaryDirectory.resolve("custom-gradle-home");
        Path cachedClient = createCachedClient(gradleHome);

        Path resolved = MinecraftFont.resolveClientJar(
            Map.of("GRADLE_USER_HOME", gradleHome.toString()),
            temporaryDirectory.resolve("user-home"));

        assertEquals(cachedClient, resolved);
    }

    @Test
    void missingMinecraftClientExplainsHowToProvideIt() {
        Path userHome = temporaryDirectory.resolve("missing-user-home");

        IllegalStateException failure = assertThrows(
            IllegalStateException.class,
            () -> MinecraftFont.resolveClientJar(Map.of(), userHome));

        assertTrue(failure.getMessage().contains("MODULARUI2_PREVIEW_MINECRAFT_JAR"));
        assertTrue(failure.getMessage().contains("retro_futura_gradle"));
    }

    @Test
    void loadsTheAsciiFontFromTheMinecraftClientJar() throws Exception {
        Path clientJar = temporaryDirectory.resolve("client.jar");
        BufferedImage expected = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        expected.setRGB(3, 4, 0xFF12AB34);
        writeClientJar(clientJar, expected);

        BufferedImage loaded = MinecraftFont.loadFontTexture(clientJar);

        assertEquals(expected.getWidth(), loaded.getWidth());
        assertEquals(expected.getHeight(), loaded.getHeight());
        assertEquals(expected.getRGB(3, 4), loaded.getRGB(3, 4));
    }

    private Path createCachedClient(Path gradleHome) throws Exception {
        Path client = gradleHome.resolve("caches/retro_futura_gradle/mc-vanilla/1.7.10/client.jar");
        Files.createDirectories(client.getParent());
        return Files.createFile(client);
    }

    private static void writeClientJar(Path clientJar, BufferedImage font) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(font, "png", encoded);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(clientJar))) {
            output.putNextEntry(new JarEntry(FONT_ENTRY));
            output.write(encoded.toByteArray());
            output.closeEntry();
        }
    }
}
