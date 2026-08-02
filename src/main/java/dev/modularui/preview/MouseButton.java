package dev.modularui.preview;

/** Mouse buttons supported by the local preview session. */
public enum MouseButton {
    LEFT(0),
    RIGHT(1);

    private final int modularUiCode;

    MouseButton(int modularUiCode) {
        this.modularUiCode = modularUiCode;
    }

    public int modularUiCode() {
        return modularUiCode;
    }
}
