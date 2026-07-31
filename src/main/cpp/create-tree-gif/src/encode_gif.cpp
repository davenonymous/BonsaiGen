#include "encode.hpp"
#include "log.hpp"
#include "quantize.hpp"

#include <gif_lib.h>

#include <algorithm>
#include <cstring>
#include <filesystem>
#include <vector>

namespace ctg {

namespace {

// Floyd-Steinberg on straight-alpha pixels; off by default (per-frame speckle).
void ditherFrame(const Frame& frame, const Palette& palette, int transparentIndex,
                 std::vector<GifByteType>& indices) {
    int width = frame.width, height = frame.height;
    std::vector<float> errors(static_cast<std::size_t>(width) * height * 3, 0.0f);

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            std::size_t pixel = static_cast<std::size_t>(y) * width + x;
            if (frame.rgba[pixel * 4 + 3] < 128) {
                indices[pixel] = static_cast<GifByteType>(transparentIndex);
                continue;
            }
            float channels[3];
            for (int c = 0; c < 3; ++c) {
                channels[c] = std::clamp(frame.rgba[pixel * 4 + c] + errors[pixel * 3 + c],
                                         0.0f, 255.0f);
            }
            int index = palette.lookup[rgb555(static_cast<std::uint8_t>(channels[0]),
                                              static_cast<std::uint8_t>(channels[1]),
                                              static_cast<std::uint8_t>(channels[2]))];
            indices[pixel] = static_cast<GifByteType>(index);
            for (int c = 0; c < 3; ++c) {
                float error = channels[c] - palette.colors[index][c];
                if (x + 1 < width) errors[(pixel + 1) * 3 + c] += error * 7.0f / 16.0f;
                if (y + 1 < height) {
                    if (x > 0) errors[(pixel + width - 1) * 3 + c] += error * 3.0f / 16.0f;
                    errors[(pixel + width) * 3 + c] += error * 5.0f / 16.0f;
                    if (x + 1 < width) errors[(pixel + width + 1) * 3 + c] += error * 1.0f / 16.0f;
                }
            }
        }
    }
}

void mapFrame(const Frame& frame, const Palette& palette, int transparentIndex,
              std::vector<GifByteType>& indices) {
    for (std::size_t pixel = 0; pixel < indices.size(); ++pixel) {
        if (frame.rgba[pixel * 4 + 3] < 128) {
            indices[pixel] = static_cast<GifByteType>(transparentIndex);
        } else {
            indices[pixel] = palette.lookup[rgb555(frame.rgba[pixel * 4 + 0],
                                                   frame.rgba[pixel * 4 + 1],
                                                   frame.rgba[pixel * 4 + 2])];
        }
    }
}

} // namespace

bool encodeGif(const std::string& path, const std::vector<Frame>& frames, int frameDelayMs,
               int maxColors, bool dither) {
    if (frames.empty()) return false;
    int width = frames.front().width;
    int height = frames.front().height;

    Palette palette = buildPalette(frames, maxColors);
    int transparentIndex = static_cast<int>(palette.colors.size());

    // giflib requires a power-of-two color map; pad to 256 so the transparent
    // slot right after the palette always exists.
    std::vector<GifColorType> colors(256, GifColorType{0, 0, 0});
    for (std::size_t index = 0; index < palette.colors.size(); ++index) {
        colors[index] = {palette.colors[index][0], palette.colors[index][1],
                         palette.colors[index][2]};
    }
    ColorMapObject* colorMap = GifMakeMapObject(256, colors.data());
    if (!colorMap) return false;

    int errorCode = 0;
    GifFileType* gif = EGifOpenFileName(path.c_str(), false, &errorCode);
    if (!gif) {
        log::error("cannot open gif for writing: " + path + " (" +
                   std::string(GifErrorString(errorCode)) + ")");
        GifFreeMapObject(colorMap);
        return false;
    }

    EGifSetGifVersion(gif, true); // GIF89a, needed for transparency + looping
    bool ok = EGifPutScreenDesc(gif, width, height, 8, transparentIndex, colorMap) != GIF_ERROR;
    GifFreeMapObject(colorMap);

    // NETSCAPE2.0 application extension: loop forever.
    if (ok) {
        static const char kNetscape[] = "NETSCAPE2.0";
        static const std::uint8_t kLoopForever[] = {0x01, 0x00, 0x00};
        ok = EGifPutExtensionLeader(gif, APPLICATION_EXT_FUNC_CODE) != GIF_ERROR &&
             EGifPutExtensionBlock(gif, 11, kNetscape) != GIF_ERROR &&
             EGifPutExtensionBlock(gif, 3, kLoopForever) != GIF_ERROR &&
             EGifPutExtensionTrailer(gif) != GIF_ERROR;
    }

    std::vector<GifByteType> indices(static_cast<std::size_t>(width) * height);
    for (const Frame& frame : frames) {
        if (!ok) break;
        if (frame.width != width || frame.height != height) {
            ok = false;
            break;
        }

        GraphicsControlBlock gcb{};
        gcb.DisposalMode = DISPOSE_BACKGROUND;
        gcb.UserInputFlag = false;
        gcb.DelayTime = frameDelayMs / 10;
        gcb.TransparentColor = transparentIndex;
        GifByteType gcbBytes[4];
        std::size_t gcbLength = EGifGCBToExtension(&gcb, gcbBytes);
        ok = EGifPutExtension(gif, GRAPHICS_EXT_FUNC_CODE, static_cast<int>(gcbLength),
                              gcbBytes) != GIF_ERROR;
        if (!ok) break;

        ok = EGifPutImageDesc(gif, 0, 0, width, height, false, nullptr) != GIF_ERROR;
        if (!ok) break;

        if (dither) {
            ditherFrame(frame, palette, transparentIndex, indices);
        } else {
            mapFrame(frame, palette, transparentIndex, indices);
        }
        for (int row = 0; row < height && ok; ++row) {
            ok = EGifPutLine(gif, &indices[static_cast<std::size_t>(row) * width], width) !=
                 GIF_ERROR;
        }
    }

    if (EGifCloseFile(gif, &errorCode) == GIF_ERROR) {
        log::error("failed to finalize gif: " + path + " (" +
                   std::string(GifErrorString(errorCode)) + ")");
        ok = false;
    }
    if (!ok) {
        log::error("gif encode failed: " + path);
        std::error_code removeError;
        std::filesystem::remove(path, removeError); // never leave a truncated file
    }
    return ok;
}

} // namespace ctg
