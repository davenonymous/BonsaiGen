#pragma once

#include "assets.hpp"

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace ctg {

struct Texture {
    int layer = 0;          // index into the texture array; 0 is the checkerboard fallback
    int size = 16;          // square edge length in pixels
    bool fullyOpaque = false;
    bool decoded = false;   // false => fallback checkerboard was substituted
};

// Decodes and caches block textures from the asset search path, keyed by
// resource location ("minecraft:block/oak_log"). Produces CPU-side RGBA layers
// for upload as a GL texture array; contains no GL calls itself.
class TextureStore {
public:
    explicit TextureStore(const AssetSource& assets);

    // Never fails: unknown/corrupt textures come back as the checkerboard.
    const Texture& ensure(const std::string& resourceLocation);

    int layerCount() const;
    // Increases whenever a new layer is added; lets the renderer re-upload lazily.
    std::uint64_t version() const;

    // All layers as one contiguous RGBA block, smaller textures integer-upscaled
    // to the common layer size (returned via layerSize).
    std::vector<std::uint8_t> buildLayers(int& layerSize) const;

private:
    struct Layer {
        std::vector<std::uint8_t> rgba;
        int size = 16;
    };

    Texture decode(const std::string& resourceLocation);

    const AssetSource& assets_;
    std::unordered_map<std::string, Texture> byName_;
    std::vector<Layer> layers_;
    std::uint64_t version_ = 0;
};

} // namespace ctg
