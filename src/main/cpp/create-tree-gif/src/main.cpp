#include "assets.hpp"
#include "blockmodel.hpp"
#include "encode.hpp"
#include "log.hpp"
#include "options.hpp"
#include "render.hpp"
#include "scene.hpp"
#include "texture.hpp"

#include "nlohmann/json.hpp"
#include "stb/stb_image_write.h"

#include <algorithm>
#include <chrono>
#include <filesystem>
#include <fstream>

namespace fs = std::filesystem;
using nlohmann::json;

namespace ctg {

namespace {

constexpr int kExitOk = 0;
constexpr int kExitUsage = 1;
constexpr int kExitGlFailure = 2;
constexpr int kExitStrictResolution = 3;

std::vector<std::string> collectModelFiles(const std::vector<std::string>& inputs) {
    std::vector<std::string> files;
    for (const std::string& input : inputs) {
        if (fs::is_regular_file(input)) {
            files.push_back(input);
            continue;
        }
        if (!fs::is_directory(input)) {
            log::error("input not found: " + input);
            continue;
        }
        for (const auto& entry : fs::recursive_directory_iterator(input)) {
            if (!entry.is_regular_file() || entry.path().extension() != ".json") continue;
            if (entry.path().generic_string().find("models/multiblock") == std::string::npos)
                continue;
            files.push_back(entry.path().generic_string());
        }
    }
    std::sort(files.begin(), files.end());
    files.erase(std::unique(files.begin(), files.end()), files.end());
    return files;
}

// generated/resourcepacks/<pack>/assets/bonsaitrees4/models/multiblock/<rest>.json
// -> "<pack>/<rest>"; anything else -> basename without extension.
std::string outputStemFor(const std::string& modelPath) {
    std::string generic = fs::path(modelPath).generic_string();

    auto packsPos = generic.find("resourcepacks/");
    auto multiblockPos = generic.find("/models/multiblock/");
    if (packsPos != std::string::npos && multiblockPos != std::string::npos &&
        multiblockPos > packsPos) {
        std::string pack = generic.substr(packsPos + 14);
        pack = pack.substr(0, pack.find('/'));
        std::string rest = generic.substr(multiblockPos + 19);
        auto dotPos = rest.rfind('.');
        if (dotPos != std::string::npos) rest = rest.substr(0, dotPos);
        return pack + "/" + rest;
    }
    return fs::path(modelPath).stem().string();
}

bool writePng(const std::string& path, const Frame& frame) {
    return stbi_write_png(path.c_str(), frame.width, frame.height, 4, frame.rgba.data(),
                          frame.width * 4) != 0;
}

void printReport(const BlockModelResolver& resolver) {
    const auto& unresolved = resolver.unresolvedStates();
    std::fprintf(stderr, "RESOLVED %d/%zu block states\n", resolver.resolvedStateCount(),
                 resolver.resolvedStateCount() + unresolved.size());
    if (unresolved.empty()) return;

    std::fprintf(stderr, "unresolved states:\n");
    for (const std::string& state : unresolved) {
        std::fprintf(stderr, "  %s\n", state.c_str());
    }
}

struct ManifestEntry {
    std::string model;
    std::string stem;
    std::vector<std::string> outputs;
};

void writeManifest(const std::string& path, const std::vector<ManifestEntry>& entries,
                   const BlockModelResolver& resolver) {
    json manifest;
    manifest["models"] = json::array();
    for (const ManifestEntry& entry : entries) {
        json item;
        item["model"] = entry.model;
        item["name"] = entry.stem;
        item["outputs"] = entry.outputs;
        manifest["models"].push_back(item);
    }
    manifest["unresolved"] = json::array();
    for (const std::string& state : resolver.unresolvedStates()) {
        manifest["unresolved"].push_back(state);
    }
    manifest["resolved_states"] = resolver.resolvedStateCount();

    std::ofstream file(path);
    if (!file) {
        log::error("cannot write manifest: " + path);
        return;
    }
    file << manifest.dump(2) << "\n";
}

void processModel(const std::string& modelFile, const Options& options,
                  BlockModelResolver& resolver, TextureStore& textures, Renderer& renderer,
                  std::vector<ManifestEntry>& manifest, int& failures);

int run(const Options& options) {
    if (options.glInfoOnly) {
        Renderer renderer;
        if (!renderer.init(options)) return kExitGlFailure;
        std::printf("%s\n", renderer.contextDescription().c_str());
        return kExitOk;
    }

    AssetSource assets;
    int usableSources = 0;
    for (const std::string& path : options.assetPaths) {
        if (assets.addSource(path)) ++usableSources;
    }
    if (usableSources == 0) {
        log::error("none of the --assets sources could be opened");
        return kExitUsage;
    }
    log::info("using " + std::to_string(usableSources) + " asset source(s)");

    std::vector<std::string> modelFiles = collectModelFiles(options.inputs);
    if (modelFiles.empty()) {
        log::error("no model files found");
        return kExitUsage;
    }
    if (options.shardCount > 1) {
        std::vector<std::string> shard;
        for (std::size_t index = 0; index < modelFiles.size(); ++index) {
            if (static_cast<int>(index % options.shardCount) == options.shardIndex) {
                shard.push_back(modelFiles[index]);
            }
        }
        modelFiles = std::move(shard);
    }
    log::info("processing " + std::to_string(modelFiles.size()) + " model(s)");

    TextureStore textures(assets);
    BlockModelResolver resolver(assets, textures, options.tintOverrides, options.cullLeaves);

    Renderer renderer;
    if (!options.dryRun && !renderer.init(options)) return kExitGlFailure;
    if (!options.dryRun) log::debug(renderer.contextDescription());

    std::vector<ManifestEntry> manifest;
    int failures = 0;

    for (const std::string& modelFile : modelFiles) {
        try {
            processModel(modelFile, options, resolver, textures, renderer, manifest, failures);
        } catch (const std::exception& e) {
            // One broken model (malformed mod json, io error) must not lose
            // the rest of the batch.
            log::error("model failed: " + modelFile + ": " + e.what());
            ++failures;
        }
    }

    if (options.report) printReport(resolver);
    if (options.manifestJson) writeManifest(*options.manifestJson, manifest, resolver);

    if (failures > 0) {
        log::error(std::to_string(failures) + " model(s) failed");
        return kExitUsage;
    }
    if (options.strict && !resolver.unresolvedStates().empty()) {
        log::error("strict mode: " + std::to_string(resolver.unresolvedStates().size()) +
                   " unresolved block state(s)");
        return kExitStrictResolution;
    }
    return kExitOk;
}

void processModel(const std::string& modelFile, const Options& options,
                  BlockModelResolver& resolver, TextureStore& textures, Renderer& renderer,
                  std::vector<ManifestEntry>& manifest, int& failures) {
    auto startTime = std::chrono::steady_clock::now();
    auto scene = buildScene(modelFile, resolver);
    if (!scene) {
        ++failures;
        return;
    }
    if (options.dryRun) return;

    std::string stem = options.outFile
                           ? fs::path(*options.outFile).replace_extension("").string()
                           : outputStemFor(modelFile);
    fs::path basePath = options.outFile && fs::path(stem).is_absolute()
                            ? fs::path(stem)
                            : fs::path(options.outDir) / stem;
    std::error_code errorCode;
    fs::create_directories(basePath.parent_path(), errorCode);

    std::vector<Frame> frames = renderer.renderTurntable(*scene, textures, options);
    if (frames.empty()) {
        log::error("render produced no frames for " + modelFile);
        ++failures;
        return;
    }

    ManifestEntry entry{modelFile, stem, {}};
    bool wroteEverything = true;
    for (const std::string& format : options.formats) {
        std::string outputPath = basePath.string() + "." + format;
        bool ok = false;
        if (format == "webp") {
            ok = encodeWebp(outputPath, frames, options.frameDelayMs, options.webpLossless,
                            options.webpQuality);
        } else if (format == "gif") {
            ok = encodeGif(outputPath, frames, options.frameDelayMs, options.gifColors,
                           options.gifDither);
        } else if (format == "png") {
            ok = writePng(outputPath, frames.front());
        }
        if (ok) {
            entry.outputs.push_back(outputPath);
        } else {
            log::error("failed to write " + outputPath);
            wroteEverything = false;
        }
    }
    if (options.dumpFrame) {
        int frameIndex = std::min(*options.dumpFrame, static_cast<int>(frames.size()) - 1);
        std::string dumpPath = basePath.string() + ".frame" + std::to_string(frameIndex) + ".png";
        if (writePng(dumpPath, frames[static_cast<std::size_t>(frameIndex)])) {
            entry.outputs.push_back(dumpPath);
        }
    }
    if (!wroteEverything) ++failures;
    manifest.push_back(std::move(entry));

    auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                         std::chrono::steady_clock::now() - startTime).count();
    log::info(stem + ": " + std::to_string(scene->blockCount) + " blocks, " +
              std::to_string(scene->quadCount) + " quads, " + std::to_string(elapsedMs) + " ms");
}

} // namespace

} // namespace ctg

int main(int argc, char** argv) {
    bool helpShown = false;
    auto options = ctg::parseOptions(argc, argv, helpShown);
    if (!options) return helpShown ? ctg::kExitOk : ctg::kExitUsage;
    try {
        return ctg::run(*options);
    } catch (const std::exception& e) {
        ctg::log::error(std::string("fatal: ") + e.what());
        return ctg::kExitUsage;
    }
}
