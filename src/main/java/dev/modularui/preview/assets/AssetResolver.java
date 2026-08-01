package dev.modularui.preview.assets;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.minecraft.util.ResourceLocation;

public final class AssetResolver {

    private static final String ASSET_PREFIX = "assets/";
    private static final int TRANSLATION_PATH_DEPTH = 3;

    private final List<Path> sources;

    public AssetResolver(List<Path> sources) {
        this.sources = List.copyOf(sources);
    }

    public Optional<ResolvedAsset> find(ResourceLocation location) {
        String entryName = "assets/" + location.getResourceDomain() + '/' + location.getResourcePath();
        for (Path source : sources) {
            Optional<ResolvedAsset> resolved = Files.isDirectory(source)
                ? findInDirectory(source, entryName)
                : findInJar(source, entryName);
            if (resolved.isPresent()) return resolved;
        }
        return Optional.empty();
    }

    public Translations translations(String locale) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> resolvedSources = new ArrayList<>();
        for (Path source : sources) {
            if (Files.isDirectory(source)) {
                loadTranslationsFromDirectory(source, locale, values, resolvedSources);
            } else if (Files.isRegularFile(source)) {
                loadTranslationsFromJar(source, locale, values, resolvedSources);
            }
        }
        return new Translations(values, resolvedSources);
    }

    private void loadTranslationsFromDirectory(Path source, String locale, Map<String, String> values,
        List<String> resolvedSources) {
        Path root = source.toAbsolutePath()
            .normalize();
        Path assets = "assets".equals(root.getFileName().toString()) ? root : root.resolve(ASSET_PREFIX);
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> paths = Files.walk(assets, TRANSLATION_PATH_DEPTH)) {
            for (Path languageFile : paths.filter(Files::isRegularFile)
                .filter(path -> isTranslationPath(path, locale))
                .sorted(Comparator.comparing(Path::toString))
                .toList()) {
                try (InputStream input = Files.newInputStream(languageFile)) {
                    loadTranslations(input, values);
                    resolvedSources.add(languageFile.toString());
                }
            }
        } catch (IOException exception) {
            throw unreadableAsset(assets.toString(), exception);
        }
    }

    private void loadTranslationsFromJar(Path source, String locale, Map<String, String> values,
        List<String> resolvedSources) {
        try (JarFile jar = new JarFile(source.toFile())) {
            List<JarEntry> entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> isTranslationEntry(entry.getName(), locale))
                .sorted(Comparator.comparing(JarEntry::getName))
                .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    loadTranslations(input, values);
                    resolvedSources.add(source + "!/" + entry.getName());
                }
            }
        } catch (IOException exception) {
            throw unreadableAsset(source.toString(), exception);
        }
    }

    private boolean isTranslationPath(Path path, String locale) {
        Path parent = path.getParent();
        return parent != null
            && "lang".equalsIgnoreCase(parent.getFileName().toString())
            && (locale + ".lang").equalsIgnoreCase(path.getFileName().toString());
    }

    private boolean isTranslationEntry(String name, String locale) {
        String normalized = name.replace('\\', '/');
        return normalized.startsWith(ASSET_PREFIX)
            && normalized.toLowerCase(java.util.Locale.ROOT).endsWith(
                "/lang/" + locale.toLowerCase(java.util.Locale.ROOT) + ".lang");
    }

    private void loadTranslations(InputStream input, Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        properties.forEach((key, value) -> values.putIfAbsent(key.toString(), value.toString()));
    }

    private Optional<ResolvedAsset> findInDirectory(Path source, String entryName) {
        Path root = source.toAbsolutePath()
            .normalize();
        String relativeName = "assets".equals(root.getFileName()
            .toString()) ? entryName.substring(ASSET_PREFIX.length()) : entryName;
        Path asset = root.resolve(relativeName)
            .normalize();
        if (!asset.startsWith(root) || !Files.isRegularFile(asset)) return Optional.empty();
        try {
            return Optional.of(new ResolvedAsset(asset.toString(), Files.readAllBytes(asset)));
        } catch (IOException exception) {
            throw unreadableAsset(asset.toString(), exception);
        }
    }

    private Optional<ResolvedAsset> findInJar(Path source, String entryName) {
        if (!Files.isRegularFile(source)) return Optional.empty();
        try (JarFile jar = new JarFile(source.toFile())) {
            JarEntry entry = jar.getJarEntry(entryName);
            if (entry == null || entry.isDirectory()) return Optional.empty();
            try (InputStream input = jar.getInputStream(entry)) {
                return Optional.of(new ResolvedAsset(source + "!/" + entryName, input.readAllBytes()));
            }
        } catch (IOException exception) {
            throw unreadableAsset(source.toString(), exception);
        }
    }

    private IllegalStateException unreadableAsset(String source, IOException cause) {
        return new IllegalStateException("Could not read preview asset from " + source, cause);
    }

    public record ResolvedAsset(String source, byte[] bytes) {

        public ResolvedAsset {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    public record Translations(Map<String, String> values, List<String> sources) {

        public Translations {
            values = Map.copyOf(values);
            sources = List.copyOf(sources);
        }
    }
}
