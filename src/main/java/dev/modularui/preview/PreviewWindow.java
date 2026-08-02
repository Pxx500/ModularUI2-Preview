package dev.modularui.preview;

import java.awt.BorderLayout;
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
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Interactive desktop host for local preview sessions. */
public final class PreviewWindow {

    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;
    private static final long WATCH_POLL_MILLIS = 100;
    private static final Duration WATCH_DEBOUNCE = Duration.ofMillis(300);

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

    public void watch(Path projectRoot, String className, Path outputDirectory, Path configuration) throws Exception {
        PreviewInputQueue inputs = new PreviewInputQueue();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread sessionThread = new Thread(
            () -> runWatch(projectRoot, className, outputDirectory, configuration, inputs, failure),
            "modularui-preview-watch");
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
                window.setImage(session.render().image());
            }
        } catch (Throwable throwable) {
            failure.set(throwable);
            showFailure(window, throwable);
        } finally {
            dispose(window);
        }
    }

    private void runWatch(Path projectRoot, String className, Path outputDirectory, Path configuration,
        PreviewInputQueue inputs, AtomicReference<Throwable> failure) {
        WindowHandle window = null;
        PreviewGeneration active = null;
        try {
            window = createWindow(className, placeholder(configuration), inputs);
            PreviewInputSnapshot initial = capture(projectRoot, configuration, window);
            PreviewWatchState watchState = new PreviewWatchState(initial, WATCH_DEBOUNCE);
            window.showBuilding("Building initial preview...");
            active = rebuild(projectRoot, className, outputDirectory, configuration, window, active);

            while (true) {
                PreviewInput input = inputs.poll(WATCH_POLL_MILLIS);
                if (input == PreviewInput.Stop.INSTANCE) return;
                if (input != null && active != null) interact(active, input, window);

                try {
                    watchState.observe(PreviewInputSnapshot.capture(projectRoot, configuration), System.nanoTime());
                } catch (RuntimeException snapshotFailure) {
                    window.showError(snapshotFailure);
                }
                if (!watchState.rebuildReady(System.nanoTime())) continue;
                window.showBuilding("Rebuilding preview...");
                active = rebuild(projectRoot, className, outputDirectory, configuration, window, active);
            }
        } catch (Throwable throwable) {
            failure.set(throwable);
            showFailure(window, throwable);
        } finally {
            close(active, window);
            dispose(window);
        }
    }

    private PreviewGeneration rebuild(Path projectRoot, String className, Path outputDirectory, Path configuration,
        WindowHandle window, PreviewGeneration active) {
        PreviewGeneration candidate = null;
        try {
            PreviewScreen screen = PreviewScreen.load(configuration);
            candidate = PreviewGeneration.open(
                projectRoot,
                className,
                screen,
                projectRoot.resolve("build/preview-generations"));
            new UiPreviewRunner().writeArtifacts(
                outputDirectory,
                className,
                candidate.session(),
                candidate.initialResult());
            window.setImage(candidate.initialResult().image());
            window.clearStatus();
            close(active, window);
            return candidate;
        } catch (Throwable rebuildFailure) {
            close(candidate, window);
            window.showError(rebuildFailure);
            return active;
        }
    }

    private PreviewInputSnapshot capture(Path projectRoot, Path configuration, WindowHandle window) {
        try {
            return PreviewInputSnapshot.capture(projectRoot, configuration);
        } catch (RuntimeException failure) {
            window.showError(failure);
            return PreviewInputSnapshot.synthetic("unavailable-input-snapshot");
        }
    }

    private void interact(PreviewGeneration active, PreviewInput input, WindowHandle window) {
        try {
            apply(active.session(), input);
            window.setImage(active.session().render().image());
        } catch (RuntimeException | LinkageError interactionFailure) {
            window.showError(interactionFailure);
        }
    }

    private void apply(PreviewSession session, PreviewInput input) {
        switch (input) {
            case PreviewInput.Move move -> session.moveMouse(move.x(), move.y());
            case PreviewInput.Press press -> session.press(press.button());
            case PreviewInput.Release release -> session.release(release.button());
            case PreviewInput.Scroll scroll -> session.scroll(scroll.direction(), scroll.amount());
            case PreviewInput.Stop ignored -> {
                // The stop command is handled before dispatch.
            }
        }
    }

    private WindowHandle createWindow(String className, BufferedImage image, PreviewInputQueue inputs)
        throws InterruptedException, InvocationTargetException {
        AtomicReference<WindowHandle> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PreviewCanvas canvas = new PreviewCanvas(image, inputs);
            JLabel status = new JLabel();
            status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            status.setOpaque(true);
            status.setVisible(false);
            JPanel content = new JPanel(new BorderLayout());
            content.add(canvas, BorderLayout.CENTER);
            content.add(status, BorderLayout.SOUTH);

            JFrame frame = new JFrame("ModularUI2 Preview - " + className);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent event) {
                    inputs.stop();
                }
            });
            frame.setContentPane(content);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            result.set(new WindowHandle(frame, canvas, status));
        });
        return result.get();
    }

    private BufferedImage placeholder(Path configuration) {
        PreviewScreen screen;
        try {
            screen = PreviewScreen.load(configuration);
        } catch (Exception ignored) {
            screen = PreviewScreen.fullHd();
        }
        BufferedImage image = new BufferedImage(screen.width(), screen.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(screen.backgroundColor(), true));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private void close(PreviewGeneration generation, WindowHandle window) {
        if (generation == null) return;
        try {
            generation.close();
        } catch (IOException closeFailure) {
            if (window != null) window.showError(closeFailure);
        }
    }

    private void showFailure(WindowHandle window, Throwable throwable) {
        if (window == null) return;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            window.frame(),
            message(throwable),
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

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.toString() : message;
    }

    private record WindowHandle(JFrame frame, PreviewCanvas canvas, JLabel status) {

        private void setImage(BufferedImage image) {
            SwingUtilities.invokeLater(() -> canvas.setImage(image));
        }

        private void showBuilding(String text) {
            SwingUtilities.invokeLater(() -> setStatus(text, new Color(0xFFF3CD), new Color(0x664D03)));
        }

        private void showError(Throwable failure) {
            String fullMessage = message(failure);
            String summary = fullMessage.lines()
                .findFirst()
                .orElse(fullMessage);
            SwingUtilities.invokeLater(() -> {
                setStatus("Preview stale - " + summary, new Color(0xF8D7DA), new Color(0x842029));
                status.setToolTipText("<html>" + fullMessage.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br>") + "</html>");
            });
        }

        private void clearStatus() {
            SwingUtilities.invokeLater(() -> {
                status.setText("");
                status.setToolTipText(null);
                status.setVisible(false);
            });
        }

        private void setStatus(String text, Color background, Color foreground) {
            status.setText(text);
            status.setBackground(background);
            status.setForeground(foreground);
            status.setVisible(true);
        }
    }

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
