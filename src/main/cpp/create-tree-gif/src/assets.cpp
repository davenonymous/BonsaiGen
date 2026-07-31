#include "assets.hpp"

#include "log.hpp"
#include "miniz/miniz.h"

#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;

namespace ctg {

namespace {

struct JarSource {
    std::string path;
    mz_zip_archive archive{};

    ~JarSource() { mz_zip_reader_end(&archive); }
};

} // namespace

struct AssetSource::Impl {
    // A source is either an open jar (jar != nullptr) or a directory root.
    struct Entry {
        std::string path;
        std::unique_ptr<JarSource> jar; // null => directory
    };
    std::vector<Entry> sources;
};

AssetSource::AssetSource() : impl_(std::make_unique<Impl>()) {}
AssetSource::~AssetSource() = default;

bool AssetSource::addSource(const std::string& path) {
    if (fs::is_directory(path)) {
        impl_->sources.push_back({path, nullptr});
        log::debug("asset source (dir): " + path);
        return true;
    }
    if (!fs::is_regular_file(path)) {
        log::warn("asset source does not exist: " + path);
        return false;
    }

    auto jar = std::make_unique<JarSource>();
    jar->path = path;
    if (!mz_zip_reader_init_file(&jar->archive, path.c_str(), 0)) {
        log::warn("failed to open jar: " + path);
        return false;
    }
    log::debug("asset source (jar): " + path);
    impl_->sources.push_back({path, std::move(jar)});
    return true;
}

std::optional<std::vector<std::uint8_t>> AssetSource::readBytes(const std::string& innerPath) const {
    for (const auto& source : impl_->sources) {
        if (source.jar) {
            auto& archive = const_cast<mz_zip_archive&>(source.jar->archive);
            int fileIndex = mz_zip_reader_locate_file(&archive, innerPath.c_str(), nullptr, 0);
            if (fileIndex < 0) continue;

            std::size_t size = 0;
            void* data = mz_zip_reader_extract_to_heap(&archive, fileIndex, &size, 0);
            if (!data) {
                log::warnOnce("corrupt zip entry " + innerPath + " in " + source.path);
                continue;
            }
            std::vector<std::uint8_t> bytes(static_cast<std::uint8_t*>(data),
                                            static_cast<std::uint8_t*>(data) + size);
            mz_free(data);
            return bytes;
        }

        fs::path candidate = fs::path(source.path) / innerPath;
        if (!fs::is_regular_file(candidate)) continue;

        std::ifstream file(candidate, std::ios::binary);
        if (!file) continue;
        std::vector<std::uint8_t> bytes((std::istreambuf_iterator<char>(file)),
                                        std::istreambuf_iterator<char>());
        return bytes;
    }
    return std::nullopt;
}

bool AssetSource::exists(const std::string& innerPath) const {
    for (const auto& source : impl_->sources) {
        if (source.jar) {
            auto& archive = const_cast<mz_zip_archive&>(source.jar->archive);
            if (mz_zip_reader_locate_file(&archive, innerPath.c_str(), nullptr, 0) >= 0) return true;
        } else if (fs::is_regular_file(fs::path(source.path) / innerPath)) {
            return true;
        }
    }
    return false;
}

std::size_t AssetSource::sourceCount() const { return impl_->sources.size(); }

std::vector<std::string> AssetSource::sourcePaths() const {
    std::vector<std::string> paths;
    for (const auto& source : impl_->sources) paths.push_back(source.path);
    return paths;
}

} // namespace ctg
