package dev.modularui.preview.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.util.stream.Stream;

public final class PreviewProject {

    private static final String TEMPLATE_ROOT = "/dev/modularui/preview/template/";
    private static final List<String> BUNDLED_RUNTIME_ARTIFACTS = List.of(
        "ModularUI2-2.3.84-1.7.10-dev.jar",
        "ModularUI-1.3.4-dev.jar");

    private final Path root;
    private final Path compiledOutput;
    private final List<Path> assetSources;
    private final List<Path> bundledRuntime;
    private final List<Path> productionRuntime;
    private final List<Path> libraries;
    private final List<Path> extensions;
    private final Map<String, String> properties;

    private PreviewProject(Path root, Path compiledOutput, List<Path> assetSources, List<Path> bundledRuntime,
        List<Path> productionRuntime, List<Path> libraries, List<Path> extensions, Map<String, String> properties) {
        this.root = root;
        this.compiledOutput = compiledOutput;
        this.assetSources = assetSources;
        this.bundledRuntime = bundledRuntime;
        this.productionRuntime = productionRuntime;
        this.libraries = libraries;
        this.extensions = extensions;
        this.properties = properties;
    }

    public static void initialize(Path projectRoot) {
        ensureEmpty(projectRoot);
        try {
            Files.createDirectories(projectRoot.resolve("src/preview/resources/assets"));
            copyTemplate("preview.properties", projectRoot.resolve("preview.properties"));
            copyTemplate(
                "StarterPanelPreview.java",
                projectRoot.resolve("src/preview/java/example/StarterPanelPreview.java"));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create preview project at " + projectRoot, exception);
        }
    }

    private static void ensureEmpty(Path projectRoot) {
        if (!Files.exists(projectRoot)) return;
        if (!Files.isDirectory(projectRoot)) {
            throw new IllegalArgumentException("Preview project target is not a directory: " + projectRoot);
        }
        try (var files = Files.list(projectRoot)) {
            if (files.findAny().isPresent()) {
                throw new IllegalArgumentException("Preview project target is not empty: " + projectRoot);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview project target: " + projectRoot, exception);
        }
    }

    private static void copyTemplate(String resourceName, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream source = PreviewProject.class.getResourceAsStream(TEMPLATE_ROOT + resourceName)) {
            if (source == null) throw new IOException("Missing packaged preview template: " + resourceName);
            Files.copy(source, target);
        }
    }

    public static PreviewProject open(Path root) {
        Path normalizedRoot = root.toAbsolutePath()
            .normalize();
        return open(normalizedRoot, normalizedRoot.resolve("build/classes/java/preview"));
    }

    public static PreviewProject open(Path root, Path compiledOutput) {
        Path normalizedRoot = root.toAbsolutePath()
            .normalize();
        List<Path> assetSources = Stream.of(
            normalizedRoot.resolve("src/preview/resources"),
            normalizedRoot.resolve("assets"))
            .filter(Files::isDirectory)
            .toList();
        return new PreviewProject(
            normalizedRoot,
            compiledOutput.toAbsolutePath()
                .normalize(),
            assetSources,
            locateBundledRuntime(),
            loadRuntimeClasspath(normalizedRoot, normalizedRoot.resolve("runtime-classpath.txt")),
            discoverJars(normalizedRoot.resolve("libs")),
            discoverJars(normalizedRoot.resolve("extensions")),
            loadProperties(normalizedRoot.resolve("preview.properties")));
    }

    public Path root() {
        return root;
    }

    public Path previewSources() {
        return root.resolve("src/preview/java");
    }

