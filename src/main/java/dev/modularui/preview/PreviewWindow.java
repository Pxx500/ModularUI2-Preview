package dev.modularui.preview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
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
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Interactive desktop host for local preview sessions. */
public final class PreviewWindow {

    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;
    private static final long WATCH_POLL_MILLIS = 100;
    private static final Duration WATCH_DEBOUNCE = Duration.ofMillis(300);

    public void open(Path projectRoot, String className, PreviewScreen screen) throws Exception {
        open(projectRoot, className, null, screen);
    }

    public void open(Path projectRoot, String className, String scenarioId, PreviewScreen screen) throws Exception {
        PreviewInputQueue inputs = new PreviewInputQueue();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread sessionThread = new Thread(
            () -> runSession(projectRoot, className, scenarioId, screen, inputs, failure),
            "modularui-preview-session");
        sessionThread.start();
        sessionThread.join();
        rethrow(failure.get());
    }

    public void watch(Path projectRoot, String className, Path outputDirectory, Path configuration) throws Exception {
        watch(projectRoot, className, null, outputDirectory, configuration);
    }

    public void watch(Path projectRoot, String className, String scenarioId, Path outputDirectory,
        Path configuration) throws Exception {
        PreviewInputQueue inputs = new PreviewInputQueue();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread sessionThread = new Thread(
            () -> runWatch(projectRoot, className, scenarioId, outputDirectory, configuration, inputs, failure),
            "modularui-preview-watch");
        sessionThread.start();
        sessionThread.join();
        rethrow(failure.get());
    }

    private void runSession(Path projectRoot, String className, String scenarioId, PreviewScreen screen,
        PreviewInputQueue inputs, AtomicReference<Throwable> failure) {
        WindowHandle window = null;
        try (PreviewSession session = PreviewEngine.open(projectRoot, className, scenarioId, screen)) {
            PreviewResult initial = session.render();
            String selectedScenario = session.scenario().map(PreviewScenario.Metadata::id).orElse(scenarioId);
            window = createWindow(className, selectedScenario, initial.image(), initial.layout().guiScale(), inputs);
            window.setImage(initial);
            while (true) {
                PreviewInput input = inputs.take();
                if (input == PreviewInput.Stop.INSTANCE) return;
                apply(session, input);
                window.setImage(session.render());
            }
        } catch (Throwable throwable) {
            failure.set(throwable);
            showFailure(window, throwable);
        } finally {
            dispose(window);
        }
    }

    private void runWatch(Path projectRoot, String className, String scenarioId, Path outputDirectory,
        Path configuration, PreviewInputQueue inputs, AtomicReference<Throwable> failure) {
        WindowHandle window = null;
        PreviewGeneration active = null;
        try {
            window = createWindow(className, scenarioId, placeholder(configuration), 0, inputs);
            PreviewInputSnapshot initial = capture(projectRoot, configuration, window);
            PreviewWatchState watchState = new PreviewWatchState(initial, WATCH_DEBOUNCE);
            window.showBuilding("Building initial preview...");
            active = rebuild(projectRoot, className, scenarioId, outputDirectory, configuration, window, active);

            while (true) {
                PreviewInput input = inputs.poll(WATCH_POLL_MILLIS);
                if (input == PreviewInput.Stop.INSTANCE) return;
                if (input != null && active != null) interact(active, input, window);

                try {
                    watchState.observe(PreviewInputSnapshot.capture(projectRoot, configuration), System.nanoTime());
                } catch (RuntimeException snapshotFailure) {
                    window.showError(snapshotFailure);
                    continue;
                }
                if (!watchState.rebuildReady(System.nanoTime())) continue;
                window.showBuilding("Rebuilding preview...");
                active = rebuild(projectRoot, className, scenarioId, outputDirectory, configuration, window, active);
            }
        } catch (Throwable throwable) {
            failure.set(throwable);
            showFailure(window, throwable);
        } finally {
            close(active, window);
            dispose(window);
        }
    }

