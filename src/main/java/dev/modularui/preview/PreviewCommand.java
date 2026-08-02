package dev.modularui.preview;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

record PreviewCommand(
    Mode mode,
    Path projectRoot,
    String className,
    Path outputDirectory,
    Path configuration,
    Path actions) {

    private static final String USAGE = """
        Usage:
          preview init <project-directory>
          preview render <project-directory> [--class <name>] [--output <directory>] [--config <file>] [--actions <file>]
          preview open <project-directory> [--class <name>] [--config <file>]
          preview watch <project-directory> [--class <name>] [--output <directory>] [--config <file>]
          preview help
        """;

    enum Mode {
        INIT,
        RENDER,
        OPEN,
        WATCH,
        HELP
    }

    static PreviewCommand parse(String[] arguments) {
        if (arguments.length == 1 && (arguments[0].equals("help") || arguments[0].equals("--help"))) {
            return new PreviewCommand(Mode.HELP, null, null, null, null, null);
        }
        if (arguments.length < 2) throw new IllegalArgumentException("Missing preview command or project directory");

        Mode mode = parseMode(arguments[0]);
        Path projectRoot = Path.of(arguments[1])
            .toAbsolutePath();
        if (mode == Mode.INIT) {
            if (arguments.length != 2) throw new IllegalArgumentException("init does not accept options");
            return new PreviewCommand(mode, projectRoot, null, null, null, null);
        }

        Map<String, String> options = parseOptions(arguments);
        rejectUnsupportedOptions(mode, options);
        return new PreviewCommand(
            mode,
            projectRoot,
            options.get("--class"),
            pathOption(options, "--output"),
            pathOption(options, "--config"),
            pathOption(options, "--actions"));
    }

    static String usage() {
        return USAGE;
    }

    private static Mode parseMode(String value) {
        try {
            Mode mode = Mode.valueOf(value.toUpperCase(Locale.ROOT));
            if (mode == Mode.HELP) throw new IllegalArgumentException("help does not accept a project directory");
            return mode;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown preview command: " + value);
        }
    }

    private static Map<String, String> parseOptions(String[] arguments) {
        Map<String, String> options = new HashMap<>();
        for (int index = 2; index < arguments.length; index += 2) {
            String name = arguments[index];
            if (!name.startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + name);
            if (index + 1 >= arguments.length) throw new IllegalArgumentException("Missing value for option: " + name);
            if (options.put(name, arguments[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + name);
            }
        }
        return options;
    }

    private static void rejectUnsupportedOptions(Mode mode, Map<String, String> options) {
        for (String option : options.keySet()) {
            boolean supported = switch (option) {
                case "--class", "--config" -> true;
                case "--output" -> mode == Mode.RENDER || mode == Mode.WATCH;
                case "--actions" -> mode == Mode.RENDER;
                default -> false;
            };
            if (!supported) throw new IllegalArgumentException("Unsupported option for " + mode.name().toLowerCase(Locale.ROOT) + ": " + option);
        }
    }

    private static Path pathOption(Map<String, String> options, String name) {
        String value = options.get(name);
        return value == null ? null : Path.of(value).toAbsolutePath();
    }
}
