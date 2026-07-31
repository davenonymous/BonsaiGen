#include "options.hpp"

#include "log.hpp"

#include <charconv>
#include <cstdio>
#include <cstring>
#include <sstream>

namespace ctg {

namespace {

const char* kUsage = R"(create-tree-gif - render animated 360-degree previews of bonsai tree models

Usage: create-tree-gif [options] <model.json|directory>...

Required:
  --assets PATH          Jar file or directory with Minecraft assets. Repeatable;
                         earlier sources win.

Output:
  --out-dir DIR          Output directory (default: current directory)
  --out FILE             Output basename (single input only; extension ignored)
  --formats LIST         Comma list of webp,gif,png (default: webp,gif)
  --manifest-json FILE   Write a JSON list of generated files

Rendering:
  --size WxH | --size N  Canvas size (default: 512x512)
  --frames N             Frames per rotation (default: 36)
  --frame-delay-ms N     Delay per frame, multiple of 10 (default: 50)
  --pitch DEG            Downward camera pitch (default: 30)
  --yaw-start DEG        Initial rotation (default: 45)
  --spin cw|ccw          Rotation direction (default: ccw)
  --projection MODE      ortho|persp (default: ortho)
  --margin F             Canvas margin fraction (default: 0.05)
  --supersample N        Anti-alias factor, 1 disables (default: 2)
  --background COLOR     none or #RRGGBB (default: none = transparent)
  --cull-leaves          Cull faces between adjacent leaf blocks
  --tint NAME=RRGGBB     Force a tint for a block, even when its model has no
                         tintindex (mods that tint via code). Repeatable.

Encoding:
  --webp-lossless        Lossless WebP (default)
  --webp-quality N       Lossy WebP at quality N (disables lossless)
  --gif-colors N         Palette size, max 255 (default: 255)
  --gif-dither           Enable Floyd-Steinberg dithering (default: off)

Batch:
  --shard I/N            Process only every Nth model, offset I (default: 0/1)
  --strict               Exit 3 when any block state fails to resolve
  --report               Print an unresolved-block summary after the batch
  --dry-run              Resolve models without rendering or encoding

Diagnostics:
  --gl-info              Initialize GL, print context info and exit
  --dump-frame N         Additionally write frame N as PNG
  -v, --verbose          Verbose logging
  -q, --quiet            Errors only

Exit codes: 0 ok, 1 usage/io error, 2 GL init failure, 3 strict resolution failure
)";

bool parseInt(const std::string& text, int& out) {
    auto [ptr, ec] = std::from_chars(text.data(), text.data() + text.size(), out);
    return ec == std::errc() && ptr == text.data() + text.size();
}

bool parseDouble(const std::string& text, double& out) {
    try {
        std::size_t used = 0;
        out = std::stod(text, &used);
        return used == text.size();
    } catch (...) {
        return false;
    }
}

bool parseHexColor(std::string text, std::uint32_t& out) {
    if (!text.empty() && text[0] == '#') text = text.substr(1);
    if (text.size() != 6) return false;
    std::uint32_t value = 0;
    auto [ptr, ec] = std::from_chars(text.data(), text.data() + text.size(), value, 16);
    if (ec != std::errc() || ptr != text.data() + text.size()) return false;
    out = value;
    return true;
}

std::vector<std::string> splitCommaList(const std::string& text) {
    std::vector<std::string> parts;
    std::stringstream stream(text);
    std::string part;
    while (std::getline(stream, part, ',')) {
        if (!part.empty()) parts.push_back(part);
    }
    return parts;
}

bool fail(const std::string& message) {
    log::error(message + " (see --help)");
    return false;
}

} // namespace

