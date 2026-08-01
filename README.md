# ModularUI2 Preview

Render production-shaped ModularUI2 Java code without starting Minecraft. The tool produces a full-screen PNG and versioned widget bounds that a developer or coding agent can inspect immediately.

The Java source under `src/preview/java` compiles against the real ModularUI2 development artifact. The headless runtime supplies a deliberately small compatible rendering surface. There is no preview-only layout language and no source transformation.

## Requirements

- Windows, Linux, or macOS
- JDK 21 available as `JAVA_HOME` or on `PATH`
- Internet access on the first run so Gradle and the pinned ModularUI2 artifact can be downloaded

Gradle does not need to be installed. The repository includes the Gradle Wrapper.

## First preview

On Windows:

```bat
preview.bat example.ExampleScreen
```

On Linux or macOS:

```sh
./preview.sh example.ExampleScreen
```

The result is written to:

```text
output/ExampleScreen/preview.png
output/ExampleScreen/bounds.json
```

The first run downloads build dependencies. Warm previews normally complete in about one second.

## Write a screen

Create a normal Java class under `src/preview/java`. Its directory must match its Java package.

```java
package example;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

public final class MyScreen implements IGuiHolder<GuiData> {

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        return ModularPanel.defaultPanel("my-screen", 240, 140)
            .child(IKey.str("Production-shaped UI").asWidget().pos(12, 10));
    }
}
```

Render it:

```bat
preview.bat example.MyScreen
```

The requested class currently needs a no-argument constructor. The layout code, imports, widget types, positions, and sizes are normal ModularUI2 code. To move the screen into a mod, copy the Java source to that mod's `src/main/java`, adjust only its package and application-specific data wiring, and keep the UI construction unchanged.

## Screen configuration

Edit `preview.properties`:

```properties
screen.width=1920
screen.height=1080
gui.scale=auto
screen.background=#101820
```

`gui.scale` accepts `auto`, `1`, `2`, `3`, or `4`. `auto` follows Minecraft 1.7.10 `ScaledResolution` rules. A large panel can therefore extend beyond the framebuffer at high automatic scales, exactly as it would in the game. Use an explicit scale such as `2` when you want to inspect the whole panel at a predictable size.

`screen.background` accepts `#RRGGBB` or `#AARRGGBB`.

## Output artifacts

`preview.png` always has the configured physical framebuffer dimensions. ModularUI2 lays out the panel in logical GUI coordinates; the renderer centers it and applies the effective Minecraft GUI scale using nearest-neighbor pixels.

`bounds.json` contains:

- schema version, preview class, and fidelity status;
- physical and logical screen dimensions;
- effective GUI scale;
- panel bounds in local, logical, and physical screen coordinates;
- a stable tree path for every widget;
- widget bounds in all three coordinate spaces;
- `visible` and `clipped` flags;
- explicit warnings for unsupported or partially rendered behavior.

Rectangles use `x`, `y`, `width`, and `height` with half-open pixel bounds. Physical screen bounds are derived from logical bounds and the effective GUI scale.

## Custom output directory

```bat
preview.bat example.MyScreen output/my-iteration
```

```sh
./preview.sh example.MyScreen output/my-iteration
```

## Supported surface

The first version supports the subset proven by the included contract fixtures and Galaxia's `TeamPermissionScreen`:

- `ModularPanel`, `ParentWidget`, `TextWidget`, `ButtonWidget`, and `ScrollWidget`;
- fixed, relative, right, and bottom positioning used by those fixtures;
- scroll clipping;
- production `background` and `overlay` drawables that use the supported headless Minecraft drawing surface;
- Minecraft 1.7.10 ASCII font metrics, pixels, and text shadow.

Unsupported custom widgets and failing drawables produce warnings. A warning means the output must not be treated as a fidelity-complete preview.

## Troubleshooting

### Preview class was not found

Pass the fully qualified class name, including its package, and place the source below `src/preview/java` in the matching directory.

### Preview class needs a no-argument constructor

Add a no-argument constructor or create a small preview entry class that constructs the production-shaped panel with representative data.

### A production import does not compile

The standalone workspace includes ModularUI2 but not every dependency from every mod. Add the required artifact to the `preview` dependencies in `build.gradle.kts`, or keep application-specific wiring in a small preview entry class.

### The panel is clipped with `gui.scale=auto`

This is the same scaling result Minecraft would calculate for that framebuffer. Set `gui.scale=2` or another explicit value to inspect the whole design.

## Verification

Run the contract tests:

```bat
gradlew.bat test
```

```sh
./gradlew test
```
