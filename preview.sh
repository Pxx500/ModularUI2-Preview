#!/usr/bin/env sh
set -eu

tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preview_launcher="$tool_root/build/install/modularui2-preview/bin/modularui2-preview"

if [ ! -f "$preview_launcher" ]; then
    "$tool_root/gradlew" -p "$tool_root" installDist
fi

exec "$preview_launcher" "$@"
