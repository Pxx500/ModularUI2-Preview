package dev.modularui.preview;

sealed interface PreviewInput {

    record Move(int x, int y) implements PreviewInput {}

    record Press(MouseButton button) implements PreviewInput {}

    record Release(MouseButton button) implements PreviewInput {}

    record Scroll(ScrollDirection direction, int amount) implements PreviewInput {}

    enum Stop implements PreviewInput {
        INSTANCE
    }
}
