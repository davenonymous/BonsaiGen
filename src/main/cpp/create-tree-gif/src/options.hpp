#pragma once

#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <vector>

namespace ctg {

enum class Projection { Ortho, Perspective };

struct Options {
    std::vector<std::string> assetPaths;
    std::vector<std::string> inputs;
    std::string outDir = ".";
    std::optional<std::string> outFile; // only valid with exactly one input
    std::vector<std::string> formats = {"webp", "gif"};

    int width = 512;
    int height = 512;
    int frames = 36;
    int frameDelayMs = 50;
    double pitchDeg = 30.0;
    double yawStartDeg = 45.0;
    bool spinClockwise = false;
    Projection projection = Projection::Ortho;
    double margin = 0.05;
    int supersample = 2;
    std::optional<std::uint32_t> backgroundRgb; // nullopt => transparent

    bool webpLossless = true;
    int webpQuality = 85;
    int gifColors = 255;
    bool gifDither = false;

    bool cullLeaves = false;
    std::map<std::string, std::uint32_t> tintOverrides; // block name -> RRGGBB

    int shardIndex = 0;
    int shardCount = 1;

    bool strict = false;
    bool report = false;
    bool dryRun = false;
    std::optional<int> dumpFrame;
    std::optional<std::string> manifestJson;
    bool glInfoOnly = false;
};

// Parses argv. On error prints the problem (and usage hint) and returns nullopt.
// When --help is requested, prints usage and sets helpShown.
std::optional<Options> parseOptions(int argc, char** argv, bool& helpShown);

} // namespace ctg
