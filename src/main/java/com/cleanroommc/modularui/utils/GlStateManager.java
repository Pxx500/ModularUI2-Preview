package com.cleanroommc.modularui.utils;

import dev.modularui.preview.PreviewDrawContext;

public final class GlStateManager {

    private GlStateManager() {}

    public static void color(float red, float green, float blue, float alpha) {
        PreviewDrawContext.color(red, green, blue, alpha);
    }
}
