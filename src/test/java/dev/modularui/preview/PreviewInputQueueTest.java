package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewInputQueueTest {

    @Test
    void coalescesOnlyAdjacentMouseMoves() {
        PreviewInputQueue queue = new PreviewInputQueue();

        queue.move(1, 2);
        queue.move(3, 4);
        queue.press(MouseButton.LEFT);
        queue.move(5, 6);
        queue.move(7, 8);
        queue.release(MouseButton.LEFT);

        assertEquals(
            List.of(
                new PreviewInput.Move(3, 4),
                new PreviewInput.Press(MouseButton.LEFT),
                new PreviewInput.Move(7, 8),
                new PreviewInput.Release(MouseButton.LEFT)),
            queue.drain());
    }

    @Test
    void mapsALetterboxedWindowBackToFramebufferCoordinates() {
        PreviewViewport viewport = PreviewViewport.fit(1000, 600, 800, 600);

        assertEquals(new Bounds(100, 0, 800, 600), viewport.windowBounds());
        assertEquals(new PreviewViewport.Point(0, 0), viewport.toFramebuffer(100, 0));
        assertEquals(new PreviewViewport.Point(799, 599), viewport.toFramebuffer(899, 599));
        assertEquals(new PreviewViewport.Point(-100, 300), viewport.toFramebuffer(0, 300));
    }
}
