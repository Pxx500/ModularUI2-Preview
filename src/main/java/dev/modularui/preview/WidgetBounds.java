package dev.modularui.preview;

public record WidgetBounds(
    String path,
    String type,
    Bounds local,
    Bounds logical,
    Bounds screen,
    boolean visible,
    boolean clipped,
    boolean enabled) {

    /** Retains the constructor used by existing preview integrations. */
    public WidgetBounds(String path, String type, Bounds local, Bounds logical, Bounds screen,
        boolean visible, boolean clipped) {
        this(path, type, local, logical, screen, visible, clipped, true);
    }
}
