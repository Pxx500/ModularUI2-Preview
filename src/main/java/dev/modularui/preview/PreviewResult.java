package dev.modularui.preview;

import java.awt.image.BufferedImage;
import java.util.List;

public record PreviewResult(
    BufferedImage image,
    ScreenLayout layout,
    List<WidgetBounds> widgets,
    List<String> warnings) {}
