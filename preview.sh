#!/usr/bin/env sh
set -eu

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "Usage: ./preview.sh project-directory fully.qualified.PreviewClass [output-directory]" >&2
    exit 2
fi

tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preview_project=$(CDPATH= cd -- "$1" && pwd)
preview_class=$2
preview_dist="$tool_root/build/install/modularui2-preview"
preview_java_file="$preview_dist/bin/java-executable.txt"

if [ ! -f "$preview_java_file" ]; then
    "$tool_root/gradlew" -p "$tool_root" installDist
fi

preview_java=$(head -n 1 "$preview_java_file")

if [ "$#" -eq 3 ]; then
    exec "$preview_java" -classpath "$preview_dist/lib/*" dev.modularui.preview.UiPreviewMain "$preview_project" "$preview_class" "$3"
fi

exec "$preview_java" -classpath "$preview_dist/lib/*" dev.modularui.preview.UiPreviewMain "$preview_project" "$preview_class"
