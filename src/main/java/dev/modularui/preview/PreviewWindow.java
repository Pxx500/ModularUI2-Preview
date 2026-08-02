package dev.modularui.preview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Interactive desktop host for one local preview session. */
public final class PreviewWindow {

    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;

    public void open(Path projectRoot, String className, PreviewScreen screen) throws Exception {
        PreviewInputQueue inputs = new PreviewInputQueue();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread sessionThread = new Thread(
            () -> runSession(projectRoot, className, screen, inputs, failure),
            "modularui-preview-session");
        sessionThread.start();
        sessionThread.join();
        rethrow(failure.get());
    }

    private void runSession(Path projectRoot, String className, PreviewScreen screen, PreviewInputQueue inputs,
        AtomicReference<Throwable> failure) {
        WindowHandle window = null;
        try (PreviewSession session = PreviewEngine.open(projectRoot, className, screen)) {
            window = createWindow(className, session.render().image(), inputs);
            while (true) {
                PreviewInput input = inputs.take();
                if (input == PreviewInput.Stop.INSTANCE) return;
                apply(session, input);
                BufferedImage image = session.render().image();
                WindowHandle activeWindow = window;
                SwingUtilities.invokeLater(() -> activeWindow.canvas().setImage(image));
            }
        } catch (Throwable throwable) {
            failure.set(throwable);
            showFailure(window, throwable);
        } finally {
            dispose(window);
        }
    }

    private void apply(PreviewSession session, PreviewInput input) {
        switch (input) {
            case PreviewInput.Move move -> session.moveMouse(move.x(), move.y());
            case PreviewInput.Press press -> session.press(press.button());
            case PreviewInput.Release release -> session.release(release.button());
            case PreviewInput.Scroll scroll -> session.scroll(scroll.direction(), scroll.amount());
            case PreviewInput.Stop ignored -> {
                // The stop command is handled by the session loop before dispatch.
            }
        }
    }

    private WindowHandle createWindow(String className, BufferedImage image, PreviewInputQueue inputs)
        throws InterruptedException, InvocationTargetException {
        AtomicReference<WindowHandle> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PreviewCanvas canvas = new PreviewCanvas(image, inputs);
            JFrame frame = new JFrame("ModularUI2 Preview - " + className);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent event) {
                    inputs.stop();
                }
            });
            frame.setContentPane(canvas);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            result.set(new WindowHandle(frame, canvas));
        });
        return result.get();
    }

    private void showFailure(WindowHandle window, Throwable failure) {
        if (window == null) return;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            window.frame(),
            failure.getMessage() == null ? failure.toString() : failure.getMessage(),
            "Preview interaction failed",
            JOptionPane.ERROR_MESSAGE));
    }

    private void dispose(WindowHandle window) {
        if (window == null) return;
        SwingUtilities.invokeLater(window.frame()::dispose);
    }

    private void rethrow(Throwable failure) throws Exception {
        if (failure == null) return;
        if (failure instanceof Exception exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException("Interactive preview failed", failure);
    }

    private record WindowHandle(JFrame frame, PreviewCanvas canvas) {}

    private static final class PreviewCanvas extends JComponent {

        private final PreviewInputQueue inputs;
        private volatile BufferedImage image;

        private PreviewCanvas(BufferedImage image, PreviewInputQueue inputs) {
            this.image = image;
            this.inputs = inputs;
            setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
            addMouseMotionListener(new MouseMotionAdapter() {

                @Override
                public void mouseMoved(MouseEvent event) {
                    move(event);
                }
            });
            addMouseListener(new MouseAdapter() {

                @Override
                public void mousePressed(MouseEvent event) {
                    MouseButton button = button(event);
                    if (button == null) return;
                    move(event);
                    inputs.press(button);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    MouseButton button = button(event);
                    if (button == null) return;
                    move(event);
                    inputs.release(button);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    move(event);
                }
            });
            addMouseWheelListener(this::wheel);
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        private void move(MouseEvent event) {
            PreviewViewport.Point point = viewport().toFramebuffer(event.getX(), event.getY());
            inputs.move(point.x(), point.y());
        }

        private void wheel(MouseWheelEvent event) {
            int rotation = event.getWheelRotation();
            if (rotation == 0) return;
            move(event);
            inputs.scroll(rotation < 0 ? ScrollDirection.UP : ScrollDirection.DOWN, Math.abs(rotation));
        }

        private MouseButton button(MouseEvent event) {
            return switch (event.getButton()) {
                case MouseEvent.BUTTON1 -> MouseButton.LEFT;
                case MouseEvent.BUTTON3 -> MouseButton.RIGHT;
                default -> null;
            };
        }

        private PreviewViewport viewport() {
            BufferedImage current = image;
            return PreviewViewport.fit(getWidth(), getHeight(), current.getWidth(), current.getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            BufferedImage current = image;
            PreviewViewport viewport = PreviewViewport.fit(
                getWidth(), getHeight(), current.getWidth(), current.getHeight());
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setColor(Color.BLACK);
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Bounds bounds = viewport.windowBounds();
            graphics2D.drawImage(current, bounds.x(), bounds.y(), bounds.width(), bounds.height(), null);
            graphics2D.dispose();
        }
    }
}
