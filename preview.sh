#!/usr/bin/env sh
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 4 ]; then
    echo "Usage: ./preview.sh project-directory [fully.qualified.PreviewClass] [output-directory] [configuration]" >&2
    echo "       ./preview.sh project-directory --actions actions-file" >&2
    echo "       ./preview.sh project-directory --interactive" >&2
    exit 2
fi

tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preview_project=$(CDPATH= cd -- "$1" && pwd)
preview_dist="$tool_root/build/install/modularui2-preview"
preview_java_file="$preview_dist/bin/java-executable.txt"

if [ ! -f "$preview_java_file" ]; then
    "$tool_root/gradlew" -p "$tool_root" installDist
fi

preview_java=$(head -n 1 "$preview_java_file")
runtime_project=$preview_project
runtime_dist=$preview_dist
if command -v cygpath >/dev/null 2>&1; then
    runtime_project=$(cygpath -w "$preview_project")
    runtime_dist=$(cygpath -w "$preview_dist")
fi

if [ "$#" -eq 1 ]; then
    exec "$preview_java" -Djoml.nounsafe=true -classpath "$runtime_dist/lib/*" dev.modularui.preview.UiPreviewMain "$runtime_project"
fi

preview_class=$2
if [ "$#" -ge 3 ]; then
    if [ "$#" -eq 4 ]; then
        exec "$preview_java" -Djoml.nounsafe=true -classpath "$runtime_dist/lib/*" dev.modularui.preview.UiPreviewMain "$runtime_project" "$preview_class" "$3" "$4"
    fi
    exec "$preview_java" -Djoml.nounsafe=true -classpath "$runtime_dist/lib/*" dev.modularui.preview.UiPreviewMain "$runtime_project" "$preview_class" "$3"
fi

exec "$preview_java" -Djoml.nounsafe=true -classpath "$runtime_dist/lib/*" dev.modularui.preview.UiPreviewMain "$runtime_project" "$preview_class"
