#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

namespace ctg {

// Ordered search path over jar files (zip archives) and plain directories.
// Paths are archive-internal, e.g. "assets/minecraft/textures/block/oak_log.png".
// The first source containing a path wins. Knows nothing about Minecraft.
class AssetSource {
public:
    AssetSource();
    ~AssetSource();
    AssetSource(const AssetSource&) = delete;
    AssetSource& operator=(const AssetSource&) = delete;

    // Returns false (with a log message) when the jar/directory cannot be opened.
    bool addSource(const std::string& path);

    std::optional<std::vector<std::uint8_t>> readBytes(const std::string& innerPath) const;
    bool exists(const std::string& innerPath) const;

    std::size_t sourceCount() const;
    std::vector<std::string> sourcePaths() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace ctg
