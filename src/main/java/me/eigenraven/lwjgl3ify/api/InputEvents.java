package me.eigenraven.lwjgl3ify.api;

/** Optional Lwjgl3ify input contract normally supplied or stripped by Forge. */
public final class InputEvents {

    private InputEvents() {}

    public interface KeyboardListener {

        void onKeyEvent(KeyEvent event);

        void onTextEvent(TextEvent event);
    }

    public record KeyEvent(int key, int scanCode, int action, int modifiers) {}

    public record TextEvent(int codePoint, int modifiers) {}
}