    public void compileSources() {
        if (!Files.isDirectory(previewSources())) return;
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(previewSources())) {
            sources = paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview sources: " + previewSources(), exception);
        }
        if (sources.isEmpty()) return;

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Preview sources require a JDK, but no Java compiler is available");
        }
        Path output = compiledOutput;
        try {
            Files.createDirectories(output);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not create preview class output: " + output, exception);
        }

        String projectClasspath = Stream.of(
            bundledRuntime.stream(),
            productionRuntime.stream(),
            libraries.stream(),
            extensions.stream())
            .flatMap(stream -> stream)
            .map(Path::toString)
            .collect(java.util.stream.Collectors.joining(File.pathSeparator));
        String classpath = Stream.of(projectClasspath, System.getProperty("java.class.path"))
            .filter(value -> value != null && !value.isBlank())
            .collect(java.util.stream.Collectors.joining(File.pathSeparator));
        List<String> arguments = new ArrayList<>();
        arguments.add("-encoding");
        arguments.add(StandardCharsets.UTF_8.name());
        arguments.add("-classpath");
        arguments.add(classpath);
        arguments.add("-d");
        arguments.add(output.toString());
        sources.stream().map(Path::toString).forEach(arguments::add);

        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new));
        if (exitCode != 0) {
            throw new IllegalArgumentException(
                "Preview source compilation failed:\n" + diagnostics.toString(StandardCharsets.UTF_8));
        }
    }

    public List<Path> assetSources() {
        return assetSources;
    }

    public List<Path> libraries() {
        return libraries;
    }

    public List<Path> productionRuntime() {
        return productionRuntime;
    }

    public List<Path> extensions() {
        return extensions;
    }

    public List<Path> watchedInputs(Path configuration) {
        return Stream.of(
            Stream.of(
                previewSources(),
                root.resolve("src/preview/resources"),
                root.resolve("assets"),
                root.resolve("libs"),
                root.resolve("extensions"),
                root.resolve("runtime-classpath.txt"),
                configuration),
            productionRuntime.stream())
            .flatMap(stream -> stream)
            .map(path -> path.toAbsolutePath()
                .normalize())
            .distinct()
            .toList();
    }

    public List<Path> runtimeArtifacts() {
        return Stream.of(
            Stream.of(compiledOutput)
                .filter(Files::isDirectory),
            bundledRuntime.stream(),
            productionRuntime.stream(),
            libraries.stream(),
            extensions.stream())
            .flatMap(stream -> stream)
            .toList();
    }

    private static List<Path> locateBundledRuntime() {
        Map<String, Path> classpath = Stream.of(
            System.getProperty("java.class.path", "").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            .filter(entry -> !entry.isBlank())
            .map(Path::of)
            .filter(Files::isRegularFile)
            .collect(java.util.stream.Collectors.toMap(
                path -> path.getFileName().toString(),
                path -> path.toAbsolutePath().normalize(),
                (first, ignored) -> first));
        return BUNDLED_RUNTIME_ARTIFACTS.stream()
            .map(name -> Optional.ofNullable(classpath.get(name))
                .orElseThrow(() -> new IllegalStateException(
                    "The bundled runtime artifact is missing from the previewer distribution: " + name)))
            .toList();
    }

    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    private static List<Path> discoverJars(Path directory) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName()
                    .toString()
                    .endsWith(".jar"))
                .sorted(Comparator.comparing(path -> path.getFileName()
                    .toString()))
                .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not inspect preview project directory: " + directory, exception);
        }
    }

    private static Map<String, String> loadProperties(Path file) {
        if (!Files.isRegularFile(file)) return Map.of();
        Properties loaded = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            loaded.load(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read preview project configuration: " + file, exception);
        }
        return loaded.stringPropertyNames()
            .stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(name -> name, loaded::getProperty));
    }

    private static List<Path> loadRuntimeClasspath(Path root, Path file) {
        if (!Files.isRegularFile(file)) return List.of();
        try {
            return Files.readAllLines(file)
                .stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(Path::of)
                .map(path -> path.isAbsolute() ? path : root.resolve(path))
                .map(path -> path.toAbsolutePath()
                    .normalize())
                .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read production runtime classpath: " + file, exception);
        }
    }
}
