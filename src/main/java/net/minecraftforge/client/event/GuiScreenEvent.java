package net.minecraftforge.client.event;

/** Event type boundary for the native ModularUI animation lifecycle. */
public class GuiScreenEvent {
    public static class DrawScreenEvent extends GuiScreenEvent {
        public static class Pre extends DrawScreenEvent {}
    }
}
