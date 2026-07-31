#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace ctg {

struct Frame {
    int width = 0;
    int height = 0;
    std::vector<std::uint8_t> rgba; // straight (non-premultiplied) alpha
};

bool encodeWebp(const std::string& path, const std::vector<Frame>& frames, int frameDelayMs,
                bool lossless, int quality);

bool encodeGif(const std::string& path, const std::vector<Frame>& frames, int frameDelayMs,
               int maxColors, bool dither);

} // namespace ctg
