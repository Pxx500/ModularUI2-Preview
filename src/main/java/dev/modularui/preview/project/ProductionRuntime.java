package dev.modularui.preview.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Owns preparation and caching of a production project's runtime classpath. */
final class ProductionRuntime {

    private static final String CLASSPATH_FILE = "runtime-classpath.txt";
    private static final String PRODUCTION_PROJECT = "production.project";
    private static final String PRODUCTION_TASK = "production.gradle.task";
    private static final String DEFAULT_TASK = "classes";
    private static final String PROBE_TASK = "_modularUiPreviewClasspath";
    private static final String OUTPUT_ENVIRONMENT = "MODULAR_UI_PREVIEW_CLASSPATH_OUTPUT";
    private static final String INIT_SCRIPT_RESOURCE = "/dev/modularui/preview/gradle/preview-classpath.init.gradle";
    private static final Duration GRADLE_TIMEOUT = Duration.ofMinutes(5);

    private final Path previewRoot;
    private final Path classpathFile;
    private final Path productionProject;
    private final String productionTask;

    private ProductionRuntime(Path previewRoot, Path classpathFile, Path productionProject, String productionTask) {
        this.previewRoot = previewRoot;
        this.classpathFile = classpathFile;
        this.productionProject = productionProject;
        this.productionTask = productionTask;
    }

    static ProductionRuntime open(Path previewRoot, Map<String, String> properties) {
        Path classpathFile = previewRoot.resolve(CLASSPATH_FILE);
        String configuredProject = properties.get(PRODUCTION_PROJECT);
        if (configuredProject == null) return new ProductionRuntime(previewRoot, classpathFile, null, null);
        if (Files.isRegularFile(classpathFile)) {
            throw failure("classpath_error", PRODUCTION_PROJECT + " cannot be combined with " + CLASSPATH_FILE);
        }
        Path project = resolve(previewRoot, configuredProject);
        String task = properties.getOrDefault(PRODUCTION_TASK, DEFAULT_TASK).trim();
        if (!task.matches("[A-Za-z0-9:_-]+")) {
            throw failure("gradle_error", "Invalid production Gradle task: " + task);
        }
        return new ProductionRuntime(previewRoot, null, project, task);
    }

    List<Path> resolve() {
        List<Path> runtime = productionProject == null ? loadClasspathFile() : resolveGradleProject();
        return runtime.stream()
            .filter(path -> !isModularUiRuntime(path))
            .distinct()
            .toList();
    }

    List<Path> watchedInputs() {
        if (productionProject == null) {
            if (classpathFile == null) return List.of();
            return Stream.concat(Stream.of(classpathFile), loadClasspathFile().stream()).toList();
        }
        return productionInputs();
    }

    private List<Path> loadClasspathFile() {
        if (classpathFile == null || !Files.isRegularFile(classpathFile)) return List.of();
        try {
            return validateEntries(Files.readAllLines(classpathFile), previewRoot);
        } catch (IOException exception) {
            throw failure("classpath_error", "Could not read production runtime classpath: " + classpathFile,
                exception);
        }
    }

