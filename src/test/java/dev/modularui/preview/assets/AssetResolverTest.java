package dev.modularui.preview.assets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.imageio.ImageIO;
import dev.modularui.preview.project.PreviewProject;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetResolverTest {

    private static final String ASSET_PATH = "assets/example/textures/gui/pixel.png";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesMinecraftAssetsFromDirectoriesAndJars() throws Exception {
        byte[] directoryPng = png(Color.RED);
        byte[] jarPng = png(Color.BLUE);
        Path assetRoot = temporaryDirectory.resolve("resources");
        Path directoryAsset = assetRoot.resolve(ASSET_PATH);
        Files.createDirectories(directoryAsset.getParent());
        Files.write(directoryAsset, directoryPng);
        Path assetJar = writeAssetJar(temporaryDirectory.resolve("example.jar"), jarPng);
        ResourceLocation location = new ResourceLocation("example", "textures/gui/pixel.png");

        AssetResolver.ResolvedAsset fromDirectory = new AssetResolver(List.of(assetRoot)).find(location)
            .orElseThrow();
        AssetResolver.ResolvedAsset fromJar = new AssetResolver(List.of(assetJar)).find(location)
            .orElseThrow();

        assertArrayEquals(directoryPng, fromDirectory.bytes());
        assertArrayEquals(jarPng, fromJar.bytes());
        assertTrue(fromDirectory.source().contains("resources"));
        assertTrue(fromJar.source().contains("example.jar"));
    }

    @Test
    void resolvesThePortableProjectsTopLevelAssetsDirectory() throws Exception {
        byte[] expected = png(Color.GREEN);
        Path projectRoot = Files.createDirectories(temporaryDirectory.resolve("machine-preview"));
        Path asset = projectRoot.resolve("assets/example/textures/gui/pixel.png");
        Files.createDirectories(asset.getParent());
        Files.write(asset, expected);
        PreviewProject project = PreviewProject.open(projectRoot);

        AssetResolver.ResolvedAsset resolved = new AssetResolver(project.assetSources())
            .find(new ResourceLocation("example", "textures/gui/pixel.png"))
            .orElseThrow();

        assertArrayEquals(expected, resolved.bytes());
    }

    @Test
    void loadsMinecraftTranslationsWithProjectAssetsTakingPriorityOverJars() throws Exception {
        Path assetRoot = temporaryDirectory.resolve("resources");
        Path directoryLang = assetRoot.resolve("assets/example/lang/en_US.lang");
        Files.createDirectories(directoryLang.getParent());
        Files.writeString(directoryLang, "example.title=Project title\n");
        Path jar = temporaryDirectory.resolve("translations.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("assets/example/lang/en_US.lang"));
            output.write("example.title=Jar title\nexample.status=Ready\n".getBytes());
            output.closeEntry();
        }

        var translations = new AssetResolver(List.of(assetRoot, jar)).translations("en_US").values();

        assertEquals("Project title", translations.get("example.title"));
        assertEquals("Ready", translations.get("example.status"));
    }

    private static byte[] png(Color color) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static Path writeAssetJar(Path jar, byte[] png) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ASSET_PATH));
            output.write(png);
            output.closeEntry();
        }
        return jar;
    }
}
