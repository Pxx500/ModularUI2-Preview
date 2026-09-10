package dev.modularui.preview;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PreviewViewportTest {

    @Test
    void fitMapsLetterboxedMouseCoordinatesToTheFullFramebuffer() {
        PreviewViewport viewport = PreviewViewport.fit(1000, 1000, 1920, 1080);

        assertEquals(new Bounds(0, 219, 1000, 562), viewport.windowBounds());
        assertEquals(new PreviewViewport.Point(960, 540), viewport.toFramebuffer(500, 500));
        assertEquals(new PreviewViewport.Point(0, -2), viewport.toFramebuffer(0, 218));
    }

    @Test
    void actualSizePreservesPixelsAndMapsScrolledCanvasCoordinates() {
        PreviewViewport viewport = PreviewViewport.actualSize(1920, 1080, 1920, 1080);

        assertEquals(new Bounds(0, 0, 1920, 1080), viewport.windowBounds());
        // Swing delivers view coordinates including the scroll offset.
        assertEquals(new PreviewViewport.Point(1400, 800), viewport.toFramebuffer(1400, 800));
    }

    @Test
    void actualSizeCentersSmallFramesWithoutUpscaling() {
        PreviewViewport viewport = PreviewViewport.actualSize(1280, 720, 640, 480);

        assertEquals(new Bounds(320, 120, 640, 480), viewport.windowBounds());
        assertEquals(new PreviewViewport.Point(0, 0), viewport.toFramebuffer(320, 120));
        assertEquals(new PreviewViewport.Point(639, 479), viewport.toFramebuffer(959, 599));
    }
}
