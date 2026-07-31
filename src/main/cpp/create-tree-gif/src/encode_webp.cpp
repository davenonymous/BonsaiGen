#include "encode.hpp"
#include "log.hpp"

#include <webp/encode.h>
#include <webp/mux.h>

#include <cstdio>
#include <filesystem>

namespace ctg {

bool encodeWebp(const std::string& path, const std::vector<Frame>& frames, int frameDelayMs,
                bool lossless, int quality) {
    if (frames.empty()) return false;
    int width = frames.front().width;
    int height = frames.front().height;

    WebPAnimEncoderOptions encoderOptions;
    if (!WebPAnimEncoderOptionsInit(&encoderOptions)) return false;
    encoderOptions.anim_params.loop_count = 0; // infinite
    encoderOptions.minimize_size = 1;

    WebPAnimEncoder* encoder = WebPAnimEncoderNew(width, height, &encoderOptions);
    if (!encoder) return false;

    WebPConfig config;
    if (!WebPConfigInit(&config)) {
        WebPAnimEncoderDelete(encoder);
        return false;
    }
    config.lossless = lossless ? 1 : 0;
    // For lossless, quality selects compression effort (100 = extremely slow);
    // pixel-art frames compress nearly as well at low effort.
    config.quality = lossless ? 30.0f : static_cast<float>(quality);
    config.alpha_quality = 100;
    if (!WebPValidateConfig(&config)) {
        WebPAnimEncoderDelete(encoder);
        return false;
    }

    bool ok = true;
    for (std::size_t frameIndex = 0; frameIndex < frames.size() && ok; ++frameIndex) {
        const Frame& frame = frames[frameIndex];
        if (frame.width != width || frame.height != height) {
            ok = false;
            break;
        }

        WebPPicture picture;
        if (!WebPPictureInit(&picture)) {
            ok = false;
            break;
        }
        picture.use_argb = 1;
        picture.width = width;
        picture.height = height;
        ok = WebPPictureImportRGBA(&picture, frame.rgba.data(), width * 4) &&
             WebPAnimEncoderAdd(encoder, &picture,
                                static_cast<int>(frameIndex) * frameDelayMs, &config);
        if (!ok) {
            log::error("webp frame encode failed: " + std::string(
                           WebPAnimEncoderGetError(encoder)));
        }
        WebPPictureFree(&picture);
    }

    WebPData data;
    WebPDataInit(&data);
    if (ok) {
        // A null frame closes the last frame's duration.
        ok = WebPAnimEncoderAdd(encoder, nullptr,
                                static_cast<int>(frames.size()) * frameDelayMs, nullptr) &&
             WebPAnimEncoderAssemble(encoder, &data);
        if (!ok) {
            log::error("webp assemble failed: " + std::string(WebPAnimEncoderGetError(encoder)));
        }
    }
    WebPAnimEncoderDelete(encoder);
    if (!ok) {
        WebPDataClear(&data);
        return false;
    }

    std::FILE* file = std::fopen(path.c_str(), "wb");
    if (!file) {
        log::error("cannot open webp for writing: " + path);
        WebPDataClear(&data);
        return false;
    }
    bool written = std::fwrite(data.bytes, 1, data.size, file) == data.size;
    std::fclose(file);
    WebPDataClear(&data);
    if (!written) {
        log::error("short write: " + path);
        std::error_code removeError;
        std::filesystem::remove(path, removeError); // never leave a truncated file
    }
    return written;
}

} // namespace ctg
