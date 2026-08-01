package dev.modularui.preview;

public record WidgetBounds(
    String path,
    String type,
    Bounds local,
    Bounds logical,
    Bounds screen,
    boolean visible,
    boolean clipped) {}