    private List<Path> resolveGradleProject() {
        if (!Files.isDirectory(productionProject)) {
            throw failure("gradle_error", "Production project directory does not exist: " + productionProject);
        }
        Path cache = previewRoot.resolve("build/preview-runtime");
        Path fingerprintFile = cache.resolve("fingerprint.txt");
        Path cachedClasspath = cache.resolve("runtime-classpath.txt");
        String fingerprint = fingerprint();
        try {
            if (Files.isRegularFile(fingerprintFile)
                && fingerprint.equals(Files.readString(fingerprintFile, StandardCharsets.UTF_8).trim())
                && Files.isRegularFile(cachedClasspath)) {
                List<Path> cached = validateEntries(Files.readAllLines(cachedClasspath), productionProject);
                if (!cached.isEmpty()) return cached;
            }
            Files.createDirectories(cache);
            Path initScript = materializeInitScript(cache);
            Path candidate = cache.resolve("runtime-classpath.candidate.txt");
            Files.deleteIfExists(candidate);
            runGradle(initScript, candidate, cache.resolve("gradle.log"));
            if (!Files.isRegularFile(candidate)) {
                throw failure("missing_output", "Gradle did not produce a production classpath: " + candidate);
            }
            List<Path> resolved = validateEntries(Files.readAllLines(candidate), productionProject).stream()
                .filter(path -> !isModularUiRuntime(path))
                .toList();
            if (resolved.isEmpty()) {
                throw failure("classpath_error", "Gradle produced an empty production classpath for "
                    + productionProject);
            }
            Files.write(cachedClasspath, resolved.stream().map(Path::toString).toList(), StandardCharsets.UTF_8);
            Files.writeString(fingerprintFile, fingerprint + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.deleteIfExists(candidate);
            return resolved;
        } catch (IOException exception) {
            throw failure("classpath_error", "Could not cache the production classpath below " + cache, exception);
        }
    }

    private void runGradle(Path initScript, Path output, Path log) throws IOException {
        Path windowsWrapper = productionProject.resolve("gradlew.bat");
        Path unixWrapper = productionProject.resolve("gradlew");
        boolean windows = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path wrapper = windows ? windowsWrapper : unixWrapper;
        if (!Files.isRegularFile(wrapper)) {
            throw failure("gradle_error", "Production project Gradle wrapper is missing: " + wrapper);
        }
        List<String> command = new ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(wrapper.toString());
        command.add("--no-configuration-cache");
        command.add("--console=plain");
        command.add("--init-script");
        command.add(initScript.toString());
        command.add(productionTask);
        command.add(PROBE_TASK);
        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(productionProject.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
        builder.environment().put(OUTPUT_ENVIRONMENT, output.toString());
        Process process = builder.start();
        boolean completed;
        try {
            completed = process.waitFor(GRADLE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw failure("gradle_error", "Interrupted while preparing the production classpath", exception);
        }
        if (!completed) {
            process.destroyForcibly();
            throw failure("gradle_error", "Production Gradle preparation timed out after "
                + GRADLE_TIMEOUT.toSeconds() + " seconds. Log: " + log);
        }
        if (process.exitValue() != 0) {
            throw failure("gradle_error", "Production Gradle preparation failed with exit code "
                + process.exitValue() + ". Log: " + log);
        }
    }

    private Path materializeInitScript(Path cache) throws IOException {
        Path script = cache.resolve("preview-classpath.init.gradle");
        byte[] expected = initScriptBytes();
        if (Files.notExists(script) || !java.util.Arrays.equals(expected, Files.readAllBytes(script))) {
            Path candidate = cache.resolve("preview-classpath.init.gradle.candidate");
            Files.write(candidate, expected);
            Files.move(candidate, script, StandardCopyOption.REPLACE_EXISTING);
        }
        return script;
    }

    private static byte[] initScriptBytes() throws IOException {
        try (InputStream input = ProductionRuntime.class.getResourceAsStream(INIT_SCRIPT_RESOURCE)) {
            if (input == null) throw failure("classpath_error", "Missing packaged Gradle classpath probe");
            return input.readAllBytes();
        }
    }

    private String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, productionProject.toString());
            update(digest, productionTask);
            digest.update(initScriptBytes());
            for (Path input : fingerprintFiles()) {
                update(digest, productionProject.relativize(input).toString().replace('\\', '/'));
                digest.update(Files.readAllBytes(input));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw failure("classpath_error", "Could not fingerprint production inputs in " + productionProject,
                exception);
        }
    }

    private List<Path> fingerprintFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(productionProject)) {
            return paths.filter(Files::isRegularFile)
                .filter(this::isProductionInput)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private List<Path> productionInputs() {
        return Stream.of(
            productionProject.resolve("src/main"),
            productionProject.resolve("buildSrc"),
            productionProject.resolve("gradle"),
            productionProject.resolve("build.gradle"),
            productionProject.resolve("build.gradle.kts"),
            productionProject.resolve("settings.gradle"),
            productionProject.resolve("settings.gradle.kts"),
            productionProject.resolve("gradle.properties"))
            .filter(Files::exists)
            .toList();
    }

    private boolean isProductionInput(Path path) {
        Path relative = productionProject.relativize(path);
        if (relative.startsWith("src/main") || relative.startsWith("buildSrc") || relative.startsWith("gradle")) {
            return true;
        }
        if (relative.getNameCount() != 1) return false;
        return switch (relative.getFileName().toString()) {
            case "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradle.properties" ->
                true;
            default -> false;
        };
    }

    private static List<Path> validateEntries(List<String> lines, Path base) {
        List<Path> entries = lines.stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path : base.resolve(path))
            .map(path -> path.toAbsolutePath().normalize())
            .distinct()
            .toList();
        for (Path entry : entries) {
            if (Files.notExists(entry)) throw failure("missing_output", "Production classpath entry is missing: " + entry);
        }
        return entries;
    }

    private static boolean isModularUiRuntime(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("modularui-") || name.startsWith("modularui2-");
    }

    private static Path resolve(Path root, String configured) {
        Path path = Path.of(configured.trim());
        return (path.isAbsolute() ? path : root.resolve(path)).toAbsolutePath().normalize();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static IllegalArgumentException failure(String category, String message) {
        return new IllegalArgumentException("[" + category + "] " + message);
    }

    private static IllegalArgumentException failure(String category, String message, Throwable cause) {
        return new IllegalArgumentException("[" + category + "] " + message, cause);
    }
}
