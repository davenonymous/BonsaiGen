# create-tree-gif

Renders animated 360-degree turntable previews (animated WebP and GIF, both
with transparency) of Bonsai Trees 4 multiblock models, e.g. the files under
`generated/resourcepacks/*/assets/bonsaitrees4/models/multiblock/`.

Runs fully headless (SDL2 offscreen video driver + Mesa llvmpipe, with a direct
EGL surfaceless fallback), so it works locally and on GitHub Actions runners
without a display or GPU. Every block is rendered as a textured cube.

## Building

Dependencies (Ubuntu): `cmake ninja-build libsdl2-dev libwebp-dev libgif-dev
libegl1-mesa-dev libgl1-mesa-dev`. JSON parsing (nlohmann), PNG decoding (stb)
and jar reading (miniz) are vendored under `third_party/`.

```bash
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
```

## Usage

```bash
# Everything wired up automatically (build if needed + asset discovery):
scripts/render-previews.sh --out-dir out ../../../../generated/resourcepacks

# Manually:
build/create-tree-gif $(scripts/collect-assets.sh) \
    --out-dir out \
    ../../../../generated/resourcepacks/minecraft/assets/bonsaitrees4/models/multiblock/minecraft/azalea_tree.json
```

Textures are read from an ordered list of `--assets` sources (mod jars or
directories; first match wins):

- `scripts/collect-assets.sh` prints the local jar list: `libs/*.jar`, the
  gradle `curse.maven` cache, and the vanilla client jar.
- `./gradlew -q printModJars` prints the same list from the resolved gradle
  classpath (and forces the jars to download on a cold cache) - preferred on CI.
- `scripts/fetch-vanilla-jar.sh` downloads the Minecraft client jar from
  Mojang's piston-meta into `.cache/` (version from `gradle.properties`).
- `scripts/generate-galleries.sh <out-dir>` writes a `README.md` gallery into
  every pack folder of a previews directory (plus a root index), using relative
  image links so GitHub renders them when browsing a branch.

Run `build/create-tree-gif --help` for all options (canvas size, frame count,
camera, formats, tint overrides, sharding, ...). Useful diagnostics:

- `--gl-info` prints the GL context and exits (run this first when CI breaks).
- `--dry-run --report` resolves all blocks without rendering; unresolved states
  are listed and rendered as magenta/black checkerboards otherwise.
- `--dump-frame 0` writes a single frame as PNG next to the animations.

Output files mirror the pack layout: `<out-dir>/<pack>/<namespace-path>.webp`
and `.gif`. Exit codes: 0 ok, 1 usage/render error, 2 GL init failure,
3 unresolved blocks with `--strict`.

## Rendering model

- Blockstates are resolved like Minecraft does: `blockstates/<name>.json`
  variant/multipart selection, model parent chain, per-face textures, variant
  x/y rotations (log `axis` works), coplanar overlay layers (Biomes O' Plenty
  style leaves), and `tintindex`-driven foliage tinting (override per block
  with `--tint name=RRGGBB`).
- Leaves and other cutout textures use alpha-test rendering; faces between
  adjacent leaf blocks are kept ("fancy graphics" look, `--cull-leaves`
  disables).
- Known limitation: `uvlock` is ignored and non-cube shapes (vines, fans,
  crosses) are drawn as full cubes by design.

## Tests

`test/smoke-test.sh` builds the tool, verifies the GL context, checks that all
403 models resolve (except a known list of blocks whose mod jars are absent),
renders one model and validates both output formats plus determinism.

## CI

`.github/workflows/preview-bonsai-models.yaml` renders previews for the
multiblock models changed in a pull request, uploads them as an artifact and -
for same-repo PRs - publishes them to the orphan branch `model-previews`
(folder `pr-<N>/`) and posts a sticky PR comment with inline animations.
Every published folder contains per-mod `README.md` galleries; a
`workflow_dispatch` run renders all models and publishes the full gallery to
`gallery/` on the same branch.
