#pragma once

#include "blockmodel.hpp"

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace ctg {

// Interleaved vertex layout: position(3) uvLayer(3) color(3).
constexpr int kVertexFloats = 9;

struct SceneMesh {
    std::vector<float> vertices;
    std::vector<std::uint32_t> indices;

    // Of the occupied bounding box, in block coordinates.
    float center[3] = {0, 0, 0};
    float horizontalRadius = 1.0f; // max XZ distance from center incl. cube diagonal
    float halfHeight = 1.0f;

    int blockCount = 0;
    int quadCount = 0;
};

// Parses a bonsaitrees4 multiblock model file (loader/version/ref/shape) and
// builds the culled cube mesh. Returns nullopt when the file is not a valid
// multiblock model (logged).
std::optional<SceneMesh> buildScene(const std::string& modelFilePath, BlockModelResolver& resolver);

} // namespace ctg
