#pragma once

#include "encode.hpp"

#include <array>
#include <cstdint>
#include <vector>

namespace ctg {

// Global palette over all frames (RGB555 histogram + weighted median cut) so
// colors stay stable across the whole animation.
struct Palette {
    std::vector<std::array<std::uint8_t, 3>> colors; // <= requested size
    std::array<std::uint8_t, 32768> lookup{};        // RGB555 -> palette index
};

Palette buildPalette(const std::vector<Frame>& frames, int maxColors);

inline int rgb555(std::uint8_t r, std::uint8_t g, std::uint8_t b) {
    return ((r >> 3) << 10) | ((g >> 3) << 5) | (b >> 3);
}

} // namespace ctg
