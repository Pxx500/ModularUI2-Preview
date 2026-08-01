package dev.modularui.preview;

public record Bounds(int x, int y, int width, int height) {

    public Bounds {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Bounds dimensions cannot be negative");
    }

    public Bounds translate(int offsetX, int offsetY) {
        return new Bounds(x + offsetX, y + offsetY, width, height);
    }

    public Bounds scale(int factor) {
        if (factor <= 0) throw new IllegalArgumentException("Bounds scale must be positive");
        return new Bounds(x * factor, y * factor, width * factor, height * factor);
    }

    public Bounds intersection(Bounds other) {
        int intersectionX = Math.max(x, other.x);
        int intersectionY = Math.max(y, other.y);
        int right = Math.min(x + width, other.x + other.width);
        int bottom = Math.min(y + height, other.y + other.height);
        return new Bounds(intersectionX, intersectionY, Math.max(0, right - intersectionX), Math.max(0, bottom - intersectionY));
    }

    public boolean contains(Bounds other) {
        return other.x >= x && other.y >= y && other.x + other.width <= x + width
            && other.y + other.height <= y + height;
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }
}
