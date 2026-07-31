#!/usr/bin/env bash
# End-to-end smoke test: builds the tool, checks the resolution gate over all
# models, renders one tree and validates the outputs.
set -euo pipefail

TOOL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "$TOOL_ROOT/../../../.." && pwd)"
BINARY="$TOOL_ROOT/build/create-tree-gif"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

fail() { echo "FAIL: $*" >&2; exit 1; }

if [ ! -x "$BINARY" ]; then
    cmake -S "$TOOL_ROOT" -B "$TOOL_ROOT/build" -DCMAKE_BUILD_TYPE=Release
    cmake --build "$TOOL_ROOT/build" -j
fi

ASSET_ARGS=()
while read -r flag jar_path; do
    [ "$flag" = "--assets" ] && ASSET_ARGS+=("$flag" "$jar_path")
done < <("$TOOL_ROOT/scripts/collect-assets.sh")
[ "${#ASSET_ARGS[@]}" -gt 0 ] || fail "collect-assets.sh found no asset sources"

echo "== GL context =="
"$BINARY" --gl-info || fail "GL context creation failed"

echo "== resolution gate over all models =="
"$BINARY" "${ASSET_ARGS[@]}" --dry-run --report -q "$REPO_ROOT/generated/resourcepacks" \
    2>"$WORK_DIR/report.txt" || fail "dry run failed"
# Only these blocks may be unresolved; their jars are known-absent
# (mods commented out in mods.conf / missing libs jars).
KNOWN_MISSING='^(eternal_starlight:starlight_mangrove_(leaves|log|roots)|regions_unexplored:(cactus_flower|mauve_leaves|mauve_log))\|'
UNEXPECTED="$(grep '^  ' "$WORK_DIR/report.txt" | sed 's/^  //' |
    grep -Ev "$KNOWN_MISSING" || true)"
if [ -n "$UNEXPECTED" ]; then
    fail "unexpected unresolved blocks:"$'\n'"$UNEXPECTED"
fi

echo "== render azalea_tree =="
MODEL="$REPO_ROOT/generated/resourcepacks/minecraft/assets/bonsaitrees4/models/multiblock/minecraft/azalea_tree.json"
"$BINARY" "${ASSET_ARGS[@]}" --out-dir "$WORK_DIR/out" --frames 8 -q \
    --manifest-json "$WORK_DIR/manifest.json" "$MODEL" || fail "render failed"

WEBP="$WORK_DIR/out/minecraft/minecraft/azalea_tree.webp"
GIF="$WORK_DIR/out/minecraft/minecraft/azalea_tree.gif"
[ -s "$WEBP" ] || fail "webp missing or empty"
[ -s "$GIF" ] || fail "gif missing or empty"
head -c 6 "$GIF" | grep -q "GIF89a" || fail "gif is not GIF89a"
head -c 4 "$WEBP" | grep -q "RIFF" || fail "webp is not RIFF"
[ "$(grep -a -o ANMF "$WEBP" | wc -l)" -eq 8 ] || fail "webp does not contain 8 animation frames"
grep -q '"outputs"' "$WORK_DIR/manifest.json" || fail "manifest missing outputs"

if command -v ffprobe >/dev/null 2>&1; then
    PIX_FMT="$(ffprobe -v error -show_entries stream=pix_fmt -of csv=p=0 "$GIF")"
    [ "$PIX_FMT" = "bgra" ] || fail "gif has no transparency (pix_fmt=$PIX_FMT)"
fi

echo "== determinism =="
"$BINARY" "${ASSET_ARGS[@]}" --out-dir "$WORK_DIR/out2" --frames 8 -q "$MODEL"
cmp "$GIF" "$WORK_DIR/out2/minecraft/minecraft/azalea_tree.gif" || fail "gif not deterministic"
cmp "$WEBP" "$WORK_DIR/out2/minecraft/minecraft/azalea_tree.webp" || fail "webp not deterministic"

echo "PASS"
