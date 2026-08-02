#!/usr/bin/env sh
set -eu

tool_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -n "${JAVA_HOME:-}" ]; then
    preview_java="$JAVA_HOME/bin/java"
    preview_javac="$JAVA_HOME/bin/javac"
else
    preview_java=$(command -v java || true)
    preview_javac=$(command -v javac || true)
fi

if [ -z "$preview_java" ] || [ ! -x "$preview_java" ]; then
    echo "ModularUI2 Preview requires JDK 21. Set JAVA_HOME or add java and javac to PATH." >&2
    exit 2
fi
if [ -z "$preview_javac" ] || [ ! -x "$preview_javac" ]; then
    echo "ModularUI2 Preview requires a JDK with javac, not a JRE." >&2
    exit 2
fi

java_version=$("$preview_java" -version 2>&1 | awk 'NR == 1 { for (field = 1; field <= NF; field++) if (match($field, /[0-9]+([.][0-9]+)*/)) { print substr($field, RSTART, RLENGTH); exit } }')
java_major=${java_version%%.*}
case "$java_major" in
    ''|*[!0-9]*)
        echo "Could not determine the installed Java version: $java_version" >&2
        exit 2
        ;;
esac
if [ "$java_major" -lt 21 ]; then
    echo "ModularUI2 Preview requires JDK 21 or newer; found $java_version." >&2
    exit 2
fi

preview_launcher="$tool_root/bin/modularui2-preview"
if [ ! -f "$preview_launcher" ]; then
    preview_launcher="$tool_root/build/install/modularui2-preview/bin/modularui2-preview"
    if [ ! -f "$preview_launcher" ]; then
        "$tool_root/gradlew" -p "$tool_root" installDist
    fi
fi

exec "$preview_launcher" "$@"
