#!/usr/bin/env bash
# Downloads the Minecraft client jar (block textures/models source) from
# Mojang's piston-meta into .cache/, verifying the published sha1.
# The version is read from gradle.properties. Requires curl and jq.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
MINECRAFT_VERSION="$(sed -n 's/^minecraft_version=//p' "$REPO_ROOT/gradle.properties")"
if [ -z "$MINECRAFT_VERSION" ]; then
    echo "error: minecraft_version not found in gradle.properties" >&2
    exit 1
fi

OUT_DIR="${1:-$REPO_ROOT/.cache}"
OUT_JAR="$OUT_DIR/minecraft_${MINECRAFT_VERSION}_client.jar"

if [ -f "$OUT_JAR" ]; then
    echo "already present: $OUT_JAR"
    exit 0
fi
mkdir -p "$OUT_DIR"
trap 'rm -f "$OUT_JAR.tmp"' EXIT

MANIFEST_URL="https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
VERSION_URL="$(curl -fsSL "$MANIFEST_URL" |
    jq -r --arg v "$MINECRAFT_VERSION" '.versions[] | select(.id == $v) | .url')"
if [ -z "$VERSION_URL" ]; then
    echo "error: version $MINECRAFT_VERSION not found in Mojang manifest" >&2
    exit 1
fi

CLIENT_INFO="$(curl -fsSL "$VERSION_URL" | jq -c '.downloads.client')"
CLIENT_URL="$(jq -r '.url' <<<"$CLIENT_INFO")"
CLIENT_SHA1="$(jq -r '.sha1' <<<"$CLIENT_INFO")"

echo "downloading $CLIENT_URL"
curl -fsSL -o "$OUT_JAR.tmp" "$CLIENT_URL"
echo "$CLIENT_SHA1  $OUT_JAR.tmp" | sha1sum -c - >/dev/null
mv "$OUT_JAR.tmp" "$OUT_JAR"
echo "fetched: $OUT_JAR"
