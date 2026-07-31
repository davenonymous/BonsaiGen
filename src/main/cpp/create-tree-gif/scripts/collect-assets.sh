#!/usr/bin/env bash
# Prints "--assets <path>" arguments for create-tree-gif: local mod jars, the
# gradle curse.maven cache, and the vanilla client jar. Mod jars come first so
# their assets win over vanilla.
#
# Environment overrides:
#   GRADLE_USER_HOME  gradle cache root (default ~/.gradle)
#   VANILLA_JAR       explicit path to the minecraft client jar
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

for jar in "$REPO_ROOT"/libs/*.jar; do
    [ -f "$jar" ] && printf -- '--assets %s\n' "$jar"
done

for jar in "$GRADLE_HOME"/caches/modules-2/files-2.1/curse.maven/*/*/*/*.jar; do
    [ -f "$jar" ] && printf -- '--assets %s\n' "$jar"
done

minecraft_version="$(sed -n 's/^minecraft_version=//p' "$REPO_ROOT/gradle.properties")"
vanilla_candidates=(
    "${VANILLA_JAR:-}"
    "$GRADLE_HOME/caches/neoformruntime/artifacts/minecraft_${minecraft_version}_client.jar"
    "$REPO_ROOT/.cache/minecraft_${minecraft_version}_client.jar"
)
for candidate in "${vanilla_candidates[@]}"; do
    if [ -n "$candidate" ] && [ -f "$candidate" ]; then
        printf -- '--assets %s\n' "$candidate"
        exit 0
    fi
done

echo "warning: no vanilla client jar found (run scripts/fetch-vanilla-jar.sh)" >&2
exit 0