std::optional<Options> parseOptions(int argc, char** argv, bool& helpShown) {
    helpShown = false;
    Options opts;

    auto nextValue = [&](int& i, const char* flag, std::string& out) -> bool {
        if (i + 1 >= argc) return fail(std::string(flag) + " requires a value");
        out = argv[++i];
        return true;
    };

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        std::string value;

        if (arg == "--help" || arg == "-h") {
            std::fputs(kUsage, stdout);
            helpShown = true;
            return std::nullopt;
        } else if (arg == "--assets") {
            if (!nextValue(i, "--assets", value)) return std::nullopt;
            opts.assetPaths.push_back(value);
        } else if (arg == "--out-dir") {
            if (!nextValue(i, "--out-dir", value)) return std::nullopt;
            opts.outDir = value;
        } else if (arg == "--out") {
            if (!nextValue(i, "--out", value)) return std::nullopt;
            opts.outFile = value;
        } else if (arg == "--formats") {
            if (!nextValue(i, "--formats", value)) return std::nullopt;
            opts.formats = splitCommaList(value);
            for (const auto& format : opts.formats) {
                if (format != "webp" && format != "gif" && format != "png") {
                    fail("unknown format: " + format);
                    return std::nullopt;
                }
            }
            if (opts.formats.empty()) {
                fail("--formats needs at least one of webp,gif,png");
                return std::nullopt;
            }
        } else if (arg == "--size") {
            if (!nextValue(i, "--size", value)) return std::nullopt;
            auto xPos = value.find('x');
            if (xPos == std::string::npos) {
                if (!parseInt(value, opts.width)) { fail("bad --size"); return std::nullopt; }
                opts.height = opts.width;
            } else {
                if (!parseInt(value.substr(0, xPos), opts.width) ||
                    !parseInt(value.substr(xPos + 1), opts.height)) {
                    fail("bad --size, expected WxH or N");
                    return std::nullopt;
                }
            }
            if (opts.width < 16 || opts.height < 16 || opts.width > 4096 || opts.height > 4096) {
                fail("--size out of range 16..4096");
                return std::nullopt;
            }
        } else if (arg == "--frames") {
            if (!nextValue(i, "--frames", value)) return std::nullopt;
            if (!parseInt(value, opts.frames) || opts.frames < 1 || opts.frames > 360) {
                fail("--frames out of range 1..360");
                return std::nullopt;
            }
        } else if (arg == "--frame-delay-ms") {
            if (!nextValue(i, "--frame-delay-ms", value)) return std::nullopt;
            // Upper bound keeps GIF's 16-bit centisecond field and the WebP
            // millisecond timestamps far away from overflow.
            if (!parseInt(value, opts.frameDelayMs) || opts.frameDelayMs < 10 ||
                opts.frameDelayMs > 60000) {
                fail("--frame-delay-ms out of range 10..60000");
                return std::nullopt;
            }
            if (opts.frameDelayMs % 10 != 0) {
                log::warn("--frame-delay-ms is not a multiple of 10; GIF timing will be rounded");
            }
        } else if (arg == "--pitch") {
            if (!nextValue(i, "--pitch", value)) return std::nullopt;
            if (!parseDouble(value, opts.pitchDeg)) { fail("bad --pitch"); return std::nullopt; }
        } else if (arg == "--yaw-start") {
            if (!nextValue(i, "--yaw-start", value)) return std::nullopt;
            if (!parseDouble(value, opts.yawStartDeg)) { fail("bad --yaw-start"); return std::nullopt; }
        } else if (arg == "--spin") {
            if (!nextValue(i, "--spin", value)) return std::nullopt;
            if (value == "cw") opts.spinClockwise = true;
            else if (value == "ccw") opts.spinClockwise = false;
            else { fail("--spin must be cw or ccw"); return std::nullopt; }
        } else if (arg == "--projection") {
            if (!nextValue(i, "--projection", value)) return std::nullopt;
            if (value == "ortho") opts.projection = Projection::Ortho;
            else if (value == "persp") opts.projection = Projection::Perspective;
            else { fail("--projection must be ortho or persp"); return std::nullopt; }
        } else if (arg == "--margin") {
            if (!nextValue(i, "--margin", value)) return std::nullopt;
            if (!parseDouble(value, opts.margin) || opts.margin < 0.0 || opts.margin > 0.45) {
                fail("--margin out of range 0..0.45");
                return std::nullopt;
            }
        } else if (arg == "--supersample") {
            if (!nextValue(i, "--supersample", value)) return std::nullopt;
            if (!parseInt(value, opts.supersample) || opts.supersample < 1 || opts.supersample > 4) {
                fail("--supersample out of range 1..4");
                return std::nullopt;
            }
        } else if (arg == "--background") {
            if (!nextValue(i, "--background", value)) return std::nullopt;
            if (value == "none") {
                opts.backgroundRgb.reset();
            } else {
                std::uint32_t rgb = 0;
                if (!parseHexColor(value, rgb)) { fail("bad --background"); return std::nullopt; }
                opts.backgroundRgb = rgb;
            }
        } else if (arg == "--webp-lossless") {
            opts.webpLossless = true;
        } else if (arg == "--webp-quality") {
            if (!nextValue(i, "--webp-quality", value)) return std::nullopt;
            if (!parseInt(value, opts.webpQuality) || opts.webpQuality < 0 || opts.webpQuality > 100) {
                fail("--webp-quality out of range 0..100");
                return std::nullopt;
            }
            opts.webpLossless = false;
        } else if (arg == "--gif-colors") {
            if (!nextValue(i, "--gif-colors", value)) return std::nullopt;
            if (!parseInt(value, opts.gifColors) || opts.gifColors < 2 || opts.gifColors > 255) {
                fail("--gif-colors out of range 2..255");
                return std::nullopt;
            }
        } else if (arg == "--gif-dither") {
            opts.gifDither = true;
        } else if (arg == "--cull-leaves") {
            opts.cullLeaves = true;
        } else if (arg == "--tint") {
            if (!nextValue(i, "--tint", value)) return std::nullopt;
            auto eqPos = value.find('=');
            std::uint32_t rgb = 0;
            if (eqPos == std::string::npos || !parseHexColor(value.substr(eqPos + 1), rgb)) {
                fail("--tint expects NAME=RRGGBB");
                return std::nullopt;
            }
            opts.tintOverrides[value.substr(0, eqPos)] = rgb;
        } else if (arg == "--shard") {
            if (!nextValue(i, "--shard", value)) return std::nullopt;
            auto slashPos = value.find('/');
            if (slashPos == std::string::npos ||
                !parseInt(value.substr(0, slashPos), opts.shardIndex) ||
                !parseInt(value.substr(slashPos + 1), opts.shardCount) ||
                opts.shardCount < 1 || opts.shardIndex < 0 || opts.shardIndex >= opts.shardCount) {
                fail("--shard expects I/N with 0 <= I < N");
                return std::nullopt;
            }
        } else if (arg == "--strict") {
            opts.strict = true;
        } else if (arg == "--report") {
            opts.report = true;
        } else if (arg == "--dry-run") {
            opts.dryRun = true;
        } else if (arg == "--dump-frame") {
            if (!nextValue(i, "--dump-frame", value)) return std::nullopt;
            int frame = 0;
            if (!parseInt(value, frame) || frame < 0) { fail("bad --dump-frame"); return std::nullopt; }
            opts.dumpFrame = frame;
        } else if (arg == "--manifest-json") {
            if (!nextValue(i, "--manifest-json", value)) return std::nullopt;
            opts.manifestJson = value;
        } else if (arg == "--gl-info") {
            opts.glInfoOnly = true;
        } else if (arg == "-v" || arg == "--verbose") {
            log::setLevel(log::Level::Verbose);
        } else if (arg == "-q" || arg == "--quiet") {
            log::setLevel(log::Level::Quiet);
        } else if (!arg.empty() && arg[0] == '-') {
            fail("unknown option: " + arg);
            return std::nullopt;
        } else {
            opts.inputs.push_back(arg);
        }
    }

    if (opts.glInfoOnly) return opts;

    if (opts.assetPaths.empty()) { fail("at least one --assets is required"); return std::nullopt; }
    if (opts.inputs.empty()) { fail("no model files or directories given"); return std::nullopt; }
    if (opts.outFile && opts.inputs.size() != 1) {
        fail("--out is only valid with exactly one input file");
        return std::nullopt;
    }
    return opts;
}

} // namespace ctg
