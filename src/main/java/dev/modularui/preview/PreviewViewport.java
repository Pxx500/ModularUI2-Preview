package dev.modularui.preview;

record PreviewViewport(Bounds windowBounds, int framebufferWidth, int framebufferHeight) {

    static PreviewViewport fit(int windowWidth, int windowHeight, int framebufferWidth, int framebufferHeight) {
        if (windowWidth <= 0 || windowHeight <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("Viewport dimensions must be positive");
        }
        double scale = Math.min((double) windowWidth / framebufferWidth, (double) windowHeight / framebufferHeight);
        int renderedWidth = Math.max(1, (int) Math.floor(framebufferWidth * scale));
        int renderedHeight = Math.max(1, (int) Math.floor(framebufferHeight * scale));
        Bounds bounds = new Bounds(
            (windowWidth - renderedWidth) / 2,
            (windowHeight - renderedHeight) / 2,
            renderedWidth,
            renderedHeight);
        return new PreviewViewport(bounds, framebufferWidth, framebufferHeight);
    }

    Point toFramebuffer(int windowX, int windowY) {
        int x = Math.floorDiv((windowX - windowBounds.x()) * framebufferWidth, windowBounds.width());
        int y = Math.floorDiv((windowY - windowBounds.y()) * framebufferHeight, windowBounds.height());
        return new Point(x, y);
    }

    record Point(int x, int y) {}
}
