#!/usr/bin/env bash
# Convenience wrapper: builds (if needed) and renders previews for the given
# model files or directories. Used both locally and by CI.
#
#   scripts/render-previews.sh --out-dir out generated/resourcepacks
#   scripts/render-previews.sh --out-dir previews $(cat changed-models.txt)
set -euo pipefail

TOOL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BINARY="$TOOL_ROOT/build/create-tree-gif"

if [ ! -x "$BINARY" ]; then
    echo "building create-tree-gif ..." >&2
    cmake -S "$TOOL_ROOT" -B "$TOOL_ROOT/build" -DCMAKE_BUILD_TYPE=Release >&2
    cmake --build "$TOOL_ROOT/build" -j >&2
fi

ASSET_ARGS=()
while read -r flag jar_path; do
    [ "$flag" = "--assets" ] && ASSET_ARGS+=("$flag" "$jar_path")
done < <("$TOOL_ROOT/scripts/collect-assets.sh")

exec "$BINARY" "${ASSET_ARGS[@]}" "$@"
