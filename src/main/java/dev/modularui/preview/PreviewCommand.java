package dev.modularui.preview;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

record PreviewCommand(
    Mode mode,
    Path projectRoot,
    String scenarioId,
    String className,
    Path outputDirectory,
    Path configuration,
    Path actions,
    VerifyOptions verification) {

    private static final String USAGE = """
        Usage:
          preview init <project-directory>
          preview list <project-directory> [--class <name>]
          preview render <project-directory> [scenario] [--class <name>] [--output <directory>] [--config <file>] [--actions <file>]
          preview open <project-directory> [scenario] [--class <name>] [--config <file>]
          preview watch <project-directory> [scenario] [--class <name>] [--output <directory>] [--config <file>]
          preview verify <project-directory> [family-or-scenario] [--full] [--failed] [--output <directory>] [--jobs <count>]
          preview doctor <project-directory> [--class <name>]
          preview help
        """;

    enum Mode {
        INIT,
        LIST,
        RENDER,
        OPEN,
        WATCH,
        VERIFY,
        DOCTOR,
        HELP
    }

    static PreviewCommand parse(String[] arguments) {
        if (arguments.length == 1 && (arguments[0].equals("help") || arguments[0].equals("--help"))) {
            return command(Mode.HELP, null, null, Map.of(), Set.of());
        }
        if (arguments.length < 2) throw new IllegalArgumentException("Missing preview command or project directory");

        Mode mode = parseMode(arguments[0]);
        Path projectRoot = Path.of(arguments[1]).toAbsolutePath();
        if (mode == Mode.INIT) {
            if (arguments.length != 2) throw new IllegalArgumentException("init does not accept options");
            return command(mode, projectRoot, null, Map.of(), Set.of());
        }

        int optionStart = 2;
        String scenarioId = null;
        if (optionStart < arguments.length && !arguments[optionStart].startsWith("--")) {
            scenarioId = arguments[optionStart++];
        }
        if ((mode == Mode.LIST || mode == Mode.DOCTOR) && scenarioId != null) {
            throw new IllegalArgumentException(mode.name().toLowerCase(Locale.ROOT) + " does not accept a scenario");
        }
        ParsedOptions options = parseOptions(arguments, optionStart);
        rejectUnsupportedOptions(mode, options);
        return command(mode, projectRoot, scenarioId, options.values(), options.flags());
    }

    static String usage() {
        return USAGE;
    }

    private static PreviewCommand command(Mode mode, Path projectRoot, String scenarioId, Map<String, String> options,
        Set<String> flags) {
        return new PreviewCommand(
            mode,
            projectRoot,
            scenarioId,
            options.get("--class"),
            pathOption(options, "--output"),
            pathOption(options, "--config"),
            pathOption(options, "--actions"),
            VerifyOptions.from(options, flags));
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

    private static ParsedOptions parseOptions(String[] arguments, int start) {
        Map<String, String> values = new HashMap<>();
        Set<String> flags = new HashSet<>();
        for (int index = start; index < arguments.length;) {
            String name = arguments[index++];
            if (!name.startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + name);
            if (name.equals("--full") || name.equals("--failed")) {
                if (!flags.add(name)) throw new IllegalArgumentException("Duplicate option: " + name);
                continue;
            }
            if (index >= arguments.length) throw new IllegalArgumentException("Missing value for option: " + name);
            if (values.put(name, arguments[index++]) != null) {
                throw new IllegalArgumentException("Duplicate option: " + name);
            }
        }
        return new ParsedOptions(Map.copyOf(values), Set.copyOf(flags));
    }

    private static void rejectUnsupportedOptions(Mode mode, ParsedOptions options) {
        for (String option : options.values().keySet()) {
            boolean supported = switch (option) {
                case "--class" -> true;
                case "--config" -> mode == Mode.RENDER || mode == Mode.OPEN || mode == Mode.WATCH
                    || mode == Mode.VERIFY;
                case "--output" -> mode == Mode.RENDER || mode == Mode.WATCH || mode == Mode.VERIFY;
                case "--actions" -> mode == Mode.RENDER;
                case "--jobs", "--timeout-default", "--timeout-extended" -> mode == Mode.VERIFY;
                default -> false;
            };
            if (!supported) throw unsupported(mode, option);
        }
        if (mode != Mode.VERIFY && !options.flags().isEmpty()) {
            throw unsupported(mode, options.flags().iterator().next());
        }
    }

    private static IllegalArgumentException unsupported(Mode mode, String option) {
        return new IllegalArgumentException(
            "Unsupported option for " + mode.name().toLowerCase(Locale.ROOT) + ": " + option);
    }

    private static Path pathOption(Map<String, String> options, String name) {
        String value = options.get(name);
        return value == null ? null : Path.of(value).toAbsolutePath();
    }

    record VerifyOptions(boolean full, boolean failedOnly, int jobs, Duration defaultTimeout, Duration extendedTimeout) {

        private static VerifyOptions from(Map<String, String> options, Set<String> flags) {
            int defaultJobs = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
            return new VerifyOptions(
                flags.contains("--full"),
                flags.contains("--failed"),
                positiveInteger(options, "--jobs", defaultJobs),
                Duration.ofSeconds(positiveInteger(options, "--timeout-default", 30)),
                Duration.ofSeconds(positiveInteger(options, "--timeout-extended", 120)));
        }

        private static int positiveInteger(Map<String, String> options, String name, int defaultValue) {
            String value = options.get(name);
            if (value == null) return defaultValue;
            try {
                int parsed = Integer.parseInt(value);
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {}
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }

    private record ParsedOptions(Map<String, String> values, Set<String> flags) {}
}
