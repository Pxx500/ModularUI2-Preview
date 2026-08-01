package dev.modularui.preview.render;

import dev.modularui.preview.assets.AssetResolver;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import net.minecraft.util.ResourceLocation;

public final class TextureRasterizer {

    private static final String ADAPTABLE_TEXTURE = "com.cleanroommc.modularui.drawable.AdaptableUITexture";
    private static final int NINE_SLICE_SEGMENTS = 3;

    public BufferedImage render(Object texture, AssetResolver assets, int width, int height) {
        ResourceLocation location = read(texture, "location", ResourceLocation.class);
        BufferedImage source = loadImage(assets, location);
        float u0 = readFloat(texture, "u0");
        float v0 = readFloat(texture, "v0");
        float u1 = readFloat(texture, "u1");
        float v1 = readFloat(texture, "v1");

        int sourceLeft = Math.round(u0 * source.getWidth());
        int sourceTop = Math.round(v0 * source.getHeight());
        int sourceRight = Math.round(u1 * source.getWidth());
        int sourceBottom = Math.round(v1 * source.getHeight());
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (isAdaptable(texture)) {
                drawNineSlice(
                    graphics,
                    source,
                    texture,
                    width,
                    height,
                    sourceLeft,
                    sourceTop,
                    sourceRight,
                    sourceBottom);
            } else {
                drawRegion(
                    graphics,
                    source,
                    new Region(0, 0, width, height),
                    new Region(sourceLeft, sourceTop, sourceRight, sourceBottom));
            }
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static void drawNineSlice(Graphics2D graphics, BufferedImage source, Object texture, int width, int height,
        int sourceLeft, int sourceTop, int sourceRight, int sourceBottom) {
        if (read(texture, "tiled", Boolean.class)) {
            throw new IllegalArgumentException("Tiled AdaptableUITexture is not supported by this runtime profile");
        }
        int imageWidth = read(texture, "imageWidth", Integer.class);
        int imageHeight = read(texture, "imageHeight", Integer.class);
        int left = read(texture, "bl", Integer.class);
        int top = read(texture, "bt", Integer.class);
        int right = read(texture, "br", Integer.class);
        int bottom = read(texture, "bb", Integer.class);
        int sourceWidth = sourceRight - sourceLeft;
        int sourceHeight = sourceBottom - sourceTop;
        int sourceLeftBorder = Math.round(left * sourceWidth / (float) imageWidth);
        int sourceTopBorder = Math.round(top * sourceHeight / (float) imageHeight);
        int sourceRightBorder = Math.round(right * sourceWidth / (float) imageWidth);
        int sourceBottomBorder = Math.round(bottom * sourceHeight / (float) imageHeight);

        int[] destinationX = { 0, left, width - right, width };
        int[] destinationY = { 0, top, height - bottom, height };
        int[] sourceX = { sourceLeft, sourceLeft + sourceLeftBorder, sourceRight - sourceRightBorder, sourceRight };
        int[] sourceY = { sourceTop, sourceTop + sourceTopBorder, sourceBottom - sourceBottomBorder, sourceBottom };
        for (int row = 0; row < NINE_SLICE_SEGMENTS; row++) {
            for (int column = 0; column < NINE_SLICE_SEGMENTS; column++) {
                drawRegion(
                    graphics,
                    source,
                    new Region(
                        destinationX[column],
                        destinationY[row],
                        destinationX[column + 1],
                        destinationY[row + 1]),
                    new Region(sourceX[column], sourceY[row], sourceX[column + 1], sourceY[row + 1]));
            }
        }
    }

    private static void drawRegion(Graphics2D graphics, BufferedImage source, Region destination, Region sourceRegion) {
        graphics.drawImage(
            source,
            destination.left(),
            destination.top(),
            destination.right(),
            destination.bottom(),
            sourceRegion.left(),
            sourceRegion.top(),
            sourceRegion.right(),
            sourceRegion.bottom(),
            null);
    }

    private static boolean isAdaptable(Object texture) {
        Class<?> type = texture.getClass();
        while (type != null) {
            if (ADAPTABLE_TEXTURE.equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static BufferedImage loadImage(AssetResolver assets, ResourceLocation location) {
        AssetResolver.ResolvedAsset asset = assets.find(location)
            .orElseThrow(() -> new IllegalArgumentException("Preview texture was not found: " + location));
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(asset.bytes()));
            if (image == null) throw new IllegalArgumentException("Preview texture is not a supported image: " + location);
            return image;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decode preview texture: " + location, exception);
        }
    }

    private static float readFloat(Object target, String name) {
        return read(target, name, Float.class);
    }

    private static <T> T read(Object target, String name, Class<T> type) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException(
                "Unsupported ModularUI2 texture ABI: " + target.getClass()
                    .getName()
                    + '.'
                    + name,
                exception);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private record Region(int left, int top, int right, int bottom) {}
}
