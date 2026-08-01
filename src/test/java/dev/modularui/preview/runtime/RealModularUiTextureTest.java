package dev.modularui.preview.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.modularui.preview.assets.AssetResolver;
import dev.modularui.preview.render.TextureRasterizer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RealModularUiTextureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rendersTheSelectedRegionOfARealUiTexture() throws Exception {
        Path modularUiJar = Path.of(System.getProperty("modularui.test.jar"));
        Path assetRoot = temporaryDirectory.resolve("resources");
        Path texturePath = assetRoot.resolve("assets/example/textures/gui/colors.png");
        Files.createDirectories(texturePath.getParent());
        ImageIO.write(twoColorTexture(), "png", texturePath.toFile());

        try (ProjectRuntime runtime = ProjectRuntime.open(List.of(modularUiJar))) {
            Class<?> textureType = runtime.loadClass("com.cleanroommc.modularui.drawable.UITexture");
            Class<?> colorType = runtime.loadClass("com.cleanroommc.modularui.drawable.ColorType");
            Object texture = uiTexture(textureType, colorType);

            BufferedImage rendered = new TextureRasterizer().render(
                texture,
                new AssetResolver(List.of(assetRoot)),
                3,
                2);

            assertEquals(Color.BLUE.getRGB(), rendered.getRGB(0, 0));
            assertEquals(Color.BLUE.getRGB(), rendered.getRGB(2, 1));
        }
    }

    @Test
    void preservesRealAdaptableUiTextureBordersWhenScaling() throws Exception {
        Path modularUiJar = Path.of(System.getProperty("modularui.test.jar"));
        Path assetRoot = temporaryDirectory.resolve("adaptable-resources");
        Path texturePath = assetRoot.resolve("assets/example/textures/gui/nine-slice.png");
        Files.createDirectories(texturePath.getParent());
        ImageIO.write(nineSliceTexture(), "png", texturePath.toFile());

        try (ProjectRuntime runtime = ProjectRuntime.open(List.of(modularUiJar))) {
            Class<?> textureType = runtime.loadClass("com.cleanroommc.modularui.drawable.AdaptableUITexture");
            Class<?> colorType = runtime.loadClass("com.cleanroommc.modularui.drawable.ColorType");
            Object texture = adaptableTexture(textureType, colorType);

            BufferedImage rendered = new TextureRasterizer().render(
                texture,
                new AssetResolver(List.of(assetRoot)),
                5,
                5);

            assertEquals(Color.RED.getRGB(), rendered.getRGB(0, 0));
            assertEquals(Color.GREEN.getRGB(), rendered.getRGB(1, 0));
            assertEquals(Color.BLUE.getRGB(), rendered.getRGB(4, 0));
            assertEquals(Color.YELLOW.getRGB(), rendered.getRGB(0, 1));
            assertEquals(Color.MAGENTA.getRGB(), rendered.getRGB(2, 2));
            assertEquals(Color.CYAN.getRGB(), rendered.getRGB(4, 2));
        }
    }

    private static Object uiTexture(Class<?> textureType, Class<?> colorType) throws Exception {
        Constructor<?> constructor = textureType.getConstructor(
            ResourceLocation.class,
            float.class,
            float.class,
            float.class,
            float.class,
            colorType,
            boolean.class,
            int.class);
        return constructor.newInstance(
            new ResourceLocation("example", "textures/gui/colors.png"),
            0.5F,
            0F,
            1F,
            1F,
            colorType.getField("DEFAULT")
                .get(null),
            false,
            0);
    }

    private static Object adaptableTexture(Class<?> textureType, Class<?> colorType) throws Exception {
        Constructor<?> constructor = textureType.getDeclaredConstructor(
            ResourceLocation.class,
            float.class,
            float.class,
            float.class,
            float.class,
            colorType,
            boolean.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
            new ResourceLocation("example", "textures/gui/nine-slice.png"),
            0F,
            0F,
            1F,
            1F,
            colorType.getField("DEFAULT")
                .get(null),
            false,
            0,
            3,
            3,
            1,
            1,
            1,
            1,
            false);
    }

    private static BufferedImage twoColorTexture() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        image.setRGB(1, 0, Color.BLUE.getRGB());
        return image;
    }

    private static BufferedImage nineSliceTexture() {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
        Color[] colors = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.CYAN,
            Color.WHITE,
            Color.GRAY,
            Color.BLACK };
        for (int index = 0; index < colors.length; index++) {
            image.setRGB(index % 3, index / 3, colors[index].getRGB());
        }
        return image;
    }
}
