package dev.modularui.preview;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Executes deterministic mouse actions against one live preview session. */
public final class PreviewActionRunner {

    private static final Gson JSON = new GsonBuilder()
        .serializeNulls()
        .setPrettyPrinting()
        .create();

    public List<ActionResult> run(Path projectRoot, String className, Path actionsFile, Path outputDirectory,
        PreviewScreen screen) throws IOException {
        List<ScriptAction> actions = parse(actionsFile);
        List<ActionResult> results = new ArrayList<>();
        Set<String> captureNames = new HashSet<>();
        Cursor cursor = new Cursor();
        UiPreviewRunner artifacts = new UiPreviewRunner();
        Files.createDirectories(outputDirectory);
        try (PreviewSession session = PreviewEngine.open(projectRoot, className, screen)) {
            for (ScriptAction action : actions) {
                execute(action, actionsFile, outputDirectory, className, session, artifacts, results, captureNames,
                    cursor);
            }
        }
        writeResults(outputDirectory.resolve("actions.json"), results);
        return List.copyOf(results);
    }

    private void execute(ScriptAction action, Path source, Path outputDirectory, String className,
        PreviewSession session, UiPreviewRunner artifacts, List<ActionResult> results, Set<String> captureNames,
        Cursor cursor) throws IOException {
        Boolean handled = null;
        String capture = null;
        switch (action.command()) {
            case MOVE -> {
                cursor.x = integer(action, 0);
                cursor.y = integer(action, 1);
                session.moveMouse(cursor.x, cursor.y);
            }
            case MOVE_WIDGET -> {
                PreviewResult rendered = session.render();
                WidgetBounds widget = rendered.widgets()
                    .stream()
                    .filter(candidate -> candidate.path()
                        .equals(action.arguments().getFirst()))
                    .findFirst()
                    .orElseThrow(() -> failure(source, action.line(),
                        "No widget exists at path " + action.arguments().getFirst()));
                cursor.x = widget.screen().x() + widget.screen().width() / 2;
                cursor.y = widget.screen().y() + widget.screen().height() / 2;
                session.moveMouse(cursor.x, cursor.y);
            }
            case PRESS -> handled = session.press(button(action));
            case RELEASE -> handled = session.release(button(action));
            case CLICK -> handled = session.click(button(action));
            case SCROLL -> handled = session.scroll(direction(action), scrollAmount(action));
            case CAPTURE -> {
                String name = action.arguments().getFirst();
                if (!captureNames.add(name)) {
                    throw failure(source, action.line(), "Capture name is repeated: " + name);
                }
                Path captureDirectory = outputDirectory.resolve("captures")
                    .resolve(name);
                PreviewResult rendered = session.render();
                capture = outputDirectory.relativize(captureDirectory)
                    .toString()
                    .replace('\\', '/');
                results.add(new ActionResult(action.line(), action.source(), null, cursor.x, cursor.y, capture));
                artifacts.writeArtifacts(captureDirectory, className, session, rendered);
                writeResults(captureDirectory.resolve("actions.json"), results);
                return;
            }
        }
        results.add(new ActionResult(action.line(), action.source(), handled, cursor.x, cursor.y, capture));
    }

    private List<ScriptAction> parse(Path source) throws IOException {
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        List<ScriptAction> actions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index)
                .trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int lineNumber = index + 1;
            String[] words = line.split("\\s+");
            Command command;
            try {
                command = Command.valueOf(words[0].toUpperCase(Locale.ROOT)
                    .replace('-', '_'));
            } catch (IllegalArgumentException exception) {
                throw failure(source, lineNumber, "Unknown action: " + words[0]);
            }
            List<String> arguments = List.of(words)
                .subList(1, words.length);
            validate(source, lineNumber, command, arguments);
            actions.add(new ScriptAction(lineNumber, line, command, List.copyOf(arguments)));
        }
        return actions;
    }

    private void validate(Path source, int line, Command command, List<String> arguments) {
        int minimum;
        int maximum;
        switch (command) {
            case MOVE -> {
                minimum = 2;
                maximum = 2;
            }
            case MOVE_WIDGET, PRESS, RELEASE, CLICK, CAPTURE -> {
                minimum = 1;
                maximum = 1;
            }
            case SCROLL -> {
                minimum = 1;
                maximum = 2;
            }
            default -> throw new IllegalStateException("Unhandled action command: " + command);
        }
        if (arguments.size() < minimum || arguments.size() > maximum) {
            throw failure(source, line, "Invalid argument count for " + command.text());
        }
        ScriptAction action = new ScriptAction(line, command.text(), command, arguments);
        switch (command) {
            case MOVE -> {
                integer(action, 0, source);
                integer(action, 1, source);
            }
            case PRESS, RELEASE, CLICK -> button(action, source);
            case SCROLL -> {
                direction(action, source);
                int amount = scrollAmount(action, source);
                if (amount <= 0) throw failure(source, line, "Scroll amount must be positive");
            }
            case CAPTURE -> {
                String name = arguments.getFirst();
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
                    throw failure(source, line, "Invalid capture name: " + name);
                }
            }
            case MOVE_WIDGET -> {
                // A widget path is resolved against live bounds while the script runs.
            }
        }
    }

    private int integer(ScriptAction action, int index) {
        return Integer.parseInt(action.arguments().get(index));
    }

    private int integer(ScriptAction action, int index, Path source) {
        try {
            return integer(action, index);
        } catch (NumberFormatException exception) {
            throw failure(source, action.line(), "Expected an integer: " + action.arguments().get(index));
        }
    }

    private MouseButton button(ScriptAction action) {
        return MouseButton.valueOf(action.arguments().getFirst()
            .toUpperCase(Locale.ROOT));
    }

    private MouseButton button(ScriptAction action, Path source) {
        try {
            return button(action);
        } catch (IllegalArgumentException exception) {
            throw failure(source, action.line(), "Mouse button must be left or right");
        }
    }

    private ScrollDirection direction(ScriptAction action) {
        return ScrollDirection.valueOf(action.arguments().getFirst()
            .toUpperCase(Locale.ROOT));
    }

    private ScrollDirection direction(ScriptAction action, Path source) {
        try {
            return direction(action);
        } catch (IllegalArgumentException exception) {
            throw failure(source, action.line(), "Scroll direction must be up or down");
        }
    }

    private int scrollAmount(ScriptAction action) {
        return action.arguments().size() == 1 ? 1 : integer(action, 1);
    }

    private int scrollAmount(ScriptAction action, Path source) {
        return action.arguments().size() == 1 ? 1 : integer(action, 1, source);
    }

    private void writeResults(Path file, List<ActionResult> results) throws IOException {
        Files.writeString(file, JSON.toJson(results) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private IllegalArgumentException failure(Path source, int line, String message) {
        return new IllegalArgumentException(source.getFileName() + ":" + line + ": " + message);
    }

    public record ActionResult(int line, String command, Boolean handled, int mouseX, int mouseY, String capture) {}

    private record ScriptAction(int line, String source, Command command, List<String> arguments) {}

    private enum Command {
        MOVE("move"),
        MOVE_WIDGET("move-widget"),
        PRESS("press"),
        RELEASE("release"),
        CLICK("click"),
        SCROLL("scroll"),
        CAPTURE("capture");

        private final String text;

        Command(String text) {
            this.text = text;
        }

        private String text() {
            return text;
        }
    }

    private static final class Cursor {

        private int x;
        private int y;
    }
}