    private PreviewGeneration rebuild(Path projectRoot, String className, String scenarioId, Path outputDirectory,
        Path configuration, WindowHandle window, PreviewGeneration active) {
        PreviewGeneration candidate = null;
        try {
            PreviewScreen screen = PreviewScreen.load(configuration);
            candidate = PreviewGeneration.open(
                projectRoot,
                className,
                scenarioId,
                screen,
                projectRoot.resolve("build/preview-generations"));
            new UiPreviewRunner().writeArtifacts(
                outputDirectory,
                className,
                candidate.session(),
                candidate.initialResult());
            String displayName = candidate.session().scenario().map(PreviewScenario.Metadata::id).orElse(className);
            window.installGeneration(candidate.initialResult(), displayName);
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
            window.setImage(active.session().render());
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

    private WindowHandle createWindow(String className, String scenarioId, BufferedImage image, int guiScale,
        PreviewInputQueue inputs)
        throws InterruptedException, InvocationTargetException {
        AtomicReference<WindowHandle> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            PreviewCanvas canvas = new PreviewCanvas(image, inputs);
            JScrollPane scrollPane = new JScrollPane(canvas);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.setPreferredSize(new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));
            // Wheel events over the GUI belong to ModularUI. Use the scrollbars to pan at 100%.
            scrollPane.setWheelScrollingEnabled(false);
            JLabel details = new JLabel();
            JButton save = new JButton("Save PNG");
            save.setEnabled(guiScale > 0);
            save.addActionListener(event -> canvas.saveImage());
            JToggleButton fit = new JToggleButton("Fit", true);
            JToggleButton actualSize = new JToggleButton("100%");
            ButtonGroup viewingMode = new ButtonGroup();
            viewingMode.add(fit);
            viewingMode.add(actualSize);
            fit.addActionListener(event -> canvas.setFit(true));
            actualSize.addActionListener(event -> canvas.setFit(false));
            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
            toolbar.add(fit);
            toolbar.add(actualSize);
            toolbar.add(save);
            toolbar.add(details);
            JLabel status = new JLabel();
            status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            status.setOpaque(true);
            status.setVisible(false);
            JPanel content = new JPanel(new BorderLayout());
            content.add(toolbar, BorderLayout.NORTH);
            content.add(scrollPane, BorderLayout.CENTER);
            content.add(status, BorderLayout.SOUTH);

            JFrame frame = new JFrame("ModularUI2 Preview - " + (scenarioId == null ? className : scenarioId));
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
            WindowHandle handle = new WindowHandle(frame, canvas, status, details, save);
            handle.updateDetails(image, guiScale);
            result.set(handle);
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

    private record WindowHandle(JFrame frame, PreviewCanvas canvas, JLabel status, JLabel details, JButton save) {

        private void setImage(PreviewResult result) {
            SwingUtilities.invokeLater(() -> {
                canvas.setImage(result.image());
                updateDetails(result.image(), result.layout().guiScale());
                showWarnings(result);
            });
        }

        private void installGeneration(PreviewResult result, String displayName)
            throws InterruptedException, InvocationTargetException {
            SwingUtilities.invokeAndWait(() -> {
                canvas.installGeneration(result.image());
                updateDetails(result.image(), result.layout().guiScale());
                frame.setTitle("ModularUI2 Preview - " + displayName);
                status.setText("");
                status.setToolTipText(null);
                status.setVisible(false);
                showWarnings(result);
            });
        }

        private void showWarnings(PreviewResult result) {
            if (!result.warnings().isEmpty()) {
                setStatus("Limited preview - " + String.join(" | ", result.warnings()),
                    new Color(0xFFF3CD), new Color(0x664D03));
                status.setToolTipText(status.getText());
            }
        }

        private void updateDetails(BufferedImage image, int guiScale) {
            details.setText(image.getWidth() + " x " + image.getHeight()
                + (guiScale > 0 ? "  |  GUI scale " + guiScale : ""));
            save.setEnabled(guiScale > 0);
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

        private void setStatus(String text, Color background, Color foreground) {
            status.setText(text);
            status.setBackground(background);
            status.setForeground(foreground);
            status.setVisible(true);
        }
    }

    private static final class PreviewCanvas extends JComponent implements Scrollable {

        private final PreviewInputQueue inputs;
        private volatile BufferedImage image;
        private boolean fit = true;

        private PreviewCanvas(BufferedImage image, PreviewInputQueue inputs) {
            this.image = image;
            this.inputs = inputs;
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
            boolean dimensionsChanged = this.image.getWidth() != image.getWidth()
                || this.image.getHeight() != image.getHeight();
            this.image = image;
            if (dimensionsChanged) revalidate();
            repaint();
        }

        private void setFit(boolean fit) {
            this.fit = fit;
            revalidate();
            repaint();
        }

        private void saveImage() {
            // Preserve the frame shown when Save was clicked, even if watch rebuilds while the chooser is open.
            BufferedImage displayed = image;
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save current preview");
            chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.setSelectedFile(new java.io.File("preview.png"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path destination = chooser.getSelectedFile().toPath();
            if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                destination = destination.resolveSibling(destination.getFileName() + ".png");
            }
            if (Files.exists(destination) && JOptionPane.showConfirmDialog(
                this, "Replace " + destination.getFileName() + "?", "Replace image",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try {
                if (!ImageIO.write(displayed, "png", destination.toFile())) {
                    throw new IOException("PNG encoder is unavailable");
                }
            } catch (IOException failure) {
                JOptionPane.showMessageDialog(this, message(failure), "Could not save PNG", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void installGeneration(BufferedImage image) {
            inputs.resetForNewSession();
            setImage(image);
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
            return fit
                ? PreviewViewport.fit(getWidth(), getHeight(), current.getWidth(), current.getHeight())
                : PreviewViewport.actualSize(getWidth(), getHeight(), current.getWidth(), current.getHeight());
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(image.getWidth(), image.getHeight());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.HORIZONTAL ? visibleRect.width : visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return fit || getParent() != null && getParent().getWidth() >= image.getWidth();
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return fit || getParent() != null && getParent().getHeight() >= image.getHeight();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            BufferedImage current = image;
            PreviewViewport viewport = viewport();
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
