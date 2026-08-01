package net.minecraft.util;

public interface IIcon {

    int getIconWidth();

    int getIconHeight();

    float getMinU();

    float getMaxU();

    float getInterpolatedU(double value);

    float getMinV();

    float getMaxV();

    float getInterpolatedV(double value);

    String getIconName();
}
