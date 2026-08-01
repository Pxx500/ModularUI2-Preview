#!/usr/bin/env sh
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "Usage: ./preview.sh fully.qualified.PreviewClass [output-directory]" >&2
    exit 2
fi

tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
preview_class=$1

if [ "$#" -eq 2 ]; then
    exec "$tool_root/gradlew" -p "$tool_root" preview "-PpreviewClass=$preview_class" "-PpreviewOutput=$2"
fi

exec "$tool_root/gradlew" -p "$tool_root" preview "-PpreviewClass=$preview_class"
