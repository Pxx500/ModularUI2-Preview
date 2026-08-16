package org.lwjgl.input;

/** Headless keyboard boundary. Scripted previews currently expose no pressed keys. */
public final class Keyboard {

    private Keyboard() {}

    public static boolean isCreated() {
        return true;
    }

    public static boolean isKeyDown(int key) {
        return false;
    }
}
