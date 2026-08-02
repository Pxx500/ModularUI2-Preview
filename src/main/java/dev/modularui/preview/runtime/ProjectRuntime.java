package dev.modularui.preview.runtime;

import dev.modularui.preview.Bounds;
import dev.modularui.preview.PreviewEntrypoint;
import dev.modularui.preview.PreviewDrawContext;
import dev.modularui.preview.PreviewResult;
import dev.modularui.preview.PreviewScreen;
import dev.modularui.preview.PreviewSession;
import dev.modularui.preview.MouseButton;
import dev.modularui.preview.ScreenLayout;
import dev.modularui.preview.ScrollDirection;
import dev.modularui.preview.WidgetBounds;
import dev.modularui.preview.assets.AssetResolver;
import dev.modularui.preview.project.PreviewProject;
import java.io.IOException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.StatCollector;

public final class ProjectRuntime implements AutoCloseable {

    private final URLClassLoader classLoader;

    private ProjectRuntime(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    static ProjectRuntime open(List<Path> artifacts) {
        URL[] urls = artifacts.stream()
            .map(ProjectRuntime::toUrl)
            .toArray(URL[]::new);
        return new ProjectRuntime(new IsolatedClassLoader(urls, ProjectRuntime.class.getClassLoader()));
    }

    Class<?> loadClass(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }

    public static PreviewSession openSession(PreviewProject project, String entrypointName, PreviewScreen previewScreen) {
        ProjectRuntime runtime = open(project.runtimeArtifacts());
        try {
            return runtime.createSession(project, entrypointName, previewScreen);
        } catch (RuntimeException | LinkageError exception) {
            try {
                runtime.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
    }

    private PreviewSession createSession(PreviewProject project, String entrypointName, PreviewScreen previewScreen) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            Class<?> entrypointClass = loadClass(entrypointName);
            if (!PreviewEntrypoint.class.isAssignableFrom(entrypointClass)) {
                throw new IllegalArgumentException(
                    "Preview entrypoint must implement " + PreviewEntrypoint.class.getName() + ": " + entrypointName);
            }
            PreviewEntrypoint entrypoint = (PreviewEntrypoint) instantiate(entrypointClass);
            Class<?> previewedClass = entrypoint.previewedClass();
            if (previewedClass == null) {
                throw new IllegalArgumentException("Preview entrypoint returned a null previewed class: " + entrypointName);
            }
            AssetResolver assets = createAssetResolver(project);
            AssetResolver.Translations translations = assets.translations("en_US");
            StatCollector.installTranslations(translations.values());
            Class<?> modularSyncManagerClass = loadClass("com.cleanroommc.modularui.value.sync.ModularSyncManager");
            Class<?> panelSyncManagerClass = loadClass("com.cleanroommc.modularui.value.sync.PanelSyncManager");
            Object modularSyncManager = instantiateSyncManager(modularSyncManagerClass, true);
            Object panelSyncManager = instantiatePanelSyncManager(
                panelSyncManagerClass,
                modularSyncManagerClass,
                modularSyncManager);
            Object panel = entrypoint.createPanel(new PreviewEntrypoint.Context(panelSyncManager));
            Class<?> panelClass = loadClass("com.cleanroommc.modularui.screen.ModularPanel");
            if (panel == null || !panelClass.isInstance(panel)) {
                throw new IllegalArgumentException("Preview entrypoint did not return a real ModularPanel: " + entrypointName);
            }

            Class<?> screenClass = loadClass("com.cleanroommc.modularui.screen.ModularScreen");
            Object screen = instantiateScreen(screenClass, panelClass, entrypoint.owner(), panel);
            Object settings = initialiseScreenSettings(screenClass, screen);
            String panelName = (String) invoke(panelClass, panel, "getName", new Class<?>[0]);
            initialisePanelSync(panelSyncManagerClass, panelSyncManager, panelName, panel);
            Object container = constructContainer(modularSyncManagerClass, modularSyncManager, settings, panelName);
            constructScreen(screenClass, screen, container);
            ScreenLayout screenDimensions = previewScreen.layout(1, 1);
            invoke(screenClass, screen, "onResize", new Class<?>[] { int.class, int.class },
                screenDimensions.logicalWidth(), screenDimensions.logicalHeight());

            Object area = invoke(panelClass, panel, "getArea", new Class<?>[0]);
            Bounds bounds = new Bounds(
                intValue(area, "x"),
                intValue(area, "y"),
                intValue(area, "width"),
                intValue(area, "height"));
            Path codeSource = codeSource(panelClass);
            ScreenLayout layout = previewScreen.layout(bounds.width(), bounds.height());
            List<WidgetBounds> widgets = captureWidgets(panel, bounds, layout);
            Object context = invoke(screenClass, screen, "getContext", new Class<?>[0]);
            Class<?> contextClass = loadClass("com.cleanroommc.modularui.screen.viewport.GuiContext");
            Class<?> scrollDirectionClass = loadClass("com.cleanroommc.modularui.api.UpOrDown");
            Class<?> panelManagerClass = loadClass("com.cleanroommc.modularui.screen.PanelManager");
            Object panelManager = invoke(screenClass, screen, "getPanelManager", new Class<?>[0]);
            AutoCloseable lifecycle = () -> withContextClassLoader(classLoader, () -> {
                invoke(panelManagerClass, panelManager, "closeAll", new Class<?>[0]);
                invoke(panelManagerClass, panelManager, "dispose", new Class<?>[0]);
            });
            return new PreviewSession(
                this,
                lifecycle,
                entrypointClass.getName(),
                codeSource(entrypointClass),
                previewedClass.getName(),
                codeSource(previewedClass),
                panelName,
                panelClass.getName(),
                bounds,
                codeSource,
                widgets,
                new PreviewSession.Interaction() {

                    @Override
                    public void moveMouse(int screenX, int screenY) {
                        withContextClassLoader(classLoader, () -> {
                            invoke(contextClass, context, "updateState",
                                new Class<?>[] { int.class, int.class, float.class },
                                layout.toLogicalX(screenX), layout.toLogicalY(screenY), 0F);
                            invoke(screenClass, screen, "onFrameUpdate", new Class<?>[0]);
                        });
                    }

                    @Override
                    public boolean press(MouseButton button) {
                        return dispatchMouse(screenClass, screen, button.modularUiCode(), true);
                    }

                    @Override
                    public boolean release(MouseButton button) {
                        return dispatchMouse(screenClass, screen, button.modularUiCode(), false);
                    }

                    @Override
                    public boolean scroll(ScrollDirection direction, int amount) {
                        return dispatchScroll(screenClass, screen, scrollDirectionClass, direction, amount);
                    }
                },
                () -> render(screenClass, screen, panel, bounds, previewScreen, layout, assets, translations));
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load preview runtime class", exception);
        } finally {
            StatCollector.clearTranslations();
            thread.setContextClassLoader(previous);
        }
    }

    private PreviewResult render(Class<?> screenClass, Object screen, Object panel, Bounds panelBounds,
        PreviewScreen previewScreen, ScreenLayout layout, AssetResolver assets,
        AssetResolver.Translations translations) {
        BufferedImage logicalImage = new BufferedImage(
            layout.logicalWidth(),
            layout.logicalHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = logicalImage.createGraphics();
        graphics.setColor(new Color(previewScreen.backgroundColor(), true));
        graphics.fillRect(0, 0, logicalImage.getWidth(), logicalImage.getHeight());
        List<String> renderedAssets = new ArrayList<>(translations.sources());
        try {
            StatCollector.installTranslations(translations.values());
            withContextClassLoader(classLoader,
                () -> renderedAssets.addAll(
                    PreviewDrawContext.run(
                        graphics,
                        assets,
                        () -> invoke(screenClass, screen, "drawScreen", new Class<?>[0]))));
        } finally {
            StatCollector.clearTranslations();
            graphics.dispose();
        }
        List<WidgetBounds> widgets = captureWidgets(panel, panelBounds, layout);
        return new PreviewResult(layout.toFramebuffer(logicalImage), layout, widgets, List.of(), renderedAssets);
    }

    private boolean dispatchMouse(Class<?> screenClass, Object screen, int button, boolean pressed) {
        final boolean[] handled = new boolean[1];
        withContextClassLoader(classLoader, () -> {
            boolean handledPre = (Boolean) invoke(screenClass, screen, "onMouseInputPre",
                new Class<?>[] { int.class, boolean.class }, button, pressed);
            if (!handledPre) {
                String method = pressed ? "onMousePressed" : "onMouseRelease";
                handled[0] = (Boolean) invoke(screenClass, screen, method, new Class<?>[] { int.class }, button);
            } else {
                handled[0] = true;
            }
        });
        return handled[0];
    }

    private boolean dispatchScroll(Class<?> screenClass, Object screen, Class<?> directionClass,
        ScrollDirection direction, int amount) {
        final boolean[] handled = new boolean[1];
        withContextClassLoader(classLoader, () -> {
            Object runtimeDirection;
            try {
                runtimeDirection = directionClass.getField(direction.name())
                    .get(null);
            } catch (ReflectiveOperationException exception) {
                throw reflectionFailure("Could not map the mouse scroll direction", exception);
            }
            handled[0] = (Boolean) invoke(screenClass, screen, "onMouseScroll",
                new Class<?>[] { directionClass, int.class }, runtimeDirection, amount);
        });
        return handled[0];
    }

    private AssetResolver createAssetResolver(PreviewProject project) {
        List<Path> assetSources = new ArrayList<>(project.assetSources());
        assetSources.addAll(project.runtimeArtifacts());
        return new AssetResolver(assetSources);
    }

    private List<WidgetBounds> captureWidgets(Object panel, Bounds panelBounds, ScreenLayout screen) {
        try {
            Class<?> widgetClass = loadClass("com.cleanroommc.modularui.api.widget.IWidget");
            List<WidgetBounds> widgets = new ArrayList<>();
            captureWidget(widgetClass, panel, "0", panelBounds, screen, widgets);
            return List.copyOf(widgets);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Could not load the ModularUI2 widget contract", exception);
        }
    }

    private void captureWidget(Class<?> widgetClass, Object widget, String path, Bounds panelBounds, ScreenLayout screen,
        List<WidgetBounds> widgets) {
        Object area = invoke(widgetClass, widget, "getArea", new Class<?>[0]);
        Bounds logical = new Bounds(
            intValue(area, "x"),
            intValue(area, "y"),
            intValue(area, "width"),
            intValue(area, "height"));
        Bounds local = logical.translate(-panelBounds.x(), -panelBounds.y());
        Bounds logicalScreen = new Bounds(0, 0, screen.logicalWidth(), screen.logicalHeight());
        widgets.add(new WidgetBounds(
            path,
            widget.getClass().getSimpleName(),
            local,
            logical,
            logical.scale(screen.guiScale()),
            !logical.intersection(logicalScreen).isEmpty(),
            !logicalScreen.contains(logical)));

        List<?> children = (List<?>) invoke(widgetClass, widget, "getChildren", new Class<?>[0]);
        for (int index = 0; index < children.size(); index++) {
            captureWidget(widgetClass, children.get(index), path + "/" + index, panelBounds, screen, widgets);
        }
    }

    private Object instantiateScreen(Class<?> screenClass, Class<?> panelClass, String owner, Object panel) {
        try {
            return screenClass.getConstructor(String.class, panelClass)
                .newInstance(owner, panel);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not create the ModularUI2 screen", exception);
        }
    }

    private Object instantiateSyncManager(Class<?> syncManagerClass, boolean client) {
        try {
            return syncManagerClass.getConstructor(boolean.class)
                .newInstance(client);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not create the ModularUI2 sync manager", exception);
        }
    }

    private Object instantiatePanelSyncManager(Class<?> panelSyncManagerClass, Class<?> syncManagerClass,
        Object syncManager) {
        try {
            return panelSyncManagerClass.getConstructor(syncManagerClass, boolean.class)
                .newInstance(syncManager, true);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not create the ModularUI2 panel sync manager", exception);
        }
    }

    private void initialisePanelSync(Class<?> panelSyncManagerClass, Object panelSyncManager, String panelName,
        Object panel) throws ClassNotFoundException {
        Class<?> widgetTreeClass = loadClass("com.cleanroommc.modularui.widget.WidgetTree");
        Class<?> widgetClass = loadClass("com.cleanroommc.modularui.api.widget.IWidget");
        invoke(
            widgetTreeClass,
            null,
            "collectSyncValues",
            new Class<?>[] { panelSyncManagerClass, String.class, widgetClass, boolean.class },
            panelSyncManager,
            panelName,
            panel,
            true);
    }

    private Object constructContainer(Class<?> modularSyncManagerClass, Object modularSyncManager, Object settings,
        String panelName) throws ClassNotFoundException {
        Class<?> containerClass = loadClass("com.cleanroommc.modularui.screen.ModularContainer");
        Class<?> playerClass = loadClass("net.minecraft.entity.player.EntityPlayer");
        Class<?> settingsClass = loadClass("com.cleanroommc.modularui.screen.UISettings");
        Class<?> guiDataClass = loadClass("com.cleanroommc.modularui.factory.GuiData");
        Object container = instantiate(containerClass);
        Object player = instantiate(playerClass);
        invoke(
            containerClass,
            container,
            "construct",
            new Class<?>[] { playerClass, modularSyncManagerClass, settingsClass, String.class, guiDataClass },
            player,
            modularSyncManager,
            settings,
            panelName,
            null);
        return container;
    }

    private void constructScreen(Class<?> screenClass, Object screen, Object container) throws ClassNotFoundException {
        Class<?> containerClass = loadClass("com.cleanroommc.modularui.screen.ModularContainer");
        Class<?> wrapperClass = loadClass("com.cleanroommc.modularui.screen.GuiContainerWrapper");
        try {
            wrapperClass.getConstructor(containerClass, screenClass)
                .newInstance(container, screen);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not create the ModularUI2 container screen wrapper", exception);
        }
    }

    private Object initialiseScreenSettings(Class<?> screenClass, Object screen) throws ClassNotFoundException {
        Class<?> recipeSettingsClass = loadClass("com.cleanroommc.modularui.api.RecipeViewerSettings");
        Class<?> recipeSettingsImplClass = loadClass("com.cleanroommc.modularui.screen.RecipeViewerSettingsImpl");
        Class<?> uiSettingsClass = loadClass("com.cleanroommc.modularui.screen.UISettings");
        Class<?> contextClass = loadClass("com.cleanroommc.modularui.screen.viewport.ModularGuiContext");
        try {
            Object recipeSettings = recipeSettingsImplClass.getConstructor()
                .newInstance();
            Object settings = uiSettingsClass.getConstructor(recipeSettingsClass)
                .newInstance(recipeSettings);
            Object context = invoke(screenClass, screen, "getContext", new Class<?>[0]);
            invoke(contextClass, context, "setSettings", new Class<?>[] { uiSettingsClass }, settings);
            return settings;
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not initialise ModularUI2 screen settings", exception);
        }
    }

    private static Object instantiate(Class<?> type) {
        try {
            return type.getConstructor()
                .newInstance();
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not create preview entrypoint " + type.getName(), exception);
        }
    }

    private static Object invoke(Class<?> owner, Object receiver, String name, Class<?>[] parameterTypes,
        Object... arguments) {
        try {
            return owner.getMethod(name, parameterTypes)
                .invoke(receiver, arguments);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not invoke " + owner.getName() + "." + name, exception);
        }
    }

    private static int intValue(Object receiver, String methodName) {
        try {
            return receiver.getClass()
                .getField(methodName)
                .getInt(receiver);
        } catch (ReflectiveOperationException exception) {
            throw reflectionFailure("Could not read " + receiver.getClass().getName() + "." + methodName, exception);
        }
    }

    private static Path codeSource(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .toAbsolutePath()
                .normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not determine code source for " + type.getName(), exception);
        }
    }

    private static RuntimeException reflectionFailure(String message, ReflectiveOperationException exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
            ? invocation.getCause()
            : exception;
        return new IllegalStateException(message, cause);
    }

    private static void withContextClassLoader(ClassLoader classLoader, Runnable action) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(classLoader);
            action.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Override
    public void close() throws IOException {
        classLoader.close();
    }

    private static URL toUrl(Path path) {
        try {
            return path.toUri()
                .toURL();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid runtime artifact path: " + path, exception);
        }
    }

    private static final class IsolatedClassLoader extends URLClassLoader {

        private static final String MODULAR_UI_PACKAGE = "com.cleanroommc.modularui.";
        private static final List<String> PARENT_OWNED_PACKAGES = List.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "dev.modularui.preview.",
            "net.minecraft.",
            "net.minecraftforge.",
            "cpw.mods.",
            "org.lwjgl.",
            "org.apache.logging.log4j.",
            "com.cleanroommc.modularui.core.mixins.early.minecraft.");

        private IsolatedClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = loadUncachedClass(name);
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }

        private Class<?> loadUncachedClass(String name) throws ClassNotFoundException {
            if (isParentOwned(name)) return super.loadClass(name, false);
            if (name.startsWith(MODULAR_UI_PACKAGE)) return findClass(name);
            try {
                return findClass(name);
            } catch (ClassNotFoundException exception) {
                return super.loadClass(name, false);
            }
        }

        private boolean isParentOwned(String name) {
            return PARENT_OWNED_PACKAGES.stream()
                .anyMatch(name::startsWith);
        }
    }
}
