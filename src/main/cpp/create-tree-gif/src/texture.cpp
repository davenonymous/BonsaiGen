#include "texture.hpp"

#include "log.hpp"
#include "nlohmann/json.hpp"
#include "stb/stb_image.h"

#include <cstring>

namespace ctg {

namespace {

constexpr int kFallbackSize = 16;

std::vector<std::uint8_t> makeCheckerboard() {
    std::vector<std::uint8_t> rgba(kFallbackSize * kFallbackSize * 4);
    for (int y = 0; y < kFallbackSize; ++y) {
        for (int x = 0; x < kFallbackSize; ++x) {
            bool magenta = ((x / 4) + (y / 4)) % 2 == 0;
            std::uint8_t* px = &rgba[(y * kFallbackSize + x) * 4];
            px[0] = magenta ? 0xF8 : 0x00;
            px[1] = 0x00;
            px[2] = magenta ? 0xF8 : 0x00;
            px[3] = 0xFF;
        }
    }
    return rgba;
}

// "minecraft:block/oak_log" -> "assets/minecraft/textures/block/oak_log.png"
std::string texturePathFor(const std::string& resourceLocation) {
    std::string ns = "minecraft";
    std::string path = resourceLocation;
    auto colonPos = resourceLocation.find(':');
    if (colonPos != std::string::npos) {
        ns = resourceLocation.substr(0, colonPos);
        path = resourceLocation.substr(colonPos + 1);
    }
    return "assets/" + ns + "/textures/" + path + ".png";
}

// Animated textures are vertical strips of square frames. Returns the row of
// the frame to display: .mcmeta animation.frames[0] when present, else 0.
int firstAnimationFrameRow(const AssetSource& assets, const std::string& pngPath) {
    auto metaBytes = assets.readBytes(pngPath + ".mcmeta");
    if (!metaBytes) return 0;

    auto meta = nlohmann::json::parse(metaBytes->begin(), metaBytes->end(), nullptr, false);
    if (meta.is_discarded()) return 0;
    try {
        auto frames = meta.value("animation", nlohmann::json::object())
                          .value("frames", nlohmann::json::array());
        if (frames.empty()) return 0;

        const auto& first = frames.front();
        if (first.is_number_integer()) return first.get<int>();
        if (first.is_object() && first.contains("index") && first["index"].is_number_integer()) {
            return first["index"].get<int>();
        }
    } catch (const nlohmann::json::exception&) {
        log::warnOnce("malformed .mcmeta for " + pngPath);
    }
    return 0;
}

} // namespace

TextureStore::TextureStore(const AssetSource& assets) : assets_(assets) {
    layers_.push_back({makeCheckerboard(), kFallbackSize});
    byName_["__missing__"] = Texture{0, kFallbackSize, true, false};
    version_ = 1;
}

const Texture& TextureStore::ensure(const std::string& resourceLocation) {
    auto found = byName_.find(resourceLocation);
    if (found != byName_.end()) return found->second;

    Texture texture = decode(resourceLocation);
    return byName_.emplace(resourceLocation, texture).first->second;
}

Texture TextureStore::decode(const std::string& resourceLocation) {
    std::string pngPath = texturePathFor(resourceLocation);

    auto bytes = assets_.readBytes(pngPath);
    if (!bytes) {
        log::warnOnce("missing texture: " + resourceLocation + " (" + pngPath + ")");
        return Texture{0, kFallbackSize, true, false};
    }

    int width = 0, height = 0, channels = 0;
    stbi_uc* pixels = stbi_load_from_memory(bytes->data(), static_cast<int>(bytes->size()),
                                            &width, &height, &channels, 4);
    if (!pixels) {
        log::warnOnce("failed to decode texture: " + resourceLocation);
        return Texture{0, kFallbackSize, true, false};
    }

    // Animated strip: crop to one square frame.
    int cropRow = 0;
    if (height > width && height % width == 0) {
        int frameCount = height / width;
        cropRow = firstAnimationFrameRow(assets_, pngPath);
        if (cropRow < 0 || cropRow >= frameCount) cropRow = 0;
        height = width;
    } else if (height != width) {
        log::warnOnce("non-square texture " + resourceLocation + ", cropping");
        height = width = std::min(width, height);
    }

    Layer layer;
    layer.size = width;
    layer.rgba.resize(static_cast<std::size_t>(width) * width * 4);
    std::memcpy(layer.rgba.data(),
                pixels + static_cast<std::size_t>(cropRow) * width * width * 4,
                layer.rgba.size());
    stbi_image_free(pixels);

    bool fullyOpaque = true;
    for (std::size_t i = 3; i < layer.rgba.size(); i += 4) {
        if (layer.rgba[i] != 0xFF) { fullyOpaque = false; break; }
    }

    Texture texture;
    texture.layer = static_cast<int>(layers_.size());
    texture.size = width;
    texture.fullyOpaque = fullyOpaque;
    texture.decoded = true;
    layers_.push_back(std::move(layer));
    ++version_;
    return texture;
}

int TextureStore::layerCount() const { return static_cast<int>(layers_.size()); }

std::uint64_t TextureStore::version() const { return version_; }

std::vector<std::uint8_t> TextureStore::buildLayers(int& layerSize) const {
    // Bounded so one oversized mod texture cannot blow the atlas up to
    // layers * size^2; larger textures are downsampled into their layer.
    constexpr int kMaxLayerSize = 64;
    layerSize = kFallbackSize;
    for (const auto& layer : layers_) layerSize = std::max(layerSize, layer.size);
    layerSize = std::min(layerSize, kMaxLayerSize);

    std::vector<std::uint8_t> data(layers_.size() * static_cast<std::size_t>(layerSize) * layerSize * 4);
    for (std::size_t index = 0; index < layers_.size(); ++index) {
        const Layer& layer = layers_[index];
        std::uint8_t* dst = &data[index * static_cast<std::size_t>(layerSize) * layerSize * 4];
        for (int y = 0; y < layerSize; ++y) {
            // Rational nearest-neighbour map: exact for any size ratio, in
            // both directions, never past the source bounds.
            int sourceY = y * layer.size / layerSize;
            for (int x = 0; x < layerSize; ++x) {
                int sourceX = x * layer.size / layerSize;
                const std::uint8_t* src = &layer.rgba[(static_cast<std::size_t>(sourceY) * layer.size + sourceX) * 4];
                std::memcpy(&dst[(static_cast<std::size_t>(y) * layerSize + x) * 4], src, 4);
            }
        }
    }
    return data;
}

} // namespace ctg
