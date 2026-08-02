# Streamlined Preview Workflow

## Goal

Make ModularUI2 Preview immediately usable by a developer or coding agent without
manually assembling project boilerplate, repeatedly restarting commands, or
building a distribution from source for every installation.

The change adds three capabilities:

1. project scaffolding;
2. safe file watching with transactional preview replacement;
3. portable release archives produced by GitHub Actions.

The previewer remains a local ModularUI2 shim. It does not simulate a server,
Forge lifecycle, machine logic, networking, or JVM class hotswap.

## Command-Line Interface

The launch scripts expose one command model on every platform:

```text
preview.bat init <project-directory>
preview.bat render <project-directory> [options]
preview.bat open <project-directory> [options]
preview.bat watch <project-directory> [options]
preview.bat help
```

Linux and macOS use the same arguments through `./preview.sh`.

`render` performs one build, writes the configured PNG and bounds JSON, and
exits. `open` performs one build and opens an interactive window. `watch` opens
an interactive window and rebuilds it after relevant files change.

Existing configuration inputs remain available as named options rather than
ambiguous positional arguments:

```text
--class <fully-qualified-class>
--output <directory>
--config <properties-file>
--actions <actions-file>
```

`help` and invalid invocations print the supported commands, options, and one
working example. Invalid invocations return a non-zero exit code.

## Project Scaffolding

`init` creates a runnable starter project containing:

```text
<project-directory>/
  preview.properties
  src/preview/java/example/StarterPanelPreview.java
  src/preview/resources/assets/
```

The generated project must render without further edits. The command refuses to
write into an existing non-empty directory and does not provide an overwrite
mode. Its final output shows the exact `render` and `watch` commands for the new
project.

The starter files are packaged as tool resources so `init` works from a cloned
repository and from a release archive without depending on the repository's
`examples` directory.

## Watch Mode

Watch mode observes:

- `src/preview/java`;
- `src/preview/resources`;
- `preview.properties` or the selected configuration file;
- `runtime-classpath.txt` when present;
- local JAR and extension inputs already discovered by the project loader.

A small polling snapshot is preferred over platform-specific filesystem event
semantics. Preview projects are small, and polling reliably detects editor
atomic-save and rename patterns on Windows, Linux, and macOS. The watcher checks
for changes at a short interval and waits until the input set has remained stable
for 300 milliseconds before rebuilding.

Only one rebuild runs at a time. Changes arriving during a rebuild are coalesced
into one additional rebuild after the current attempt completes.

Each attempt is transactional:

1. discover and compile inputs into a fresh generation directory;
2. create a fresh project runtime and UI session;
3. perform an initial render off-screen;
4. atomically replace the displayed session only after all steps succeed;
5. close the superseded runtime and remove obsolete generation files.

Compilation errors, missing assets, entrypoint failures, and render exceptions do
not replace the last successful session. The interactive window keeps the last
successful layout visible and marks it stale in a status area outside the
simulated monitor. The status shows the most useful error summary, including a
source file and line when available. The next input change retries automatically.

When no successful session exists yet, the window shows the error status without
a preview. A successful reload clears the stale status. Widget state resets on a
successful reload because the replacement is a new UI session.

The status chrome is never included in `preview.png`. Every successful watch
rebuild updates the normal PNG and bounds JSON artifacts, allowing agents to
consume the latest successful result while a developer uses the window.

Uncatchable failures of the JVM or native process are outside the initial watch
mode contract. Process supervision and child-JVM rendering are deliberately
deferred unless real crashes demonstrate that isolation is needed.

## Portable Distribution

The distribution must not contain an absolute Java executable path captured on
the build machine. Launchers resolve a JDK at runtime in this order:

1. `JAVA_HOME`;
2. `java` and `javac` available on `PATH`.

The launcher verifies JDK 21 and reports a direct installation/configuration
message when Java is missing, too old, or lacks the compiler required for preview
sources.

Release archives contain the built runtime libraries, Windows and Unix launch
scripts, the project template, examples, README, license, third-party notices,
and referenced license texts. The ZIP targets Windows and general extraction;
the `tar.gz` archive preserves Unix executable permissions.

## Continuous Integration and Releases

GitHub Actions runs `agentVerify` on Windows and Linux for pushes and pull
requests. A tag matching `v*` builds the distribution once, packages ZIP and
`tar.gz` archives, and publishes both as assets of the corresponding GitHub
Release.

Repository visibility remains unchanged by this work. Making the repository
public is a separate explicitly approved action after a release archive has been
downloaded and smoke-tested.

## Verification

Stable automated contracts cover:

- parsing valid and invalid CLI commands;
- creating a runnable scaffold and refusing a non-empty target;
- detecting source, resource, configuration, classpath, and library changes;
- coalescing changes while a rebuild is active;
- retaining the last successful session after compilation or rendering failure;
- replacing and closing sessions after a successful rebuild;
- keeping status chrome out of generated artifacts;
- resolving a local JDK without build-machine paths;
- assembling archives with all required runtime, example, documentation, and
  licensing files.

The final verification runs `agentVerify`, exercises `init`, renders the generated
project, performs a watch-mode failure/recovery cycle, and smoke-tests the
packaged launcher from an extracted archive.
